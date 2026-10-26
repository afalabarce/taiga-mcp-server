package dev.afalabarce.mcp.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.sse.ServerSSESession
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
 *
 * Supports both legacy HTTP+SSE and modern Streamable HTTP transports seamlessly
 * on both /sse and /mcp endpoints.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mcpRoutes(
    mcpServer: McpServer,
    sessionManager: McpSessionManager
) {
    val sseHandler: suspend ServerSSESession.() -> Unit = {
        val principal = call.principal<TaigaUserPrincipal>()
        val contextAuthToken = principal?.token
        val contextApiUrl = call.request.headers["X-Taiga-Url"] ?: call.request.queryParameters["taiga_url"]
        val contextProject = call.request.headers["X-Taiga-Project"] ?: call.request.queryParameters["taiga_project"]

        val session = sessionManager.createSession(
            authToken = contextAuthToken,
            apiUrl = contextApiUrl,
            project = contextProject
        )
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
    }

    // Standard MCP SSE Transport on /sse
    sse("/sse", sseHandler).describe {
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

    // Modern Streamable HTTP fallback on /sse (prevents 405 Method Not Allowed when Streamable clients point to /sse)
    post("/sse") {
        handleDirectMcp(call, mcpServer)
    }.describe {
        summary = "MCP Streamable HTTP POST receiver on /sse (Protected)"
        description = "Allows modern Streamable HTTP clients to send JSON-RPC requests directly to /sse without 405 error."
        tag("MCP Protocol")
        requestBody {
            description = "JSON-RPC 2.0 request or batch"
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

    // Client posts JSON-RPC messages to this endpoint for active SSE sessions
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

    // Unified SSE Transport on /mcp
    sse("/mcp", sseHandler).describe {
        summary = "MCP Server-Sent Events standby stream on /mcp (Protected)"
        description = "Requires Bearer authentication. Initiates an SSE standby connection on /mcp for asynchronous pushes."
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

    // Direct JSON-RPC over Streamable HTTP (POST /mcp)
    post("/mcp") {
        handleDirectMcp(call, mcpServer)
    }.describe {
        summary = "Direct MCP Streamable HTTP JSON-RPC endpoint (Protected)"
        description = "Requires Bearer authentication. Allows posting standard MCP JSON-RPC requests synchronously over HTTP with Streamable transport."
        tag("MCP Protocol")
        requestBody {
            description = "JSON-RPC 2.0 request or batch payload"
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

/**
 * Handles direct synchronous JSON-RPC requests (single or batch) over Streamable HTTP.
 */
private suspend fun handleDirectMcp(call: RoutingCall, mcpServer: McpServer) {
    val principal = call.principal<TaigaUserPrincipal>()
    val contextAuthToken = principal?.token
    val contextApiUrl = call.request.headers["X-Taiga-Url"] ?: call.request.queryParameters["taiga_url"]
    val contextProject = call.request.headers["X-Taiga-Project"] ?: call.request.queryParameters["taiga_project"]

    val sessionId = call.request.headers["Mcp-Session-Id"]
        ?: call.request.queryParameters["sessionId"]
    if (!sessionId.isNullOrBlank()) {
        call.response.headers.append("Mcp-Session-Id", sessionId)
    }

    val bodyText = call.receiveText().trim()
    if (bodyText.isEmpty()) {
        call.respond(HttpStatusCode.BadRequest, "Empty request body")
        return
    }

    if (bodyText.startsWith("[")) {
        // Batch JSON-RPC request
        val requests = try {
            TaigaClient.json.decodeFromString<List<JsonRpcRequest>>(bodyText)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid batch JSON-RPC request: ${e.message}")
            return
        }

        val responses = requests.mapNotNull {
            mcpServer.handleRequest(it, contextAuthToken, contextApiUrl, contextProject)
        }
        if (responses.isNotEmpty()) {
            call.respond(responses)
        } else {
            call.respond(HttpStatusCode.NoContent)
        }
    } else {
        // Single JSON-RPC request
        val request = try {
            TaigaClient.json.decodeFromString<JsonRpcRequest>(bodyText)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid JSON-RPC request: ${e.message}")
            return
        }

        val response = mcpServer.handleRequest(request, contextAuthToken, contextApiUrl, contextProject)
        if (response != null) {
            call.respond(response)
        } else {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
