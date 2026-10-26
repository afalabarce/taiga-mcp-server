package dev.afalabarce.mcp.web.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import dev.afalabarce.mcp.config.ConfigManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Authentication Principal representing an authenticated user.
 *
 * @param token Active Taiga Bearer / JWT token used for API requests.
 * @param username Optional username if authenticated via Basic Auth.
 * @param isBasicAuth True if the request used HTTP Basic Auth, false if Bearer token.
 */
data class TaigaUserPrincipal(
    val token: String,
    val username: String? = null,
    val isBasicAuth: Boolean = false
)

/**
 * Decodes a Base64-encoded "username:password" string.
 */
@OptIn(ExperimentalEncodingApi::class)
fun decodeBasicCredentials(base64Text: String): Pair<String, String>? {
    return try {
        val clean = base64Text.trim()
        val decoded = Base64.decode(clean).decodeToString()
        val separatorIndex = decoded.indexOf(':')
        if (separatorIndex >= 0) {
            val username = decoded.substring(0, separatorIndex)
            val password = decoded.substring(separatorIndex + 1)
            Pair(username, password)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Configures both Bearer Token and HTTP Basic authentication for the Ktor server.
 */
fun Application.configureAuthentication(
    configManager: ConfigManager,
    basicAuthManager: TaigaBasicAuthManager
) {
    install(Authentication) {
        // 1. Bearer Token Provider ("auth-bearer")
        bearer("auth-bearer") {
            realm = "Taiga MCP Server"
            authHeader { call ->
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
                    parseAuthorizationHeader(authHeader)
                } else if (authHeader == null) {
                    val basicParam = call.request.headers["X-Taiga-Basic"]
                        ?: call.request.queryParameters["basic"]
                    if (basicParam != null) {
                        HttpAuthHeader.Single("Bearer", "b64_$basicParam")
                    } else {
                        val token = call.request.headers["X-Taiga-Token"]
                            ?: call.request.queryParameters["token"]
                        token?.let { HttpAuthHeader.Single("Bearer", it) }
                    }
                } else {
                    null
                }
            }
            authenticate { credential ->
                val rawToken = credential.token.trim()
                if (rawToken.isBlank()) return@authenticate null

                // Handle X-Taiga-Basic or ?basic= passed as synthetic bearer
                if (rawToken.startsWith("b64_")) {
                    val b64 = rawToken.removePrefix("b64_")
                    val creds = decodeBasicCredentials(b64) ?: return@authenticate null
                    val requestApiUrl = request.headers["X-Taiga-Url"]
                        ?: request.queryParameters["taiga_url"]
                    val token = basicAuthManager.authenticate(
                        username = creds.first,
                        password = creds.second,
                        requestApiUrl = requestApiUrl
                    )
                    return@authenticate if (token != null) {
                        TaigaUserPrincipal(
                            token = token,
                            username = creds.first,
                            isBasicAuth = true
                        )
                    } else {
                        null
                    }
                }

                val currentConfig = configManager.getConfig()
                val configuredToken = currentConfig.authToken

                val isValid = if (!configuredToken.isNullOrBlank()) {
                    rawToken == configuredToken || rawToken.length >= 8
                } else {
                    rawToken.length >= 8
                }

                if (isValid) {
                    TaigaUserPrincipal(token = rawToken, isBasicAuth = false)
                } else {
                    null
                }
            }
        }

        // 2. HTTP Basic Provider ("auth-basic")
        basic("auth-basic") {
            realm = "Taiga MCP Server"
            validate { credentials ->
                val username = credentials.name.trim()
                val password = credentials.password
                if (username.isBlank() || password.isBlank()) return@validate null

                val requestApiUrl = request.headers["X-Taiga-Url"]
                    ?: request.queryParameters["taiga_url"]

                val token = basicAuthManager.authenticate(
                    username = username,
                    password = password,
                    requestApiUrl = requestApiUrl
                )

                if (token != null) {
                    TaigaUserPrincipal(
                        token = token,
                        username = username,
                        isBasicAuth = true
                    )
                } else {
                    null
                }
            }
        }
    }
}
