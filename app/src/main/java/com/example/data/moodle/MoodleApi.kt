package com.example.data.moodle

import android.util.Log
import com.example.config.AppConfig
import com.example.data.model.FileListResponse
import com.example.data.model.MoodleTokenResponse
import com.example.data.model.RemoteFileItem
import com.example.data.model.SiteInfoResponse
import com.example.data.model.UploadResponseItem
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

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
                return@withContext firstItem
            } else {
                throw IOException("Respuesta de subida inesperada de Moodle: $bodyString")
            }
        }
    }

    /**
     * Mueve los archivos subidos al área de borrador hacia las Evidencias (archivos privados)
     * del usuario configurado usando `core_user_add_user_private_files`.
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
                throw IOException("Error al guardar en evidencias de usuario: ${response.code}")
            }

            if (bodyString.contains("invalidtoken", ignoreCase = true)) {
                cachedToken = null
                return@withContext saveDraftToPrivateFiles(draftItemId)
            }

            // Si hay error en formato JSON de Moodle
            if (bodyString.contains("exception", ignoreCase = true) && bodyString.contains("errorcode", ignoreCase = true)) {
                Log.w(tag, "Aviso de Moodle al guardar evidencias: $bodyString")
            }

            return@withContext true
        }
    }

    /**
     * Lista los archivos guardados en las Evidencias (archivos privados) de la cuenta en Moodle.
     * Utiliza `core_files_get_files` para ubicar los archivos más recientes.
     */
    suspend fun listPrivateFiles(): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        val token = getToken()
        val siteInfo = getSiteInfo()
        val contextId = siteInfo.usercontextid ?: -1L

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
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Error al listar evidencias de Moodle: ${response.code}")
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
            return@withContext files
        }
    }

    /**
     * Construye la URL de descarga o streaming con el token actual para Coil o ExoPlayer.
     * Formato Moodle: /webservice/pluginfile.php/{contextid}/user/private/{filepath}/{filename}?token={token}
     */
    suspend fun buildPluginFileUrl(fileRefOrUrl: String): String {
        val token = getToken()
        val cleanBase = normalizeUrl(baseUrl)

        if (fileRefOrUrl.startsWith("http://") || fileRefOrUrl.startsWith("https://")) {
            // Ya es una URL completa
            val separator = if (fileRefOrUrl.contains("?")) "&" else "?"
            return if (!fileRefOrUrl.contains("token=")) {
                "$fileRefOrUrl${separator}token=$token"
            } else {
                fileRefOrUrl
            }
        }

        // Si es un nombre de archivo o path relativo
        val siteInfo = try { getSiteInfo() } catch (_: Exception) { null }
        val contextId = siteInfo?.usercontextid ?: 1

        val cleanFilename = fileRefOrUrl.trimStart('/')
        return "${cleanBase}webservice/pluginfile.php/$contextId/user/private/$cleanFilename?token=$token"
    }

    /**
     * Descarga el contenido en texto (para archivos JSON como usuarios.json o publicaciones.json).
     */
    suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        val fullUrl = if (!url.contains("token=")) {
            val token = getToken()
            val sep = if (url.contains("?")) "&" else "?"
            "$url${sep}token=$token"
        } else {
            url
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
        val fullUrl = if (!url.contains("token=")) {
            val token = getToken()
            val sep = if (url.contains("?")) "&" else "?"
            "$url${sep}token=$token"
        } else {
            url
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
