package dev.afalabarce.mcp.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ExperimentalKtorApi
import dev.afalabarce.mcp.mcp.protocol.JsonRpcRequest
import dev.afalabarce.mcp.mcp.server.McpServer
import dev.afalabarce.mcp.mcp.server.McpSessionManager
import dev.afalabarce.mcp.taiga.client.TaigaClient
import dev.afalabarce.mcp.web.auth.TaigaUserPrincipal
import kotlinx.coroutines.channels.ClosedReceiveChannelException

/**
 * Protected MCP Routes: /sse, /messages, and /mcp.
 * Protected by authenticate("auth-bearer").
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mcpRoutes(
    mcpServer: McpServer,
    sessionManager: McpSessionManager
) {
    // Standard MCP SSE Transport
    sse("/sse") {
        val session = sessionManager.createSession()
        try {
            send(
                ServerSentEvent(
                    event = "endpoint",
                    data = "/messages?sessionId=${session.id}"
                )
            )

            while (true) {
                try {
                    val message = session.channel.receive()
                    send(
                        ServerSentEvent(
                            event = "message",
                            data = message
                        )
                    )
                } catch (e: ClosedReceiveChannelException) {
                    break
                }
            }
        } finally {
            sessionManager.removeSession(session.id)
        }
    }.describe {
        summary = "MCP Server-Sent Events Endpoint (Protected)"
        description = "Requires Bearer authentication. Initiates an SSE streaming connection for Model Context Protocol sessions."
        tag("MCP Protocol")
        responses {
            HttpStatusCode.OK {
                description = "SSE event stream opened"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }

    // Client posts JSON-RPC messages to this endpoint
    post("/messages") {
        val sessionId = call.request.queryParameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing query parameter 'sessionId'")
            return@post
        }

        val bodyText = call.receiveText()
        val request = try {
            TaigaClient.json.decodeFromString<JsonRpcRequest>(bodyText)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON-RPC request: ${e.message}")
            return@post
        }

        val handled = sessionManager.handleIncomingMessage(sessionId, request)
        if (!handled) {
            call.respond(HttpStatusCode.NotFound, "Session '$sessionId' not found or expired")
            return@post
        }

        call.respond(HttpStatusCode.Accepted, "Accepted")
    }.describe {
        summary = "MCP JSON-RPC message receiver (Protected)"
        description = "Requires Bearer authentication. Endpoint to post MCP JSON-RPC messages for an active SSE session."
        tag("MCP Protocol")
        parameters {
            query("sessionId") {
                description = "Active SSE session ID"
                required = true
            }
        }
        requestBody {
            description = "JSON-RPC 2.0 request"
            required = true
            ContentType.Application.Json {
                schema = jsonSchema<JsonRpcRequest>()
            }
        }
        responses {
            HttpStatusCode.Accepted {
                description = "Message received and dispatched"
            }
            HttpStatusCode.BadRequest {
                description = "Invalid request format or missing sessionId"
            }
            HttpStatusCode.NotFound {
                description = "Session not found"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }

    // Direct JSON-RPC over HTTP (POST /mcp)
    post("/mcp") {
        val bodyText = call.receiveText()
        val request = try {
            TaigaClient.json.decodeFromString<JsonRpcRequest>(bodyText)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON-RPC request: ${e.message}")
            return@post
        }

        val response = mcpServer.handleRequest(request)
        if (response != null) {
            call.respond(response)
        } else {
            call.respond(HttpStatusCode.NoContent)
        }
    }.describe {
        summary = "Direct MCP JSON-RPC endpoint (Protected)"
        description = "Requires Bearer authentication. Allows posting standard MCP JSON-RPC requests synchronously over HTTP without SSE."
        tag("MCP Protocol")
        requestBody {
            description = "JSON-RPC 2.0 request payload"
            required = true
            ContentType.Application.Json {
                schema = jsonSchema<JsonRpcRequest>()
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "JSON-RPC response payload"
            }
            HttpStatusCode.NoContent {
                description = "Notification acknowledged"
            }
            HttpStatusCode.BadRequest {
                description = "Parse error or invalid JSON-RPC"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }
}
