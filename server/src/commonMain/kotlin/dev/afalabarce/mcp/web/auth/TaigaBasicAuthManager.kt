package dev.afalabarce.mcp.web.auth

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.taiga.client.TaigaApiException
import dev.afalabarce.mcp.taiga.client.TaigaClientFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Manages HTTP Basic Authentication against Taiga API with token caching and renewal.
 *
 * Avoids JWT token expirations by allowing clients to send classic username:password
 * credentials, dynamically logging in against Taiga and caching the resolved bearer token.
 */
open class TaigaBasicAuthManager(
    private val configManager: ConfigManager,
    private val clientFactory: TaigaClientFactory
) {
    private val mutex = Mutex()
    private val tokenCache = mutableMapOf<String, CachedToken>()

    private data class CachedToken(
        val token: String,
        val timeMark: TimeMark
    )

    private fun buildCacheKey(apiUrl: String, username: String, password: String): String =
        "$apiUrl\u0000$username\u0000$password"

    /**
     * Authenticates the user against Taiga API or retrieves a valid cached JWT token.
     * Tokens are cached for up to 1 hour to prevent redundant login calls.
     */
    suspend fun authenticate(
        username: String,
        password: String,
        requestApiUrl: String? = null
    ): String? {
        if (username.isBlank() || password.isBlank()) return null

        val currentConfig = configManager.getConfig()
        val targetApiUrl = requestApiUrl?.ifBlank { null }
            ?: currentConfig.apiUrl

        val cacheKey = buildCacheKey(targetApiUrl, username, password)

        mutex.withLock {
            val cached = tokenCache[cacheKey]
            if (cached != null && cached.timeMark.elapsedNow() < 1.hours) {
                return cached.token
            }
        }

        // Token not cached or expired - authenticate with Taiga
        return try {
            val token = performLogin(username, password, targetApiUrl)

            mutex.withLock {
                tokenCache[cacheKey] = CachedToken(
                    token = token,
                    timeMark = TimeSource.Monotonic.markNow()
                )
            }
            token
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Executes the actual login HTTP request to Taiga API.
     * Can be overridden in unit tests to mock authentication without network.
     */
    open suspend fun performLogin(username: String, password: String, targetApiUrl: String): String {
        val client = clientFactory.getClient(overrideApiUrl = targetApiUrl)
        val authResponse = client.login(username = username, password = password)
        return authResponse.authToken
    }

    /**
     * Invalidates a cached credential, e.g. after receiving a 401 from Taiga.
     */
    suspend fun invalidate(
        username: String,
        password: String,
        requestApiUrl: String? = null
    ) {
        val currentConfig = configManager.getConfig()
        val targetApiUrl = requestApiUrl?.ifBlank { null }
            ?: currentConfig.apiUrl

        val cacheKey = buildCacheKey(targetApiUrl, username, password)
        mutex.withLock {
            tokenCache.remove(cacheKey)
        }
    }

    /**
     * Clears all cached tokens.
     */
    suspend fun clearCache() {
        mutex.withLock {
            tokenCache.clear()
        }
    }
}
