package com.tutorkim.backend.identity.service

import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class AuthTokenHasher {
    fun sha256Hex(rawToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
