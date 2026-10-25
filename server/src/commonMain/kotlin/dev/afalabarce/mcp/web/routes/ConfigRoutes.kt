package dev.afalabarce.mcp.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.TaigaConfigUpdateRequest
import dev.afalabarce.mcp.config.TaigaLoginRequest
import dev.afalabarce.mcp.config.TaigaLoginResponse
import dev.afalabarce.mcp.taiga.client.TaigaApiException
import dev.afalabarce.mcp.taiga.client.TaigaClientFactory

/**
 * Public authentication route: Authenticates against Taiga API using username and password
 * to obtain an auth token (for tokenized access to protected MCP routes).
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.loginRoutes(
    configManager: ConfigManager,
    clientFactory: TaigaClientFactory
) {
    post("/api/auth/login") {
        val request = call.receive<TaigaLoginRequest>()
        try {
            val client = clientFactory.getClient(overrideApiUrl = request.apiUrl)
            val auth = client.login(request.username, request.password, request.type)

            // Update stored auth token in the server
            configManager.setAuthToken(auth.authToken)
            request.apiUrl?.let {
                configManager.updateConfig(TaigaConfigUpdateRequest(apiUrl = it))
            }

            call.respond(
                TaigaLoginResponse(
                    success = true,
                    message = "Successfully authenticated against Taiga. Use this token as 'Authorization: Bearer <token>' for protected MCP calls.",
                    authToken = auth.authToken,
                    userId = auth.id,
                    username = auth.username,
                    email = auth.email
                )
            )
        } catch (e: TaigaApiException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                TaigaLoginResponse(
                    success = false,
                    message = "Authentication failed: ${e.message}"
                )
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                TaigaLoginResponse(
                    success = false,
                    message = "Login error: ${e.message}"
                )
            )
        }
    }.describe {
        summary = "Authenticate with username and password to obtain a token"
        description = "Public authentication endpoint. Authenticates against Taiga API, updates the server's auth token, and returns the token for tokenized Bearer authentication on protected routes."
        tag("Configuration & Auth")
        requestBody {
            description = "Taiga user credentials"
            required = true
        }
        responses {
            HttpStatusCode.OK {
                description = "Authentication successful, token returned"
            }
            HttpStatusCode.Unauthorized {
                description = "Invalid credentials"
            }
        }
    }
}

/**
 * Protected configuration routes: Require authentication via authenticate("auth-bearer")
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.configRoutes(
    configManager: ConfigManager
) {
    get("/api/config") {
        val config = configManager.getConfig()
        call.respond(config.masked())
    }.describe {
        summary = "Get current server configuration (Protected)"
        description = "Requires Bearer authentication. Returns current API URL, default working project, and masked auth token for privacy."
        tag("Configuration & Auth")
        responses {
            HttpStatusCode.OK {
                description = "Current configuration"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }

    post("/api/config") {
        val update = call.receive<TaigaConfigUpdateRequest>()
        val updated = configManager.updateConfig(update)
        call.respond(updated.masked())
    }.describe {
        summary = "Update server configuration remotely (Protected)"
        description = "Requires Bearer authentication. Dynamically updates Taiga API URL, Bearer Auth Token, or Default Working Project."
        tag("Configuration & Auth")
        requestBody {
            description = "Configuration parameters to update"
            required = true
        }
        responses {
            HttpStatusCode.OK {
                description = "Updated configuration"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }
}
