package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.config.AppConfig
import com.example.data.local.LocalCache
import com.example.data.local.SessionManager
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.PostsDatabase
import com.example.data.model.RemoteFileItem
import com.example.data.model.User
import com.example.data.model.UserSession
import com.example.data.model.UsersDatabase
import com.example.data.moodle.MoodleApi
import com.example.util.SecurityUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class MoodgramRepository(
    private val context: Context,
    val moodleApi: MoodleApi = MoodleApi(),
    val sessionManager: SessionManager = SessionManager(context),
    val localCache: LocalCache = LocalCache(context)
) {
    private val tag = "MoodgramRepository"
    private val gson = Gson()
    private val usersMutex = Mutex()
    private val postsMutex = Mutex()

    /**
     * Construye la URL de reproducción/visualización autenticada para imágenes y videos.
     */
    suspend fun resolveMediaUrl(fileRefOrUrl: String): String {
        if (fileRefOrUrl.isEmpty()) return ""
        return moodleApi.buildPluginFileUrl(fileRefOrUrl)
    }

    /**
     * Inicializa y asegura que la cuenta de administrador (@Eliel_21) exista
     * en la base de datos de usuarios de Moodle.
     */
    suspend fun ensureAdminSeeded(): Unit = withContext(Dispatchers.IO) {
        usersMutex.withLock {
            val usersDb = getUsersInternal(forceRemote = false)
            val adminExists = usersDb.users.any {
                it.username.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true)
            }

            if (!adminExists) {
                Log.d(tag, "Sembrando usuario administrador ${AppConfig.ADMIN_USERNAME}")
                val salt = SecurityUtils.generateSalt()
                val passwordHash = SecurityUtils.hashPassword(AppConfig.ADMIN_PASSWORD, salt)
                val adminUser = User(
                    username = AppConfig.ADMIN_USERNAME,
                    displayName = "Eliel (Admin)",
                    passwordHash = passwordHash,
                    salt = salt,
                    avatarRef = "",
                    role = "admin",
                    createdAt = System.currentTimeMillis(),
                    isBanned = false
                )

                val updatedUsers = usersDb.users + adminUser
                val newDb = usersDb.copy(
                    version = usersDb.version + 1,
                    lastUpdated = System.currentTimeMillis(),
                    users = updatedUsers
                )
                saveUsersInternal(newDb)
            }
        }
    }

    /**
     * Obtiene la base de datos de usuarios más reciente.
     */
    suspend fun getUsersDatabase(forceRemote: Boolean = false): UsersDatabase = withContext(Dispatchers.IO) {
        usersMutex.withLock {
            getUsersInternal(forceRemote)
        }
    }

    private suspend fun getUsersInternal(forceRemote: Boolean): UsersDatabase {
        if (!forceRemote) {
            localCache.getUsers()?.let { return it }
        }

        try {
            var files = moodleApi.listEvidenceFiles()
            if (files.none { it.filename?.startsWith(AppConfig.USERS_FILE_PREFIX) == true }) {
                files = files + moodleApi.listPrivateFiles()
            }
            val userFiles = files.filter {
                val name = it.filename ?: ""
                name.startsWith(AppConfig.USERS_FILE_PREFIX) && name.endsWith(".json")
            }.sortedByDescending { it.timemodified ?: 0L }

            val newestFile = userFiles.firstOrNull()
            val fileUrl = newestFile?.effectiveUrl
            if (fileUrl != null) {
                val json = moodleApi.downloadText(fileUrl)
                val db = try {
                    gson.fromJson(json, UsersDatabase::class.java)
                } catch (_: Exception) {
                    null
                }
                if (db != null) {
                    localCache.saveUsers(db)
                    return db
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "No se pudo descargar usuarios de Moodle evidencias, usando caché o plantilla inicial", e)
        }

        // Fallback a caché o base inicial
        val cached = localCache.getUsers()
        if (cached != null) return cached

        // Crear base inicial con el admin
        val salt = SecurityUtils.generateSalt()
        val initialAdmin = User(
            username = AppConfig.ADMIN_USERNAME,
            displayName = "Eliel (Admin)",
            passwordHash = SecurityUtils.hashPassword(AppConfig.ADMIN_PASSWORD, salt),
            salt = salt,
            avatarRef = "",
            role = "admin",
            createdAt = System.currentTimeMillis()
        )
        val initialDb = UsersDatabase(users = listOf(initialAdmin))
        localCache.saveUsers(initialDb)
        return initialDb
    }

    private suspend fun saveUsersInternal(database: UsersDatabase) {
        localCache.saveUsers(database)
        try {
            val json = gson.toJson(database)
            val bytes = json.toByteArray(Charsets.UTF_8)
            val filename = "${AppConfig.USERS_FILE_PREFIX}_${System.currentTimeMillis()}.json"

            moodleApi.uploadToUserEvidence(
                filename = filename,
                mimeType = "application/json",
                bytes = bytes,
                evidenceTitle = "Moodgram Usuarios DB"
            )
            Log.d(tag, "Base de datos de usuarios guardada en Evidencias de Moodle con éxito ($filename)")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al guardar usuarios en evidencias de Moodle", e)
        }
    }

    /**
     * Obtiene la base de datos de publicaciones más reciente.
     */
    suspend fun getPostsDatabase(forceRemote: Boolean = false): PostsDatabase = withContext(Dispatchers.IO) {
        postsMutex.withLock {
            getPostsInternal(forceRemote)
        }
    }

    private suspend fun getPostsInternal(forceRemote: Boolean): PostsDatabase {
        if (!forceRemote) {
            localCache.getPosts()?.let { return it }
        }

        try {
            var files = moodleApi.listEvidenceFiles()
            if (files.none { it.filename?.startsWith(AppConfig.POSTS_FILE_PREFIX) == true }) {
                files = files + moodleApi.listPrivateFiles()
            }
            val postFiles = files.filter {
                val name = it.filename ?: ""
                name.startsWith(AppConfig.POSTS_FILE_PREFIX) && name.endsWith(".json")
            }.sortedByDescending { it.timemodified ?: 0L }

            val newestFile = postFiles.firstOrNull()
            val fileUrl = newestFile?.effectiveUrl
            if (fileUrl != null) {
                val json = moodleApi.downloadText(fileUrl)
                val db = try {
                    gson.fromJson(json, PostsDatabase::class.java)
                } catch (_: Exception) {
                    null
                }
                if (db != null) {
                    // Sanear cualquier URL guardada previamente con contextid = 1 o ruta sin webservice
                    val sanitizedPosts = db.posts.map { post ->
                        var url = post.mediaUrl
                        if (url.contains("/pluginfile.php/1/")) {
                            url = url.replace("/pluginfile.php/1/", "/pluginfile.php/${AppConfig.DEFAULT_CONTEXT_ID}/")
                        }
                        if (url.contains("/pluginfile.php/") && !url.contains("/webservice/pluginfile.php/")) {
                            url = url.replace("/pluginfile.php/", "/webservice/pluginfile.php/")
                        }
                        post.copy(mediaUrl = url)
                    }
                    val sanitizedDb = db.copy(posts = sanitizedPosts)
                    localCache.savePosts(sanitizedDb)
                    return sanitizedDb
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "No se pudo descargar publicaciones de Moodle evidencias, usando caché", e)
        }

        val cached = localCache.getPosts()
        if (cached != null) return cached

        val emptyDb = PostsDatabase()
        localCache.savePosts(emptyDb)
        return emptyDb
    }

    private suspend fun savePostsInternal(database: PostsDatabase) {
        localCache.savePosts(database)
        try {
            val json = gson.toJson(database)
            val bytes = json.toByteArray(Charsets.UTF_8)
            val filename = "${AppConfig.POSTS_FILE_PREFIX}_${System.currentTimeMillis()}.json"

            moodleApi.uploadToUserEvidence(
                filename = filename,
                mimeType = "application/json",
                bytes = bytes,
                evidenceTitle = "Moodgram Publicaciones DB"
            )
            Log.d(tag, "Base de datos de publicaciones guardada en Evidencias de Moodle ($filename)")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al guardar publicaciones en evidencias de Moodle", e)
        }
    }

    /**
     * Inicia sesión con nombre de usuario y contraseña.
     */
    suspend fun login(rawUsername: String, password: String): User = withContext(Dispatchers.IO) {
        val cleanUsername = if (rawUsername.startsWith("@")) rawUsername.trim() else "@${rawUsername.trim()}"

        // Verificar si es el administrador
        if (cleanUsername.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true) &&
            password == AppConfig.ADMIN_PASSWORD
        ) {
            ensureAdminSeeded()
            val usersDb = getUsersDatabase(forceRemote = false)
            val adminInDb = usersDb.users.firstOrNull {
                it.username.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true)
            }
            val adminUser = User(
                username = AppConfig.ADMIN_USERNAME,
                displayName = adminInDb?.displayName ?: "Eliel (Admin)",
                passwordHash = adminInDb?.passwordHash ?: "",
                salt = adminInDb?.salt ?: "",
                avatarRef = adminInDb?.avatarRef ?: "",
                role = "admin",
                isBanned = false
            )
            sessionManager.saveSession(adminUser)
            return@withContext adminUser
        }

        // Buscar en la base de usuarios
        val usersDb = getUsersDatabase(forceRemote = true)
        val user = usersDb.users.firstOrNull {
            it.username.equals(cleanUsername, ignoreCase = true)
        } ?: throw IOException("Usuario no encontrado. Comprueba tus datos o regístrate.")

        if (user.isBanned) {
            throw IOException("Esta cuenta ha sido suspendida por el administrador.")
        }

        val valid = SecurityUtils.verifyPassword(password, user.salt, user.passwordHash)
        if (!valid) {
            throw IOException("Contraseña incorrecta.")
        }

        sessionManager.saveSession(user)
        return@withContext user
    }

    /**
     * Registra un nuevo usuario con avatar opcional, validando no duplicados.
     */
    suspend fun register(
        rawUsername: String,
        displayName: String,
        password: String,
        avatarBytes: ByteArray? = null
    ): User = withContext(Dispatchers.IO) {
        val cleanUsername = if (rawUsername.startsWith("@")) rawUsername.trim() else "@${rawUsername.trim()}"

        if (cleanUsername.length < 3) {
            throw IOException("El nombre de usuario debe tener al menos 3 caracteres.")
        }
        if (displayName.isBlank()) {
            throw IOException("Por favor ingresa un nombre para mostrar.")
        }
        if (password.length < 6) {
            throw IOException("La contraseña debe tener al menos 6 caracteres.")
        }

        usersMutex.withLock {
            val usersDb = getUsersInternal(forceRemote = true)
            val exists = usersDb.users.any {
                it.username.equals(cleanUsername, ignoreCase = true)
            }
            if (exists) {
                throw IOException("El nombre de usuario $cleanUsername ya está en uso. Elige otro.")
            }

            var avatarRef = ""
            if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                try {
                    val avatarFilename = "avatar_${cleanUsername.removePrefix("@")}_${System.currentTimeMillis()}.jpg"
                    val uploadResult = moodleApi.uploadToUserEvidence(
                        filename = avatarFilename,
                        mimeType = "image/jpeg",
                        bytes = avatarBytes,
                        evidenceTitle = "Moodgram Avatar $cleanUsername"
                    )
                    avatarRef = uploadResult.directUrl
                } catch (e: Exception) {
                    Log.w(tag, "No se pudo subir foto de perfil a evidencias, continuando sin avatar", e)
                }
            }

            val salt = SecurityUtils.generateSalt()
            val hash = SecurityUtils.hashPassword(password, salt)
            val isInitialAdmin = cleanUsername.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true)

            val newUser = User(
                username = cleanUsername,
                displayName = displayName.trim(),
                passwordHash = hash,
                salt = salt,
                avatarRef = avatarRef,
                role = if (isInitialAdmin) "admin" else "user",
                createdAt = System.currentTimeMillis(),
                isBanned = false
            )

            val updatedList = usersDb.users + newUser
            val newDb = usersDb.copy(
                version = usersDb.version + 1,
                lastUpdated = System.currentTimeMillis(),
                users = updatedList
            )
            saveUsersInternal(newDb)
            sessionManager.saveSession(newUser)
            return@withLock newUser
        }
    }

    /**
     * Crea y publica una nueva publicación con imagen o video en las evidencias de Moodle.
     */
    suspend fun createPost(
        author: UserSession,
        text: String,
        mediaBytes: ByteArray,
        filename: String,
        mimeType: String,
        isVideo: Boolean,
        onProgress: (Float) -> Unit = {}
    ): Post = withContext(Dispatchers.IO) {
        if (mediaBytes.size > AppConfig.MAX_FILE_BYTES) {
            throw IOException("El archivo excede el límite máximo de ${AppConfig.MAX_FILE_MB} MB.")
        }

        // Subir archivo multimedia directamente a Evidencias de Moodle
        val uniqueFilename = "post_${System.currentTimeMillis()}_${filename.replace(" ", "_")}"
        val uploadResult = moodleApi.uploadToUserEvidence(
            filename = uniqueFilename,
            mimeType = mimeType,
            bytes = mediaBytes,
            evidenceTitle = "Moodgram Post $uniqueFilename",
            onProgress = onProgress
        )

        val resolvedUrl = uploadResult.directUrl

        val newPost = Post(
            id = UUID.randomUUID().toString(),
            authorUsername = author.username,
            authorDisplayName = author.displayName,
            authorAvatarRef = author.avatarRef,
            text = text.trim(),
            mediaUrl = resolvedUrl,
            mediaType = if (isVideo) "video" else "image",
            fileRef = uniqueFilename,
            fileSize = mediaBytes.size.toLong(),
            createdAt = System.currentTimeMillis(),
            likes = emptyList(),
            comments = emptyList()
        )

        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = true)
            val updatedPosts = listOf(newPost) + postsDb.posts
            val newDb = postsDb.copy(
                version = postsDb.version + 1,
                lastUpdated = System.currentTimeMillis(),
                posts = updatedPosts
            )
            savePostsInternal(newDb)
        }

        return@withContext newPost
    }

    /**
     * Alterna el like de una publicación.
     */
    suspend fun toggleLike(postId: String, username: String): Boolean = withContext(Dispatchers.IO) {
        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            var likedNow = false
            val updatedPosts = postsDb.posts.map { post ->
                if (post.id == postId) {
                    val alreadyLiked = post.likes.contains(username)
                    likedNow = !alreadyLiked
                    val newLikes = if (alreadyLiked) {
                        post.likes - username
                    } else {
                        post.likes + username
                    }
                    post.copy(likes = newLikes)
                } else {
                    post
                }
            }
            val newDb = postsDb.copy(lastUpdated = System.currentTimeMillis(), posts = updatedPosts)
            savePostsInternal(newDb)
            return@withLock likedNow
        }
    }

    /**
     * Agrega un comentario a una publicación.
     */
    suspend fun addComment(
        postId: String,
        user: UserSession,
        commentText: String
    ): Comment = withContext(Dispatchers.IO) {
        if (commentText.isBlank()) throw IOException("El comentario no puede estar vacío.")

        val newComment = Comment(
            id = UUID.randomUUID().toString(),
            authorUsername = user.username,
            authorDisplayName = user.displayName,
            authorAvatarRef = user.avatarRef,
            text = commentText.trim(),
            createdAt = System.currentTimeMillis()
        )

        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            val updatedPosts = postsDb.posts.map { post ->
                if (post.id == postId) {
                    post.copy(comments = post.comments + newComment)
                } else {
                    post
                }
            }
            val newDb = postsDb.copy(lastUpdated = System.currentTimeMillis(), posts = updatedPosts)
            savePostsInternal(newDb)
        }

        return@withContext newComment
    }

    /**
     * Elimina una publicación. Permitido si el solicitante es el autor o el administrador.
     */
    suspend fun deletePost(postId: String, requestingUsername: String, isAdmin: Boolean) = withContext(Dispatchers.IO) {
        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            val post = postsDb.posts.firstOrNull { it.id == postId } ?: return@withLock
            if (!isAdmin && !post.authorUsername.equals(requestingUsername, ignoreCase = true)) {
                throw IOException("No tienes permisos para eliminar esta publicación.")
            }

            val updatedPosts = postsDb.posts.filterNot { it.id == postId }
            val newDb = postsDb.copy(lastUpdated = System.currentTimeMillis(), posts = updatedPosts)
            savePostsInternal(newDb)
        }
    }

    /**
     * Elimina un comentario.
     */
    suspend fun deleteComment(
        postId: String,
        commentId: String,
        requestingUsername: String,
        isAdmin: Boolean
    ) = withContext(Dispatchers.IO) {
        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            val updatedPosts = postsDb.posts.map { post ->
                if (post.id == postId) {
                    val filteredComments = post.comments.filterNot { comment ->
                        if (comment.id == commentId) {
                            isAdmin ||
                            comment.authorUsername.equals(requestingUsername, ignoreCase = true) ||
                            post.authorUsername.equals(requestingUsername, ignoreCase = true)
                        } else {
                            false
                        }
                    }
                    post.copy(comments = filteredComments)
                } else {
                    post
                }
            }
            val newDb = postsDb.copy(lastUpdated = System.currentTimeMillis(), posts = updatedPosts)
            savePostsInternal(newDb)
        }
    }

    /**
     * Modifica el estado de baneo de un usuario (solo administrador).
     */
    suspend fun setUserBannedStatus(targetUsername: String, isBanned: Boolean) = withContext(Dispatchers.IO) {
        usersMutex.withLock {
            val usersDb = getUsersInternal(forceRemote = false)
            val updatedUsers = usersDb.users.map { user ->
                if (user.username.equals(targetUsername, ignoreCase = true)) {
                    user.copy(isBanned = isBanned)
                } else {
                    user
                }
            }
            val newDb = usersDb.copy(lastUpdated = System.currentTimeMillis(), users = updatedUsers)
            saveUsersInternal(newDb)
        }
    }

    /**
     * Elimina un usuario por completo (solo administrador).
     */
    suspend fun deleteUser(targetUsername: String) = withContext(Dispatchers.IO) {
        if (targetUsername.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true)) {
            throw IOException("No se puede eliminar la cuenta principal de administrador.")
        }
        usersMutex.withLock {
            val usersDb = getUsersInternal(forceRemote = false)
            val updatedUsers = usersDb.users.filterNot { it.username.equals(targetUsername, ignoreCase = true) }
            val newDb = usersDb.copy(lastUpdated = System.currentTimeMillis(), users = updatedUsers)
            saveUsersInternal(newDb)
        }
    }

    /**
     * Actualiza el perfil de un usuario (nombre para mostrar y nueva foto).
     */
    suspend fun updateProfile(
        username: String,
        newDisplayName: String,
        newAvatarBytes: ByteArray?
    ): User = withContext(Dispatchers.IO) {
        usersMutex.withLock {
            val usersDb = getUsersInternal(forceRemote = false)
            var newAvatarRef: String? = null

            if (newAvatarBytes != null && newAvatarBytes.isNotEmpty()) {
                val cleanUser = username.removePrefix("@")
                val filename = "avatar_${cleanUser}_${System.currentTimeMillis()}.jpg"
                val uploadResult = moodleApi.uploadToUserEvidence(
                    filename = filename,
                    mimeType = "image/jpeg",
                    bytes = newAvatarBytes,
                    evidenceTitle = "Moodgram Avatar $cleanUser"
                )
                newAvatarRef = uploadResult.directUrl
            }

            var updatedUser: User? = null
            val updatedUsers = usersDb.users.map { user ->
                if (user.username.equals(username, ignoreCase = true)) {
                    val modified = user.copy(
                        displayName = newDisplayName.trim(),
                        avatarRef = newAvatarRef ?: user.avatarRef
                    )
                    updatedUser = modified
                    modified
                } else {
                    user
                }
            }

            val finalUser = updatedUser ?: throw IOException("Usuario no encontrado.")
            val newDb = usersDb.copy(lastUpdated = System.currentTimeMillis(), users = updatedUsers)
            saveUsersInternal(newDb)

            sessionManager.updateDisplayName(finalUser.displayName)
            if (finalUser.avatarRef.isNotEmpty()) {
                sessionManager.updateAvatar(finalUser.avatarRef)
            }

            return@withLock finalUser
        }
    }
}
