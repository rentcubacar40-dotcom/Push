package com.example.data.moodle

import android.util.Log
import com.example.config.AppConfig
import com.example.data.model.EvidenceUploadResult
import com.example.data.model.FileListResponse
import com.example.data.model.MoodleTokenResponse
import com.example.data.model.RemoteFileItem
import com.example.data.model.SiteInfoResponse
import com.example.data.model.UploadResponseItem
import com.example.data.model.UserEvidenceItem
import com.example.data.model.UserEvidenceListPageResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente centralizado para la API de Moodle.
 * Todos los endpoints y llamadas webservice están aislados en esta clase
 * para permitir ajustes sencillos según la versión de Moodle.
 *
 * ENDPOINTS UTILIZADOS:
 * 1. Obtención de token:
 *    [POST/GET] /login/token.php?username=...&password=...&service=moodle_mobile_app
 *
 * 2. Información del sitio y usuario:
 *    [POST] /webservice/rest/server.php?wsfunction=core_webservice_get_site_info
 *
 * 3. Subida al área de borrador (draft):
 *    [POST] /webservice/upload.php (multipart con filearea=draft)
 *
 * 4. Pasar de borrador a evidencias (archivos privados):
 *    [POST] /webservice/rest/server.php?wsfunction=core_user_add_user_private_files (draftid=...)
 *
 * 5. Listar archivos de evidencias:
 *    [POST] /webservice/rest/server.php?wsfunction=core_files_get_files
 *           (component=user, filearea=private, itemid=0, filepath=/)
 *
 * 6. Descarga y visualización con token:
 *    /webservice/pluginfile.php/{contextid}/user/private/{filename}?token={token}
 */
class MoodleApi(
    private val baseUrl: String = AppConfig.MOODLE_URL,
    private val moodleUser: String = AppConfig.MOODLE_USER,
    private val moodlePass: String = AppConfig.MOODLE_PASS
) {
    private val tag = "MoodleApi"
    private val gson = Gson()
    private val tokenMutex = Mutex()

    private var cachedToken: String? = null
    private var cachedSiteInfo: SiteInfoResponse? = null
    var cachedContextId: Long? = AppConfig.DEFAULT_CONTEXT_ID
    private var cachedSesskey: String? = null
    private val privateFilesMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val evidenceFilesMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()
    private val cookieJar = object : okhttp3.CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val list = cookieStore.getOrPut(url.host) { java.util.concurrent.CopyOnWriteArrayList() }
            list.removeAll { newCookie -> cookies.any { it.name == newCookie.name } }
            list.addAll(cookies)
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .proxy(java.net.Proxy.NO_PROXY) // Seguridad activa contra inspectores y proxies intermedios (HTTP Injector, etc.)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "MoodleMobile/Moodgram")
                .header("X-Requested-With", "com.aistudio.moodgram")
                .build()
            chain.proceed(request)
        }
        .build()

    private fun normalizeUrl(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    /**
     * Obtiene el token de webservice para la cuenta de Moodle configurada.
     * Si ya existe un token en memoria, lo reutiliza a menos que se solicite renovación forzada.
     */
    suspend fun getToken(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        tokenMutex.withLock {
            if (!forceRefresh && !cachedToken.isNullOrEmpty()) {
                return@withLock cachedToken!!
            }

            val cleanBase = normalizeUrl(baseUrl)
            val tokenUrl = "${cleanBase}login/token.php"

            val formBody = okhttp3.FormBody.Builder()
                .add("username", moodleUser)
                .add("password", moodlePass)
                .add("service", AppConfig.MOODLE_SERVICE)
                .build()

            val request = Request.Builder()
                .url(tokenUrl)
                .post(formBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw IOException("Error HTTP al obtener token: ${response.code} - $bodyString")
                    }

                    val tokenResponse = gson.fromJson(bodyString, MoodleTokenResponse::class.java)
                    if (!tokenResponse.token.isNullOrEmpty()) {
                        cachedToken = tokenResponse.token
                        Log.d(tag, "Token de Moodle obtenido con éxito")
                        return@withLock tokenResponse.token
                    } else {
                        val errMsg = tokenResponse.error ?: tokenResponse.message ?: "Credenciales inválidas"
                        throw IOException("No se pudo obtener el token de Moodle: $errMsg")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Fallo al conectar con Moodle para obtener token", e)
                throw e
            }
        }
    }

    /**
     * Consulta información general del sitio y del usuario autenticado (ID y context ID).
     */
    suspend fun getSiteInfo(forceRefresh: Boolean = false): SiteInfoResponse = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedSiteInfo != null) {
            return@withContext cachedSiteInfo!!
        }

        val token = getToken(forceRefresh)
        val cleanBase = normalizeUrl(baseUrl)
        val serverUrl = "${cleanBase}webservice/rest/server.php"

        val formBody = okhttp3.FormBody.Builder()
            .add("wstoken", token)
            .add("wsfunction", "core_webservice_get_site_info")
            .add("moodlewsrestformat", "json")
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error al consultar info del sitio: ${response.code}")
            }

            // Si el token expiró, reintentar una vez
            if (bodyString.contains("invalidtoken", ignoreCase = true)) {
                cachedToken = null
                return@withContext getSiteInfo(forceRefresh = true)
            }

            val siteInfo = gson.fromJson(bodyString, SiteInfoResponse::class.java)
            cachedSiteInfo = siteInfo
            return@withContext siteInfo
        }
    }

    /**
     * Sube un archivo al área de borrador (draft) de Moodle.
     * Reporta el progreso de subida porcentual de 0.0f a 1.0f.
     */
    suspend fun uploadFileToDraft(
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): UploadResponseItem = withContext(Dispatchers.IO) {
        val token = getToken()
        val cleanBase = normalizeUrl(baseUrl)
        val uploadUrl = "${cleanBase}webservice/upload.php"

        val countingRequestBody = ProgressRequestBody(
            bytes = bytes,
            contentType = mimeType.toMediaTypeOrNull(),
            onProgress = onProgress
        )

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("token", token)
            .addFormDataPart("filearea", "draft")
            .addFormDataPart("itemid", "0")
            .addFormDataPart(
                "file_1",
                filename,
                countingRequestBody
            )
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error al subir archivo a borrador: ${response.code} - $bodyString")
            }

            if (bodyString.contains("invalidtoken", ignoreCase = true)) {
                cachedToken = null
                return@withContext uploadFileToDraft(filename, mimeType, bytes, onProgress)
            }

            val listType = object : TypeToken<List<UploadResponseItem>>() {}.type
            val items: List<UploadResponseItem>? = try {
                gson.fromJson(bodyString, listType)
            } catch (e: Exception) {
                null
            }

            val firstItem = items?.firstOrNull()
            if (firstItem?.itemid != null) {
                if (firstItem.contextid != null && firstItem.contextid > 1) {
                    cachedContextId = firstItem.contextid
                    Log.d(tag, "ContextID real detectado desde upload: ${firstItem.contextid}")
                }
                return@withContext firstItem
            } else {
                throw IOException("Respuesta de subida inesperada de Moodle: $bodyString")
            }
        }
    }

    suspend fun getUserContextId(): Long = withContext(Dispatchers.IO) {
        if (cachedContextId != null && cachedContextId!! > 1) {
            return@withContext cachedContextId!!
        }
        cachedContextId = AppConfig.DEFAULT_CONTEXT_ID
        return@withContext AppConfig.DEFAULT_CONTEXT_ID
    }

    /**
     * Autentica la sesión web en Moodle para operaciones de Evidencias (userevidence)
     * mediante la interfaz web oficial de Moodle.
     */
    suspend fun ensureWebSession(): String = withContext(Dispatchers.IO) {
        if (!cachedSesskey.isNullOrEmpty()) {
            return@withContext cachedSesskey!!
        }

        val cleanBase = normalizeUrl(baseUrl)
        val loginUrl = "${cleanBase}login/index.php"

        // 1. Obtener logintoken
        val getLoginReq = Request.Builder().url(loginUrl).get().build()
        val loginPageHtml = client.newCall(getLoginReq).execute().use { it.body?.string() ?: "" }
        val tokenMatch = Regex("name=\"logintoken\" value=\"([^\"]+)\"").find(loginPageHtml)
        val logintoken = tokenMatch?.groupValues?.get(1) ?: ""

        // 2. Realizar POST de inicio de sesión
        val loginBody = okhttp3.FormBody.Builder()
            .add("username", moodleUser)
            .add("password", moodlePass)
            .add("logintoken", logintoken)
            .build()
        val postLoginReq = Request.Builder().url(loginUrl).post(loginBody).build()
        client.newCall(postLoginReq).execute().close()

        // 3. Obtener página de edición de evidencias para extraer sesskey
        val evEditUrl = "${cleanBase}admin/tool/lp/user_evidence_edit.php?userid=${AppConfig.DEFAULT_USER_ID}"
        val evEditReq = Request.Builder().url(evEditUrl).get().build()
        val evEditHtml = client.newCall(evEditReq).execute().use { it.body?.string() ?: "" }

        val sessMatch = Regex("\"sesskey\":\"([^\"]+)\"").find(evEditHtml)
            ?: Regex("name=\"sesskey\" value=\"([^\"]+)\"").find(evEditHtml)
            ?: Regex("sesskey=([a-zA-Z0-9]+)").find(evEditHtml)

        val sesskey = sessMatch?.groupValues?.get(1)
            ?: throw IOException("No se pudo obtener la sesskey de la sesión web de Moodle.")

        cachedSesskey = sesskey
        Log.d(tag, "Sesión web de Moodle autenticada con éxito para evidencias")
        return@withContext sesskey
    }

    /**
     * Mueve los archivos subidos al área de borrador hacia las Evidencias (userevidence)
     * del usuario configurado usando el formulario de tool_lp.
     */
    suspend fun saveDraftToUserEvidence(
        draftItemId: Long,
        evidenceTitle: String,
        description: String = "Moodgram Evidencia"
    ): Long = withContext(Dispatchers.IO) {
        val cleanBase = normalizeUrl(baseUrl)
        val sesskey = ensureWebSession()
        val url = "${cleanBase}admin/tool/lp/user_evidence_edit.php?userid=${AppConfig.DEFAULT_USER_ID}"

        val formBody = okhttp3.FormBody.Builder()
            .add("userid", AppConfig.DEFAULT_USER_ID.toString())
            .add("sesskey", sesskey)
            .add("_qf__tool_lp_form_user_evidence", "1")
            .add("name", evidenceTitle)
            .add("description[text]", description)
            .add("description[format]", "1")
            .add("url", "")
            .add("files", draftItemId.toString())
            .add("submitbutton", "Guardar cambios")
            .build()

        val request = Request.Builder().url(url).post(formBody).build()
        client.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val idMatch = Regex("id=([0-9]+)").find(finalUrl)
            if (idMatch != null) {
                val evId = idMatch.groupValues[1].toLong()
                Log.d(tag, "Evidencia creada/actualizada con éxito en Moodle (ID: $evId)")
                return@withContext evId
            }

            // Si la sesión expiró, reintentar una vez tras renovar
            cachedSesskey = null
            val newSesskey = ensureWebSession()
            val retryBody = okhttp3.FormBody.Builder()
                .add("userid", AppConfig.DEFAULT_USER_ID.toString())
                .add("sesskey", newSesskey)
                .add("_qf__tool_lp_form_user_evidence", "1")
                .add("name", evidenceTitle)
                .add("description[text]", description)
                .add("description[format]", "1")
                .add("url", "")
                .add("files", draftItemId.toString())
                .add("submitbutton", "Guardar cambios")
                .build()

            val retryReq = Request.Builder().url(url).post(retryBody).build()
            client.newCall(retryReq).execute().use { retryResp ->
                val retryUrl = retryResp.request.url.toString()
                val retryMatch = Regex("id=([0-9]+)").find(retryUrl)
                if (retryMatch != null) {
                    val evId = retryMatch.groupValues[1].toLong()
                    Log.d(tag, "Evidencia creada con éxito en Moodle tras reintento (ID: $evId)")
                    return@withContext evId
                } else {
                    throw IOException("No se pudo confirmar el guardado de la evidencia. URL: $retryUrl")
                }
            }
        }
    }

    /**
     * Sube un archivo directamente a las Evidencias (userevidence) de Moodle y genera su URL con token.
     */
    suspend fun uploadToUserEvidence(
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        evidenceTitle: String = "Moodgram Evidencia",
        onProgress: (Float) -> Unit = {}
    ): EvidenceUploadResult = withContext(Dispatchers.IO) {
        val cleanBase = normalizeUrl(baseUrl)
        val token = getToken()
        val contextId = getUserContextId()

        // 1. Subir al borrador (draft)
        val uploadItem = uploadFileToDraft(
            filename = filename,
            mimeType = mimeType,
            bytes = bytes,
            onProgress = onProgress
        )
        val draftId = uploadItem.itemid
            ?: throw IOException("Moodle no retornó identificador de borrador.")

        // 2. Guardar en Evidencias de Moodle
        val evidenceId = saveDraftToUserEvidence(draftId, evidenceTitle)

        // 3. Construir URL directa de webservice pluginfile para Coil / ExoPlayer
        val directUrl = "${cleanBase}webservice/pluginfile.php/$contextId/core_competency/userevidence/$evidenceId/$filename?token=$token"
        evidenceFilesMap[filename] = directUrl
        Log.d(tag, "Archivo subido a Evidencia $evidenceId con URL: $directUrl")

        return@withContext EvidenceUploadResult(
            evidenceId = evidenceId,
            filename = filename,
            directUrl = directUrl
        )
    }

    /**
     * Lista los archivos almacenados en las Evidencias (userevidence) de Moodle.
     * Utiliza el webservice `tool_lp_data_for_user_evidence_list_page`.
     */
    suspend fun listEvidenceFiles(): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        val token = getToken()
        val contextId = getUserContextId()
        val cleanBase = normalizeUrl(baseUrl)
        val serverUrl = "${cleanBase}webservice/rest/server.php"

        val formBody = okhttp3.FormBody.Builder()
            .add("wstoken", token)
            .add("wsfunction", "tool_lp_data_for_user_evidence_list_page")
            .add("moodlewsrestformat", "json")
            .add("userid", AppConfig.DEFAULT_USER_ID.toString())
            .build()

        val request = Request.Builder().url(serverUrl).post(formBody).build()
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error al listar evidencias de usuario: ${response.code}")
            }

            if (bodyString.contains("invalidtoken", ignoreCase = true)) {
                cachedToken = null
                return@withContext listEvidenceFiles()
            }

            val listResp = try {
                gson.fromJson(bodyString, UserEvidenceListPageResponse::class.java)
            } catch (e: Exception) {
                null
            }

            val resultFiles = mutableListOf<RemoteFileItem>()
            listResp?.evidence?.forEach { ev ->
                val evId = ev.id ?: 0L
                ev.files?.forEach { file ->
                    val resolvedFile = file.copy(
                        itemid = evId,
                        contextid = contextId
                    )
                    resultFiles.add(resolvedFile)
                    val bestUrl = file.effectiveUrl
                    if (!file.filename.isNullOrEmpty() && !bestUrl.isNullOrEmpty()) {
                        evidenceFilesMap[file.filename] = bestUrl
                    }
                }
            }
            Log.d(tag, "Evidencias listadas: ${listResp?.evidence?.size ?: 0} evidencias con ${resultFiles.size} archivos")
            return@withContext resultFiles
        }
    }

    /**
     * Mueve los archivos subidos al área de borrador hacia archivos privados (fallback).
     */
    suspend fun saveDraftToPrivateFiles(draftItemId: Long): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        val cleanBase = normalizeUrl(baseUrl)
        val serverUrl = "${cleanBase}webservice/rest/server.php"

        val formBody = okhttp3.FormBody.Builder()
            .add("wstoken", token)
            .add("wsfunction", "core_user_add_user_private_files")
            .add("moodlewsrestformat", "json")
            .add("draftid", draftItemId.toString())
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error al guardar en archivos de usuario: ${response.code}")
            }

            if (bodyString.contains("invalidtoken", ignoreCase = true)) {
                cachedToken = null
                return@withContext saveDraftToPrivateFiles(draftItemId)
            }

            if (bodyString.contains("exception", ignoreCase = true) && bodyString.contains("errorcode", ignoreCase = true)) {
                Log.w(tag, "Aviso de Moodle al guardar archivos: $bodyString")
            }

            return@withContext true
        }
    }

    /**
     * Lista los archivos guardados en los archivos privados de Moodle (fallback).
     */
    suspend fun listPrivateFiles(): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        val token = getToken()
        val contextId = getUserContextId()

        val cleanBase = normalizeUrl(baseUrl)
        val serverUrl = "${cleanBase}webservice/rest/server.php"

        val formBody = okhttp3.FormBody.Builder()
            .add("wstoken", token)
            .add("wsfunction", "core_files_get_files")
            .add("moodlewsrestformat", "json")
            .add("contextid", contextId.toString())
            .add("component", "user")
            .add("filearea", "private")
            .add("itemid", "0")
            .add("filepath", "/")
            .add("filename", "")
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error al listar archivos privados de Moodle: ${response.code}")
            }

            if (bodyString.contains("invalidtoken", ignoreCase = true)) {
                cachedToken = null
                return@withContext listPrivateFiles()
            }

            val fileListResponse = try {
                gson.fromJson(bodyString, FileListResponse::class.java)
            } catch (e: Exception) {
                null
            }

            val files = fileListResponse?.files ?: emptyList()
            files.forEach { file ->
                if (file.contextid != null && file.contextid > 1) {
                    cachedContextId = file.contextid
                }
                val bestUrl = file.effectiveUrl
                if (!file.filename.isNullOrEmpty() && !bestUrl.isNullOrEmpty()) {
                    privateFilesMap[file.filename] = bestUrl
                }
            }
            return@withContext files
        }
    }

    /**
     * Construye la URL de descarga o streaming con el token actual para Coil o ExoPlayer.
     * Soporta URLs de evidencias (/core_competency/userevidence/) y archivos privados (/user/private/).
     */
    suspend fun buildPluginFileUrl(fileRefOrUrl: String): String {
        val token = getToken()
        val cleanBase = normalizeUrl(baseUrl)
        val realContextId = getUserContextId()

        if (fileRefOrUrl.startsWith("http://") || fileRefOrUrl.startsWith("https://")) {
            var correctedUrl = fileRefOrUrl
            // 1. Corregir URLs que tengan contextid = 1 o contexto incorrecto por el real
            if (realContextId > 1 && correctedUrl.contains("/pluginfile.php/1/")) {
                correctedUrl = correctedUrl.replace(
                    "/pluginfile.php/1/",
                    "/pluginfile.php/$realContextId/"
                )
            }
            // 2. Asegurarse de que use /webservice/pluginfile.php para peticiones con token
            if (correctedUrl.contains("/pluginfile.php/") && !correctedUrl.contains("/webservice/pluginfile.php/")) {
                correctedUrl = correctedUrl.replace(
                    "/pluginfile.php/",
                    "/webservice/pluginfile.php/"
                )
            }

            // 3. Renovar o agregar token
            return if (!correctedUrl.contains("token=")) {
                val separator = if (correctedUrl.contains("?")) "&" else "?"
                "$correctedUrl${separator}token=$token"
            } else {
                val regex = Regex("token=[a-zA-Z0-9]+")
                correctedUrl.replace(regex, "token=$token")
            }
        }

        val cleanFilename = fileRefOrUrl.trimStart('/')

        // Buscar primero en el mapa de evidencias
        val evidenceUrl = evidenceFilesMap[cleanFilename]
        if (!evidenceUrl.isNullOrEmpty()) {
            return buildPluginFileUrl(evidenceUrl)
        }

        // Buscar en el mapa de archivos privados
        val privateUrl = privateFilesMap[cleanFilename]
        if (!privateUrl.isNullOrEmpty()) {
            return buildPluginFileUrl(privateUrl)
        }

        return "${cleanBase}webservice/pluginfile.php/$realContextId/user/private/$cleanFilename?token=$token"
    }

    /**
     * Descarga el contenido en texto (para archivos JSON como usuarios.json o publicaciones.json).
     */
    suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        val token = getToken()
        val correctedUrl = if (url.contains("/pluginfile.php/") && !url.contains("/webservice/pluginfile.php/")) {
            url.replace("/pluginfile.php/", "/webservice/pluginfile.php/")
        } else {
            url
        }

        val fullUrl = if (!correctedUrl.contains("token=")) {
            val sep = if (correctedUrl.contains("?")) "&" else "?"
            "$correctedUrl${sep}token=$token"
        } else {
            val regex = Regex("token=[a-zA-Z0-9]+")
            correctedUrl.replace(regex, "token=$token")
        }

        val request = Request.Builder().url(fullUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Error al descargar archivo: ${response.code}")
            }
            return@use response.body?.string() ?: ""
        }
    }

    /**
     * Descarga los bytes completos de una URL.
     */
    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val token = getToken()
        val correctedUrl = if (url.contains("/pluginfile.php/") && !url.contains("/webservice/pluginfile.php/")) {
            url.replace("/pluginfile.php/", "/webservice/pluginfile.php/")
        } else {
            url
        }

        val fullUrl = if (!correctedUrl.contains("token=")) {
            val sep = if (correctedUrl.contains("?")) "&" else "?"
            "$correctedUrl${sep}token=$token"
        } else {
            val regex = Regex("token=[a-zA-Z0-9]+")
            correctedUrl.replace(regex, "token=$token")
        }

        val request = Request.Builder().url(fullUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Error al descargar bytes: ${response.code}")
            }
            return@use response.body?.bytes() ?: ByteArray(0)
        }
    }
}

/**
 * RequestBody personalizado para notificar progreso porcentual de subida de archivos en bytes.
 */
class ProgressRequestBody(
    private val bytes: ByteArray,
    private val contentType: okhttp3.MediaType?,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): okhttp3.MediaType? = contentType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = bytes.size.toLong()
        var bytesWritten = 0L

        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                if (totalBytes > 0) {
                    val progress = (bytesWritten.toFloat() / totalBytes.toFloat()).coerceIn(0.0f, 1.0f)
                    onProgress(progress)
                }
            }
        }

        val bufferedCountingSink = countingSink.buffer()
        bufferedCountingSink.write(bytes)
        bufferedCountingSink.flush()
    }
}
