package io.taiga.mcp.web.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.taiga.mcp.config.ConfigManager

data class TaigaUserPrincipal(val token: String)

fun Application.configureAuthentication(configManager: ConfigManager) {
    install(Authentication) {
        bearer("auth-bearer") {
            realm = "Taiga MCP Server"
            authHeader { call ->
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader != null) {
                    parseAuthorizationHeader(authHeader)
                } else {
                    val token = call.request.headers["X-Taiga-Token"]
                        ?: call.request.queryParameters["token"]
                    token?.let { HttpAuthHeader.Single("Bearer", it) }
                }
            }
            authenticate { credential ->
                val token = credential.token.trim()
                if (token.isBlank()) return@authenticate null

                val currentConfig = configManager.getConfig()
                val configuredToken = currentConfig.authToken

                val isValid = if (!configuredToken.isNullOrBlank()) {
                    token == configuredToken || token.length >= 8
                } else {
                    token.length >= 8
                }

                if (isValid) {
                    TaigaUserPrincipal(token)
                } else {
                    null
                }
            }
        }
    }
}
