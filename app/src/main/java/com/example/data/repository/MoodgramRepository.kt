package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.config.AppConfig
import com.example.data.local.LocalCache
import com.example.data.local.SessionManager
import com.example.data.model.ChatGroup
import com.example.data.model.ChatMessage
import com.example.data.model.ChatsDatabase
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
    private val chatsMutex = Mutex()

    private var lastHeartbeatSent: Long = 0L

    /**
     * Construye la URL de reproducción/visualización autenticada para imágenes y videos.
     */
    suspend fun resolveMediaUrl(fileRefOrUrl: String): String {
        if (fileRefOrUrl.isEmpty()) return ""
        return moodleApi.buildPluginFileUrl(fileRefOrUrl)
    }

    /**
     * Asegura que el usuario administrador inicial exista sin sobrescribir datos editados.
     */
    suspend fun ensureAdminSeeded(): Unit = withContext(Dispatchers.IO) {
        usersMutex.withLock {
            val usersDb = getUsersInternal(forceRemote = false)
            val adminInDb = usersDb.users.firstOrNull {
                it.username.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true)
            }

            if (adminInDb == null) {
                Log.d(tag, "Sembrando usuario administrador inicial ${AppConfig.ADMIN_USERNAME}")
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
                    isBanned = false,
                    lastActive = System.currentTimeMillis()
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
     * Actualiza el estado en línea / heartbeat del usuario actual.
     */
    suspend fun updateHeartbeat(username: String): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatSent < 25_000L) return@withContext // Throttle cada 25s
        lastHeartbeatSent = now

        try {
            usersMutex.withLock {
                val db = getUsersInternal(forceRemote = false)
                val updated = db.users.map { u ->
                    if (u.username.equals(username, ignoreCase = true)) {
                        u.copy(lastActive = now)
                    } else u
                }
                val newDb = db.copy(users = updated, lastUpdated = now)
                localCache.saveUsers(newDb)
            }
        } catch (_: Exception) {}
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
            }.sortedWith(compareByDescending<RemoteFileItem> { it.itemid ?: 0L }
                .thenByDescending { it.timemodified ?: 0L })

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
            Log.w(tag, "No se pudo descargar usuarios del servidor, usando caché", e)
        }

        val cached = localCache.getUsers()
        if (cached != null) return cached

        val salt = SecurityUtils.generateSalt()
        val initialAdmin = User(
            username = AppConfig.ADMIN_USERNAME,
            displayName = "Eliel (Admin)",
            passwordHash = SecurityUtils.hashPassword(AppConfig.ADMIN_PASSWORD, salt),
            salt = salt,
            avatarRef = "",
            role = "admin",
            createdAt = System.currentTimeMillis(),
            lastActive = System.currentTimeMillis()
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
            Log.d(tag, "Base de datos de usuarios guardada con éxito ($filename)")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al guardar usuarios en la nube", e)
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
            }.sortedWith(compareByDescending<RemoteFileItem> { it.itemid ?: 0L }
                .thenByDescending { it.timemodified ?: 0L })

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
            Log.w(tag, "No se pudo descargar publicaciones del servidor, usando caché", e)
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
            Log.d(tag, "Base de datos de publicaciones guardada ($filename)")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al guardar publicaciones en la nube", e)
        }
    }

    /**
     * Obtiene la base de datos de chats más reciente.
     */
    suspend fun getChatsDatabase(forceRemote: Boolean = false): ChatsDatabase = withContext(Dispatchers.IO) {
        chatsMutex.withLock {
            getChatsInternal(forceRemote)
        }
    }

    private suspend fun getChatsInternal(forceRemote: Boolean): ChatsDatabase {
        if (!forceRemote) {
            localCache.getChats()?.let { return it }
        }

        try {
            var files = moodleApi.listEvidenceFiles()
            if (files.none { it.filename?.startsWith(AppConfig.CHATS_FILE_PREFIX) == true }) {
                files = files + moodleApi.listPrivateFiles()
            }
            val chatFiles = files.filter {
                val name = it.filename ?: ""
                name.startsWith(AppConfig.CHATS_FILE_PREFIX) && name.endsWith(".json")
            }.sortedWith(compareByDescending<RemoteFileItem> { it.itemid ?: 0L }
                .thenByDescending { it.timemodified ?: 0L })

            val newestFile = chatFiles.firstOrNull()
            val fileUrl = newestFile?.effectiveUrl
            if (fileUrl != null) {
                val json = moodleApi.downloadText(fileUrl)
                val db = try {
                    gson.fromJson(json, ChatsDatabase::class.java)
                } catch (_: Exception) {
                    null
                }
                if (db != null) {
                    localCache.saveChats(db)
                    return db
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "No se pudo descargar chats del servidor, usando caché", e)
        }

        val cached = localCache.getChats()
        if (cached != null) return cached

        // Base inicial con el grupo oficial
        val usersDb = localCache.getUsers()
        val allUsernames = usersDb?.users?.map { it.username } ?: listOf(AppConfig.ADMIN_USERNAME)
        val initialGroup = ChatGroup(
            id = AppConfig.OFFICIAL_GROUP_ID,
            name = "Grupo Oficial Moodgram",
            description = "Comunidad oficial de Moodgram. Todos los miembros registrados forman parte de este chat.",
            isOfficialGroup = true,
            members = allUsernames,
            messages = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = AppConfig.OFFICIAL_GROUP_ID,
                    senderUsername = AppConfig.ADMIN_USERNAME,
                    senderDisplayName = "Eliel (Admin)",
                    senderAvatarRef = "",
                    text = "¡Bienvenidos todos a Moodgram! Aquí pueden comunicarse, compartir ideas y disfrutar de la red social en tiempo real.",
                    createdAt = System.currentTimeMillis()
                )
            ),
            lastMessage = "¡Bienvenidos todos a Moodgram!",
            lastMessageSender = AppConfig.ADMIN_USERNAME,
            lastMessageTime = System.currentTimeMillis()
        )
        val initialDb = ChatsDatabase(officialGroup = initialGroup)
        localCache.saveChats(initialDb)
        return initialDb
    }

    private suspend fun saveChatsInternal(database: ChatsDatabase) {
        localCache.saveChats(database)
        try {
            val json = gson.toJson(database)
            val bytes = json.toByteArray(Charsets.UTF_8)
            val filename = "${AppConfig.CHATS_FILE_PREFIX}_${System.currentTimeMillis()}.json"

            moodleApi.uploadToUserEvidence(
                filename = filename,
                mimeType = "application/json",
                bytes = bytes,
                evidenceTitle = "Moodgram Chats DB"
            )
            Log.d(tag, "Base de datos de chats guardada con éxito ($filename)")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al guardar chats en la nube", e)
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
                isBanned = false,
                lastActive = System.currentTimeMillis()
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

        val updatedUser = user.copy(lastActive = System.currentTimeMillis())
        sessionManager.saveSession(updatedUser)
        return@withContext updatedUser
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
                    Log.w(tag, "No se pudo subir foto de perfil, continuando sin avatar", e)
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
                isBanned = false,
                lastActive = System.currentTimeMillis()
            )

            val updatedList = usersDb.users + newUser
            val newDb = usersDb.copy(
                version = usersDb.version + 1,
                lastUpdated = System.currentTimeMillis(),
                users = updatedList
            )
            saveUsersInternal(newDb)
            sessionManager.saveSession(newUser)

            // Auto-inscribir en el Grupo Oficial
            try {
                chatsMutex.withLock {
                    val chatsDb = getChatsInternal(forceRemote = false)
                    val group = chatsDb.officialGroup
                    if (!group.members.contains(cleanUsername)) {
                        val updatedGroup = group.copy(members = group.members + cleanUsername)
                        saveChatsInternal(chatsDb.copy(officialGroup = updatedGroup, lastUpdated = System.currentTimeMillis()))
                    }
                }
            } catch (_: Exception) {}

            return@withLock newUser
        }
    }

    /**
     * Crea y publica una nueva publicación con imagen o video en la nube.
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
            reactions = emptyMap(),
            comments = emptyList()
        )

        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
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
     * Establece o alterna una reacción en una publicación.
     */
    suspend fun setPostReaction(postId: String, username: String, emoji: String): Unit = withContext(Dispatchers.IO) {
        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            val updatedPosts = postsDb.posts.map { post ->
                if (post.id == postId) {
                    val currentReaction = post.reactions[username]
                    val updatedReactions = post.reactions.toMutableMap()
                    if (currentReaction == emoji) {
                        updatedReactions.remove(username)
                    } else {
                        updatedReactions[username] = emoji
                    }
                    val updatedLikes = updatedReactions.keys.toList()
                    post.copy(reactions = updatedReactions, likes = updatedLikes)
                } else {
                    post
                }
            }
            val newDb = postsDb.copy(lastUpdated = System.currentTimeMillis(), posts = updatedPosts)
            savePostsInternal(newDb)
        }
    }

    /**
     * Alterna el like estándar de una publicación (por defecto ❤️).
     */
    suspend fun toggleLike(postId: String, username: String): Boolean = withContext(Dispatchers.IO) {
        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            var likedNow = false
            val updatedPosts = postsDb.posts.map { post ->
                if (post.id == postId) {
                    val current = post.reactions[username]
                    val updatedReactions = post.reactions.toMutableMap()
                    if (current != null) {
                        updatedReactions.remove(username)
                        likedNow = false
                    } else {
                        updatedReactions[username] = "❤️"
                        likedNow = true
                    }
                    val updatedLikes = updatedReactions.keys.toList()
                    post.copy(reactions = updatedReactions, likes = updatedLikes)
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
     * Edita un comentario existente.
     * Permitido para el autor del comentario Y para el Administrador.
     */
    suspend fun editComment(
        postId: String,
        commentId: String,
        newText: String,
        requestingUsername: String,
        isAdmin: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        if (newText.isBlank()) throw IOException("El comentario no puede estar vacío.")

        postsMutex.withLock {
            val postsDb = getPostsInternal(forceRemote = false)
            val updatedPosts = postsDb.posts.map { post ->
                if (post.id == postId) {
                    val updatedComments = post.comments.map { comment ->
                        if (comment.id == commentId) {
                            if (!isAdmin && !comment.authorUsername.equals(requestingUsername, ignoreCase = true)) {
                                throw IOException("No tienes permisos para editar este comentario.")
                            }
                            comment.copy(text = newText.trim(), isEdited = true, editedAt = System.currentTimeMillis())
                        } else {
                            comment
                        }
                    }
                    post.copy(comments = updatedComments)
                } else {
                    post
                }
            }
            val newDb = postsDb.copy(lastUpdated = System.currentTimeMillis(), posts = updatedPosts)
            savePostsInternal(newDb)
        }
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
     * Elimina un comentario. Permitido para el autor, autor del post, o administrador.
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

    // =========================================================================
    // MÓDULO DE CHAT Y GRUPO OFICIAL
    // =========================================================================

    /**
     * Envía un mensaje en un chat (Grupo Oficial o chat directo).
     */
    suspend fun sendChatMessage(
        chatId: String,
        sender: UserSession,
        text: String,
        mediaBytes: ByteArray? = null,
        filename: String = "",
        mimeType: String = ""
    ): ChatMessage = withContext(Dispatchers.IO) {
        if (text.isBlank() && (mediaBytes == null || mediaBytes.isEmpty())) {
            throw IOException("El mensaje no puede estar vacío.")
        }

        var mediaUrl = ""
        var msgMediaType = ""
        if (mediaBytes != null && mediaBytes.isNotEmpty()) {
            val uniqueName = "chat_${System.currentTimeMillis()}_${filename.ifEmpty { "img.jpg" }}"
            val uploadRes = moodleApi.uploadToUserEvidence(
                filename = uniqueName,
                mimeType = mimeType.ifEmpty { "image/jpeg" },
                bytes = mediaBytes,
                evidenceTitle = "Moodgram Chat Media"
            )
            mediaUrl = uploadRes.directUrl
            msgMediaType = "image"
        }

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderUsername = sender.username,
            senderDisplayName = sender.displayName,
            senderAvatarRef = sender.avatarRef,
            text = text.trim(),
            mediaUrl = mediaUrl,
            mediaType = msgMediaType,
            createdAt = System.currentTimeMillis(),
            isEdited = false
        )

        chatsMutex.withLock {
            val db = getChatsInternal(forceRemote = false)
            val newDb = if (chatId == AppConfig.OFFICIAL_GROUP_ID) {
                val group = db.officialGroup
                val updatedGroup = group.copy(
                    messages = group.messages + message,
                    lastMessage = if (mediaUrl.isNotEmpty() && text.isBlank()) "📷 Imagen" else text.trim(),
                    lastMessageSender = sender.displayName,
                    lastMessageTime = message.createdAt
                )
                db.copy(officialGroup = updatedGroup, lastUpdated = System.currentTimeMillis())
            } else {
                val updatedDirects = db.directChats.map { direct ->
                    if (direct.id == chatId) {
                        direct.copy(
                            messages = direct.messages + message,
                            lastMessage = if (mediaUrl.isNotEmpty() && text.isBlank()) "📷 Imagen" else text.trim(),
                            lastMessageSender = sender.displayName,
                            lastMessageTime = message.createdAt
                        )
                    } else direct
                }
                db.copy(directChats = updatedDirects, lastUpdated = System.currentTimeMillis())
            }
            saveChatsInternal(newDb)
        }

        return@withContext message
    }

    /**
     * Edita un mensaje de chat.
     * Permitido para el emisor del mensaje Y para el Administrador.
     */
    suspend fun editChatMessage(
        chatId: String,
        messageId: String,
        newText: String,
        requestingUsername: String,
        isAdmin: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        if (newText.isBlank()) throw IOException("El mensaje no puede estar vacío.")

        chatsMutex.withLock {
            val db = getChatsInternal(forceRemote = false)
            val newDb = if (chatId == AppConfig.OFFICIAL_GROUP_ID) {
                val group = db.officialGroup
                val updatedMessages = group.messages.map { msg ->
                    if (msg.id == messageId) {
                        if (!isAdmin && !msg.senderUsername.equals(requestingUsername, ignoreCase = true)) {
                            throw IOException("No tienes permisos para editar este mensaje.")
                        }
                        msg.copy(text = newText.trim(), isEdited = true, editedAt = System.currentTimeMillis())
                    } else msg
                }
                db.copy(officialGroup = group.copy(messages = updatedMessages), lastUpdated = System.currentTimeMillis())
            } else {
                val updatedDirects = db.directChats.map { direct ->
                    if (direct.id == chatId) {
                        val updatedMessages = direct.messages.map { msg ->
                            if (msg.id == messageId) {
                                if (!isAdmin && !msg.senderUsername.equals(requestingUsername, ignoreCase = true)) {
                                    throw IOException("No tienes permisos para editar este mensaje.")
                                }
                                msg.copy(text = newText.trim(), isEdited = true, editedAt = System.currentTimeMillis())
                            } else msg
                        }
                        direct.copy(messages = updatedMessages)
                    } else direct
                }
                db.copy(directChats = updatedDirects, lastUpdated = System.currentTimeMillis())
            }
            saveChatsInternal(newDb)
        }
    }

    /**
     * Elimina un mensaje de chat.
     * Permitido para el emisor del mensaje Y para el Administrador.
     */
    suspend fun deleteChatMessage(
        chatId: String,
        messageId: String,
        requestingUsername: String,
        isAdmin: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        chatsMutex.withLock {
            val db = getChatsInternal(forceRemote = false)
            val newDb = if (chatId == AppConfig.OFFICIAL_GROUP_ID) {
                val group = db.officialGroup
                val filtered = group.messages.filterNot { msg ->
                    if (msg.id == messageId) {
                        if (!isAdmin && !msg.senderUsername.equals(requestingUsername, ignoreCase = true)) {
                            throw IOException("No tienes permisos para eliminar este mensaje.")
                        }
                        true
                    } else false
                }
                val lastMsg = filtered.lastOrNull()
                db.copy(
                    officialGroup = group.copy(
                        messages = filtered,
                        lastMessage = lastMsg?.text,
                        lastMessageSender = lastMsg?.senderDisplayName,
                        lastMessageTime = lastMsg?.createdAt
                    ),
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                val updatedDirects = db.directChats.map { direct ->
                    if (direct.id == chatId) {
                        val filtered = direct.messages.filterNot { msg ->
                            if (msg.id == messageId) {
                                if (!isAdmin && !msg.senderUsername.equals(requestingUsername, ignoreCase = true)) {
                                    throw IOException("No tienes permisos para eliminar este mensaje.")
                                }
                                true
                            } else false
                        }
                        val lastMsg = filtered.lastOrNull()
                        direct.copy(
                            messages = filtered,
                            lastMessage = lastMsg?.text,
                            lastMessageSender = lastMsg?.senderDisplayName,
                            lastMessageTime = lastMsg?.createdAt
                        )
                    } else direct
                }
                db.copy(directChats = updatedDirects, lastUpdated = System.currentTimeMillis())
            }
            saveChatsInternal(newDb)
        }
    }

    /**
     * Agrega o alterna una reacción en un mensaje de chat.
     */
    suspend fun setChatMessageReaction(
        chatId: String,
        messageId: String,
        username: String,
        emoji: String
    ): Unit = withContext(Dispatchers.IO) {
        chatsMutex.withLock {
            val db = getChatsInternal(forceRemote = false)
            fun updateMessages(messages: List<ChatMessage>): List<ChatMessage> {
                return messages.map { msg ->
                    if (msg.id == messageId) {
                        val current = msg.reactions[username]
                        val updated = msg.reactions.toMutableMap()
                        if (current == emoji) {
                            updated.remove(username)
                        } else {
                            updated[username] = emoji
                        }
                        msg.copy(reactions = updated)
                    } else msg
                }
            }

            val newDb = if (chatId == AppConfig.OFFICIAL_GROUP_ID) {
                val group = db.officialGroup
                db.copy(
                    officialGroup = group.copy(messages = updateMessages(group.messages)),
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                val updatedDirects = db.directChats.map { direct ->
                    if (direct.id == chatId) {
                        direct.copy(messages = updateMessages(direct.messages))
                    } else direct
                }
                db.copy(directChats = updatedDirects, lastUpdated = System.currentTimeMillis())
            }
            saveChatsInternal(newDb)
        }
    }

    /**
     * Administrar el Grupo Oficial (nombre, descripción, foto).
     * Solo para Administradores.
     */
    suspend fun updateOfficialGroup(
        newName: String,
        newDescription: String,
        newAvatarBytes: ByteArray?,
        isAdmin: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        if (!isAdmin) throw IOException("Solo el administrador puede configurar el grupo oficial.")

        var avatarUrl: String? = null
        if (newAvatarBytes != null && newAvatarBytes.isNotEmpty()) {
            val filename = "group_avatar_${System.currentTimeMillis()}.jpg"
            val uploadRes = moodleApi.uploadToUserEvidence(
                filename = filename,
                mimeType = "image/jpeg",
                bytes = newAvatarBytes,
                evidenceTitle = "Moodgram Grupo Oficial Avatar"
            )
            avatarUrl = uploadRes.directUrl
        }

        chatsMutex.withLock {
            val db = getChatsInternal(forceRemote = false)
            val group = db.officialGroup
            val updatedGroup = group.copy(
                name = newName.trim().ifEmpty { group.name },
                description = newDescription.trim().ifEmpty { group.description },
                avatarUrl = avatarUrl ?: group.avatarUrl
            )
            val newDb = db.copy(officialGroup = updatedGroup, lastUpdated = System.currentTimeMillis())
            saveChatsInternal(newDb)
        }
    }

    /**
     * Obtiene o crea un chat directo (1 a 1) entre dos usuarios.
     */
    suspend fun getOrCreateDirectChat(user1: String, user2: String): ChatGroup = withContext(Dispatchers.IO) {
        val u1 = user1.trim().lowercase()
        val u2 = user2.trim().lowercase()
        val sorted = listOf(u1, u2).sorted()
        val chatId = "dm_${sorted[0]}_${sorted[1]}"

        chatsMutex.withLock {
            val db = getChatsInternal(forceRemote = false)
            val existing = db.directChats.firstOrNull { it.id == chatId }
            if (existing != null) return@withLock existing

            val usersDb = getUsersInternal(forceRemote = false)
            val partner = usersDb.users.firstOrNull { it.username.equals(user2, ignoreCase = true) }
            val newChat = ChatGroup(
                id = chatId,
                name = partner?.displayName ?: user2,
                avatarUrl = partner?.avatarRef ?: "",
                isOfficialGroup = false,
                members = listOf(user1, user2),
                messages = emptyList()
            )

            val newDb = db.copy(
                directChats = db.directChats + newChat,
                lastUpdated = System.currentTimeMillis()
            )
            saveChatsInternal(newDb)
            return@withLock newChat
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
     * Sincroniza en usuarios, sesión, posts, comentarios y chats.
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
                        avatarRef = newAvatarRef ?: user.avatarRef,
                        lastActive = System.currentTimeMillis()
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

            // Actualizar sesión activa
            sessionManager.updateDisplayName(finalUser.displayName)
            if (finalUser.avatarRef.isNotEmpty()) {
                sessionManager.updateAvatar(finalUser.avatarRef)
            }

            // Actualizar publicaciones y comentarios del autor
            try {
                postsMutex.withLock {
                    val postsDb = getPostsInternal(forceRemote = false)
                    val updatedPosts = postsDb.posts.map { post ->
                        val isAuthor = post.authorUsername.equals(username, ignoreCase = true)
                        val updatedComments = post.comments.map { comment ->
                            if (comment.authorUsername.equals(username, ignoreCase = true)) {
                                comment.copy(
                                    authorDisplayName = finalUser.displayName,
                                    authorAvatarRef = finalUser.avatarRef
                                )
                            } else comment
                        }
                        if (isAuthor) {
                            post.copy(
                                authorDisplayName = finalUser.displayName,
                                authorAvatarRef = finalUser.avatarRef,
                                comments = updatedComments
                            )
                        } else {
                            post.copy(comments = updatedComments)
                        }
                    }
                    savePostsInternal(postsDb.copy(posts = updatedPosts, lastUpdated = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.w(tag, "No se pudieron actualizar publicaciones previas del usuario", e)
            }

            // Actualizar mensajes de chat
            try {
                chatsMutex.withLock {
                    val chatsDb = getChatsInternal(forceRemote = false)
                    fun updateMessages(messages: List<ChatMessage>): List<ChatMessage> {
                        return messages.map { msg ->
                            if (msg.senderUsername.equals(username, ignoreCase = true)) {
                                msg.copy(
                                    senderDisplayName = finalUser.displayName,
                                    senderAvatarRef = finalUser.avatarRef
                                )
                            } else msg
                        }
                    }
                    val updatedGroup = chatsDb.officialGroup.copy(
                        messages = updateMessages(chatsDb.officialGroup.messages)
                    )
                    val updatedDirects = chatsDb.directChats.map { d ->
                        d.copy(messages = updateMessages(d.messages))
                    }
                    saveChatsInternal(chatsDb.copy(officialGroup = updatedGroup, directChats = updatedDirects, lastUpdated = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.w(tag, "No se pudieron actualizar mensajes de chat del usuario", e)
            }

            return@withLock finalUser
        }
    }
}
