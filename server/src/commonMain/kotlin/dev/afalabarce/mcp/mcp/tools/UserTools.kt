package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object GetMeTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_me",
        description = "Get detailed profile information about the currently authenticated user in Taiga",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val me = client.getMe()
        return SchemaBuilder.successJson(me)
    }
}

object GetUserTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user",
        description = "Get public profile information about a Taiga user by their numeric user ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric user ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val user = client.getUser(id)
        return SchemaBuilder.successJson(user)
    }
}

object GetUserContactsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user_contacts",
        description = "Get contacts/connections for a Taiga user",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric user ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val contacts = client.getUserContacts(id)
        return SchemaBuilder.successJson(contacts)
    }
}

object GetUserLikedTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user_liked",
        description = "Get items liked by a Taiga user",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric user ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val liked = client.getUserLiked(id)
        return SchemaBuilder.successJson(liked)
    }
}

object GetUserVotedTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user_voted",
        description = "Get items voted for by a Taiga user",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric user ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val voted = client.getUserVoted(id)
        return SchemaBuilder.successJson(voted)
    }
}

object GetUserWatchedTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user_watched",
        description = "Get items watched by a Taiga user",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric user ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val watched = client.getUserWatched(id)
        return SchemaBuilder.successJson(watched)
    }
}
