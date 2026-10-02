package com.example

import com.example.util.MediaUtils
import com.example.util.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun password_hashing_and_verification_isCorrect() {
        val password = "MySecretPassword123*"
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(password, salt)

        assertNotEquals(password, hash)
        assertTrue(SecurityUtils.verifyPassword(password, salt, hash))
        assertFalse(SecurityUtils.verifyPassword("WrongPassword", salt, hash))
    }

    @Test
    fun formatFileSize_isCorrect() {
        assertEquals("0 MB", MediaUtils.formatFileSize(0))
        assertEquals("500 KB", MediaUtils.formatFileSize(500 * 1024))
        assertEquals("2.0 MB", MediaUtils.formatFileSize(2 * 1024 * 1024))
        assertEquals("4.0 MB", MediaUtils.formatFileSize(4 * 1024 * 1024))
    }

    @Test
    fun formatRelativeTime_isCorrect() {
        val now = System.currentTimeMillis()
        assertEquals("ahora mismo", MediaUtils.formatRelativeTime(now - 1000))
        assertEquals("hace 5 min", MediaUtils.formatRelativeTime(now - 5 * 60 * 1000))
        assertEquals("hace 2 h", MediaUtils.formatRelativeTime(now - 2 * 60 * 60 * 1000))
    }
}
