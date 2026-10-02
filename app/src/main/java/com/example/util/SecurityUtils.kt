package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {

    /**
     * Genera un salt criptográfico aleatorio en formato hexadecimal.
     */
    fun generateSalt(length: Int = 16): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(length)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calcula el hash SHA-256 de una contraseña combinada con el salt.
     * Nunca se guardan contraseñas en texto plano.
     */
    fun hashPassword(password: String, salt: String): String {
        val combined = "$password:$salt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifica si una contraseña coincide con el hash y salt almacenados.
     */
    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val calculated = hashPassword(password, salt)
        return calculated.equals(expectedHash, ignoreCase = true)
    }
}
