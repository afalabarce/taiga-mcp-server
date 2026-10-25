package dev.afalabarce.mcp.mcp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    companion object {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }
}

// MCP Specific Models

@Serializable
data class McpImplementation(
    val name: String,
    val version: String
)

@Serializable
data class McpServerCapabilities(
    val tools: JsonObject? = null,
    val resources: JsonObject? = null,
    val prompts: JsonObject? = null,
    val logging: JsonObject? = null
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpServerCapabilities,
    val serverInfo: McpImplementation
)

@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class McpToolsListResult(
    val tools: List<McpToolDefinition>
)

@Serializable
data class McpCallToolRequest(
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class McpContent(
    val type: String = "text",
    val text: String
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)

@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

@Serializable
data class McpResourcesListResult(
    val resources: List<McpResource>
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList()
)

@Serializable
data class McpPromptsListResult(
    val prompts: List<McpPrompt>
)
