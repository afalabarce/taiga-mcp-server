package dev.afalabarce.mcp.mcp.server

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.JsonRpcError
import dev.afalabarce.mcp.mcp.protocol.JsonRpcRequest
import dev.afalabarce.mcp.mcp.protocol.JsonRpcResponse
import dev.afalabarce.mcp.mcp.protocol.McpImplementation
import dev.afalabarce.mcp.mcp.protocol.McpInitializeResult
import dev.afalabarce.mcp.mcp.protocol.McpPrompt
import dev.afalabarce.mcp.mcp.protocol.McpPromptArgument
import dev.afalabarce.mcp.mcp.protocol.McpPromptsListResult
import dev.afalabarce.mcp.mcp.protocol.McpResource
import dev.afalabarce.mcp.mcp.protocol.McpResourcesListResult
import dev.afalabarce.mcp.mcp.protocol.McpServerCapabilities
import dev.afalabarce.mcp.mcp.protocol.McpToolsListResult
import dev.afalabarce.mcp.mcp.tools.TaigaToolRegistry
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class McpServer(
    val toolRegistry: TaigaToolRegistry,
    val configManager: ConfigManager
) {
    companion object {
        const val SERVER_NAME = "taiga-mcpserver"
        const val SERVER_VERSION = "1.0.0"
        const val PROTOCOL_VERSION = "2024-11-05"
    }

    suspend fun handleRequest(
        request: JsonRpcRequest,
        contextAuthToken: String? = null,
        contextApiUrl: String? = null,
        contextProject: String? = null
    ): JsonRpcResponse? {
        // Notification (id is null)
        if (request.id == null && request.method == "notifications/initialized") {
            return null
        }

        return try {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "ping" -> JsonRpcResponse(id = request.id, result = buildJsonObject {})
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolsCall(request, contextAuthToken, contextApiUrl, contextProject)
                "resources/list" -> handleResourcesList(request)
                "resources/read" -> handleResourcesRead(request)
                "prompts/list" -> handlePromptsList(request)
                "prompts/get" -> handlePromptsGet(request)
                else -> JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(
                        code = JsonRpcError.METHOD_NOT_FOUND,
                        message = "Method '${request.method}' not implemented by Taiga MCP server"
                    )
                )
            }
        } catch (e: Exception) {
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = JsonRpcError.INTERNAL_ERROR,
                    message = e.message ?: "Internal server error"
                )
            )
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val initResult = McpInitializeResult(
            protocolVersion = PROTOCOL_VERSION,
            capabilities = McpServerCapabilities(
                tools = buildJsonObject { put("listChanged", false) },
                resources = buildJsonObject { put("subscribe", false) },
                prompts = buildJsonObject { put("listChanged", false) }
            ),
            serverInfo = McpImplementation(
                name = SERVER_NAME,
                version = SERVER_VERSION
            )
        )
        return JsonRpcResponse(
            id = request.id,
            result = TaigaClient.json.encodeToJsonElement(initResult)
        )
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = toolRegistry.listToolDefinitions()
        val result = McpToolsListResult(tools = tools)
        return JsonRpcResponse(
            id = request.id,
            result = TaigaClient.json.encodeToJsonElement(result)
        )
    }

    private suspend fun handleToolsCall(
        request: JsonRpcRequest,
        contextAuthToken: String? = null,
        contextApiUrl: String? = null,
        contextProject: String? = null
    ): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(code = JsonRpcError.INVALID_PARAMS, message = "Missing params for tools/call")
        )

        val toolName = params["name"]?.jsonPrimitive?.contentOrNull ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(code = JsonRpcError.INVALID_PARAMS, message = "Missing 'name' in tools/call params")
        )

        val arguments = params["arguments"] as? JsonObject ?: buildJsonObject {}
        val callResult = toolRegistry.executeTool(
            name = toolName,
            arguments = arguments,
            defaultAuthToken = contextAuthToken,
            defaultApiUrl = contextApiUrl,
            defaultProject = contextProject
        )

        return JsonRpcResponse(
            id = request.id,
            result = TaigaClient.json.encodeToJsonElement(callResult)
        )
    }

    private fun handleResourcesList(request: JsonRpcRequest): JsonRpcResponse {
        val resources = listOf(
            McpResource(
                uri = "taiga://config",
                name = "Current Server Configuration",
                description = "Shows active API URL, default project, and auth status",
                mimeType = "application/json"
            )
        )
        val result = McpResourcesListResult(resources)
        return JsonRpcResponse(
            id = request.id,
            result = TaigaClient.json.encodeToJsonElement(result)
        )
    }

    private suspend fun handleResourcesRead(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params
        val uri = params?.get("uri")?.jsonPrimitive?.contentOrNull

        return when (uri) {
            "taiga://config" -> {
                val cfg = configManager.getConfig().masked()
                val jsonText = TaigaClient.json.encodeToString(cfg)
                val contentObj = buildJsonObject {
                    put("uri", uri)
                    put("mimeType", "application/json")
                    put("text", jsonText)
                }
                JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        put("contents", TaigaClient.json.encodeToJsonElement(listOf(contentObj)))
                    }
                )
            }
            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(code = JsonRpcError.INVALID_PARAMS, message = "Unknown resource URI: $uri")
            )
        }
    }

    private fun handlePromptsList(request: JsonRpcRequest): JsonRpcResponse {
        val prompts = listOf(
            McpPrompt(
                name = "summarize-sprint",
                description = "Summarize the current sprint status, completed stories, and remaining tasks",
                arguments = listOf(
                    McpPromptArgument("milestone_id", "Numeric ID of the sprint / milestone", required = true)
                )
            ),
            McpPrompt(
                name = "plan-user-story",
                description = "Generate a breakdown of technical tasks for a given user story",
                arguments = listOf(
                    McpPromptArgument("story_id", "Numeric ID of the user story", required = true)
                )
            )
        )
        return JsonRpcResponse(
            id = request.id,
            result = TaigaClient.json.encodeToJsonElement(McpPromptsListResult(prompts))
        )
    }

    private fun handlePromptsGet(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params
        val name = params?.get("name")?.jsonPrimitive?.contentOrNull
        val promptArgs = params?.get("arguments") as? JsonObject

        val promptResult = when (name) {
            "summarize-sprint" -> {
                val milestoneId = promptArgs?.get("milestone_id")?.jsonPrimitive?.contentOrNull ?: "unknown"
                buildJsonObject {
                    put("description", "Sprint summary prompt")
                    put("messages", TaigaClient.json.encodeToJsonElement(listOf(
                        buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonObject {
                                put("type", "text")
                                put("text", "Please analyze sprint milestone #$milestoneId. List all user stories and tasks, calculate total and closed points, identify blocked or in-progress items, and summarize overall team progress.")
                            })
                        }
                    )))
                }
            }
            "plan-user-story" -> {
                val storyId = promptArgs?.get("story_id")?.jsonPrimitive?.contentOrNull ?: "unknown"
                buildJsonObject {
                    put("description", "User story breakdown prompt")
                    put("messages", TaigaClient.json.encodeToJsonElement(listOf(
                        buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonObject {
                                put("type", "text")
                                put("text", "Please read user story #$storyId from Taiga, break it down into detailed actionable tasks, suggest acceptance criteria, and recommend task statuses.")
                            })
                        }
                    )))
                }
            }
            else -> return JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(code = JsonRpcError.INVALID_PARAMS, message = "Prompt '$name' not found")
            )
        }

        return JsonRpcResponse(
            id = request.id,
            result = promptResult
        )
    }
}
