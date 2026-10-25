package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.TaigaConfigUpdateRequest
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object GetConfigTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_config",
        description = "Get current server configuration (API URL, active project, and masked auth token for privacy)",
        inputSchema = SchemaBuilder.objectSchema(properties = emptyMap())
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val config = configManager.getConfig()
        return SchemaBuilder.successJson(config.masked())
    }
}

object SetConfigTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_set_config",
        description = "Remotely update server configuration: API URL, auth token (for maximum privacy), or default working project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "api_url" to SchemaBuilder.stringProp("New Taiga API URL (e.g. 'https://api.taiga.io' or custom instance)"),
                "auth_token" to SchemaBuilder.stringProp("New Bearer Auth Token"),
                "default_project" to SchemaBuilder.stringProp("New default working project (slug or numeric ID)")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val update = TaigaConfigUpdateRequest(
            apiUrl = args["api_url"]?.jsonPrimitive?.contentOrNull,
            authToken = args["auth_token"]?.jsonPrimitive?.contentOrNull,
            defaultProject = args["default_project"]?.jsonPrimitive?.contentOrNull
        )
        val updated = configManager.updateConfig(update)
        return SchemaBuilder.successJson(updated.masked())
    }
}

object LoginTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_login",
        description = "Authenticate with username and password against Taiga API to securely obtain and store an auth token (raw credentials will NOT be retained)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "username" to SchemaBuilder.stringProp("Taiga username or email"),
                "password" to SchemaBuilder.stringProp("Taiga password"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL")
            ),
            required = listOf("username", "password")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val username = args["username"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'username'")
        val password = args["password"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'password'")
        val apiUrl = args["api_url"]?.jsonPrimitive?.contentOrNull

        val targetClient = if (apiUrl != null) {
            val cfg = configManager.resolveConfig(overrideApiUrl = apiUrl)
            TaigaClient(cfg)
        } else {
            client
        }

        val auth = targetClient.login(username, password)
        // Store token in configManager for subsequent requests
        configManager.setAuthToken(auth.authToken)
        if (apiUrl != null) {
            configManager.updateConfig(TaigaConfigUpdateRequest(apiUrl = apiUrl))
        }

        return SchemaBuilder.successText(
            "Authentication successful! Logged in as '${auth.username}' (ID: ${auth.id}). Auth token has been securely set in the server."
        )
    }
}

object TestConnectionTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_test_connection",
        description = "Test connectivity and authentication with Taiga API server by fetching authenticated user details",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val user = client.getMe()
        val config = configManager.getConfig()
        return SchemaBuilder.successText(
            "Connected successfully to Taiga (${config.apiUrl}) as '${user.username}' (Display Name: '${user.fullNameDisplay ?: user.fullName}', ID: ${user.id})."
        )
    }
}
