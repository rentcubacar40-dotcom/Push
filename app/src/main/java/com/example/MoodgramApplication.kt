package com.example

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.repository.MoodgramRepository
import com.example.util.NotificationHelper

class MoodgramApplication : Application() {

    lateinit var repository: MoodgramRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Repositorio único a nivel de Application
        repository = MoodgramRepository.getInstance(applicationContext)

        // 2. Canal de Notificaciones para mensajes y actividad
        NotificationHelper.initNotificationChannel(applicationContext)

        // 3. Coil optimizado globalmente: MemoryCache 30% RAM + DiskCache 150MB persistente
        val imageLoader = ImageLoader.Builder(applicationContext)
            .crossfade(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("moodgram_media_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }

    companion object {
        lateinit var instance: MoodgramApplication
            private set
    }
}
