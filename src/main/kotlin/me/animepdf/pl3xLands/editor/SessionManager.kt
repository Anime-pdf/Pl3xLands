package me.animepdf.pl3xLands.editor

import me.animepdf.pl3xLands.config.EditorAuthConfig
import me.animepdf.pl3xLands.config.EditorAuthType
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class SessionManager(
    private val authConfig: EditorAuthConfig,
    private val logger: Logger
) {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val random = SecureRandom()

    data class Session(
        val token: String,
        val createdAt: Long,
        val expiresAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    fun authenticate(credentials: Map<String, String>): String? {
        if (!authConfig.enable) {
            return createSession()
        }

        val isValid = when (authConfig.type) {
            EditorAuthType.BASIC -> {
                credentials["username"] == authConfig.basic.username &&
                        credentials["password"] == authConfig.basic.password
            }

            EditorAuthType.BEARER -> {
                credentials["token"] == authConfig.bearer.token
            }
        }

        return if (isValid) {
            val token = createSession()
            logger.info("New session created (auth type: ${authConfig.type})")
            token
        } else {
            logger.warning("Authentication failed (auth type: ${authConfig.type})")
            null
        }
    }

    fun validateSession(token: String): Boolean {
        val session = sessions[token] ?: return false

        if (session.isExpired()) {
            sessions.remove(token)
            logger.info("Session expired and removed")
            return false
        }

        return true
    }

    fun invalidateSession(token: String) {
        sessions.remove(token)
        logger.info("Session invalidated")
    }

    fun cleanupExpiredSessions() {
        val before = sessions.size
        sessions.entries.removeIf { it.value.isExpired() }
        val removed = before - sessions.size

        if (removed > 0) {
            logger.info("Cleaned up $removed expired sessions")
        }
    }

    private fun createSession(): String {
        val token = generateToken()
        val now = System.currentTimeMillis()
        val expiresAt = now + (authConfig.sessionTimeout * 1000)

        sessions[token] = Session(
            token = token,
            createdAt = now,
            expiresAt = expiresAt
        )

        return token
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}