package dev.afalabarce.mcp.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.server.McpServer
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatusResponse(
    val status: String,
    val service: String,
    val version: String,
    val taigaApiUrl: String,
    val hasToken: Boolean,
    val defaultProject: String
)

@Serializable
data class ServiceDiscoveryResponse(
    val service: String,
    val mcpProtocolVersion: String,
    val swaggerUi: String,
    val openApiSpec: String,
    val sseEndpoint: String,
    val messagesEndpoint: String,
    val configEndpoint: String,
    val healthEndpoint: String
)

@OptIn(ExperimentalKtorApi::class)
fun Route.healthRoutes(configManager: ConfigManager) {
    get("/health") {
        val config = configManager.getConfig()
        call.respond(
            HealthStatusResponse(
                status = "ok",
                service = McpServer.SERVER_NAME,
                version = McpServer.SERVER_VERSION,
                taigaApiUrl = config.apiUrl,
                hasToken = !config.authToken.isNullOrBlank(),
                defaultProject = config.defaultProject ?: "none"
            )
        )
    }.describe {
        summary = "Health check"
        description = "Returns the operational health and configuration state of the MCP server"
        tag("Health")
        responses {
            HttpStatusCode.OK {
                description = "Server is healthy and running"
            }
        }
    }

    get("/") {
        call.respond(
            ServiceDiscoveryResponse(
                service = "Taiga MCP Server",
                mcpProtocolVersion = McpServer.PROTOCOL_VERSION,
                swaggerUi = "/swagger",
                openApiSpec = "/openapi.json",
                sseEndpoint = "/sse",
                messagesEndpoint = "/messages",
                configEndpoint = "/api/config",
                healthEndpoint = "/health"
            )
        )
    }.describe {
        summary = "Service Discovery Root"
        description = "Provides entrypoint links to Swagger, OpenAPI, and MCP endpoints"
        tag("Health")
        responses {
            HttpStatusCode.OK {
                description = "Service discovery index"
            }
        }
    }
}
