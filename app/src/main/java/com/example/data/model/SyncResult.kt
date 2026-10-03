package com.example.data.model

/**
 * Resultado explícito de operaciones de sincronización con el servidor Moodle.
 * Elimina el comportamiento de simular éxito cuando una subida falla.
 */
sealed interface SyncResult {
    /**
     * La operación se persistió localmente y se confirmó exitosa en el servidor Moodle.
     */
    data object Synced : SyncResult

    /**
     * La operación se guardó localmente en caché pero la sincronización remota
     * está en cola o esperando confirmación del servidor.
     */
    data class Pending(val reason: String = "Pendiente de sincronizar con Moodle") : SyncResult

    /**
     * La subida al servidor falló debido a problemas de red, timeout o error de servicio.
     * El borrador o dato se preserva localmente para permitir reintento.
     */
    data class Failed(val message: String, val cause: Throwable? = null) : SyncResult

    /**
     * Se detectó una versión remota más reciente en Moodle escrita por otro dispositivo.
     */
    data class Conflict(val remoteVersion: Long?) : SyncResult
}
