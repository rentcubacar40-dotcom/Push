package com.example.data.model

data class UsersDatabase(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val users: List<User> = emptyList()
)

data class PostsDatabase(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val posts: List<Post> = emptyList()
)
