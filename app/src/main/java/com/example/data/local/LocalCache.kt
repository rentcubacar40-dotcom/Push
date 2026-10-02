package com.example.data.local

import android.content.Context
import com.example.data.model.PostsDatabase
import com.example.data.model.UsersDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalCache(private val context: Context) {
    private val gson = Gson()

    private val usersFile: File
        get() = File(context.filesDir, "cached_usuarios.json")

    private val postsFile: File
        get() = File(context.filesDir, "cached_publicaciones.json")

    suspend fun saveUsers(database: UsersDatabase) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(database)
            usersFile.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    suspend fun getUsers(): UsersDatabase? = withContext(Dispatchers.IO) {
        try {
            if (usersFile.exists()) {
                val json = usersFile.readText(Charsets.UTF_8)
                gson.fromJson(json, UsersDatabase::class.java)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun savePosts(database: PostsDatabase) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(database)
            postsFile.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    suspend fun getPosts(): PostsDatabase? = withContext(Dispatchers.IO) {
        try {
            if (postsFile.exists()) {
                val json = postsFile.readText(Charsets.UTF_8)
                gson.fromJson(json, PostsDatabase::class.java)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            usersFile.delete()
            postsFile.delete()
        } catch (_: Exception) {}
    }
}
