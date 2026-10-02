package com.example.config

/**
 * Configuración central de Moodgram.
 * Almacena los parámetros de conexión a Moodle y credenciales de administración.
 */
object AppConfig {
    const val MOODLE_URL = "https://cursos.ucf.edu.cu/"
    const val MOODLE_USER = "julianrene"
    const val MOODLE_PASS = "Transfer60*"
    const val MAX_FILE_MB = 4
    const val ADMIN_USERNAME = "@Eliel_21"
    const val ADMIN_PASSWORD = "ElielElielAdmin543345.."

    // Parámetros de almacenamiento en la nube (Evidencias de aprendizaje / userevidence)
    const val DEFAULT_CONTEXT_ID = 44640L
    const val DEFAULT_USER_ID = 2886L
    const val USERS_FILE_PREFIX = "moodgram_v2_usuarios"
    const val POSTS_FILE_PREFIX = "moodgram_v2_publicaciones"
    const val CHATS_FILE_PREFIX = "moodgram_v2_chats"
    const val OFFICIAL_GROUP_ID = "official_group"
    const val USERS_DEFAULT_FILE = "moodgram_v2_usuarios.json"
    const val POSTS_DEFAULT_FILE = "moodgram_v2_publicaciones.json"
    const val CHATS_DEFAULT_FILE = "moodgram_v2_chats.json"

    // Parámetros de servicio de la nube
    const val MOODLE_SERVICE = "moodle_mobile_app"

    // Reacciones soportadas en posts y chats
    val SUPPORTED_REACTIONS = listOf("❤️", "🔥", "😂", "😮", "😢", "👏")

    // Límites de compresión
    const val MAX_AVATAR_DIMENSION = 512
    const val MAX_IMAGE_DIMENSION = 1920
    const val MAX_AVATAR_BYTES = 300 * 1024 // 300 KB objetivo para avatares
    const val MAX_FILE_BYTES = MAX_FILE_MB * 1024 * 1024 // 4 MB exactos
}
