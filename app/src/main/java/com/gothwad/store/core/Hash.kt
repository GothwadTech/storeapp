package com.gothwad.store.core

import java.io.File
import java.security.MessageDigest

/** SHA-256 of a file as lowercase hex — used to verify published APKs. */
fun File.hexDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** SHA-256 of a string as lowercase hex. */
fun String.hexDigest(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
