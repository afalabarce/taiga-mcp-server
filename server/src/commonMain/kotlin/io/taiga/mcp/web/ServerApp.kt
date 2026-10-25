package io.taiga.mcp.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.taiga.mcp.config.ConfigManager
import io.taiga.mcp.mcp.server.McpServer
import io.taiga.mcp.mcp.server.McpSessionManager
import io.taiga.mcp.mcp.tools.TaigaToolRegistry
import io.taiga.mcp.taiga.client.TaigaApiException
import io.taiga.mcp.taiga.client.TaigaClient
import io.taiga.mcp.taiga.client.TaigaClientFactory
import io.taiga.mcp.web.auth.configureAuthentication
import io.taiga.mcp.web.routes.configRoutes
import io.taiga.mcp.web.routes.healthRoutes
import io.taiga.mcp.web.routes.loginRoutes
import io.taiga.mcp.web.routes.mcpRoutes
import io.taiga.mcp.web.routes.mcpToolAndTaigaRoutes
import io.taiga.mcp.web.routes.swaggerDocsRoutes

fun Application.configureServer(
    configManager: ConfigManager,
    mcpServer: McpServer,
    sessionManager: McpSessionManager,
    toolRegistry: TaigaToolRegistry,
    clientFactory: TaigaClientFactory
) {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    install(CallLogging)

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Taiga-Url")
        allowHeader("X-Taiga-Token")
        allowHeader("X-Taiga-Project")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    install(ContentNegotiation) {
        json(TaigaClient.json)
    }

    install(SSE)

    // Install Bearer Token Authentication
    configureAuthentication(configManager)

    install(StatusPages) {
        exception<TaigaApiException> { call, cause ->
            call.respond(
                HttpStatusCode.fromValue(cause.statusCode),
                mapOf("error" to cause.message, "status" to cause.statusCode)
            )
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal Server Error"))
            )
        }
    }

    routing {
        // --- 1. Public Routes ---
        healthRoutes(configManager)
        loginRoutes(configManager, clientFactory)
        swaggerDocsRoutes()

        // --- 2. Protected Routes (Protected with authenticate("auth-bearer")) ---
        authenticate("auth-bearer") {
            configRoutes(configManager)
            mcpRoutes(mcpServer, sessionManager)
            mcpToolAndTaigaRoutes(toolRegistry, clientFactory)
        }
    }
}
