package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object UpvoteTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_upvote",
        description = "Add a vote to a user story, task, issue, or epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', or 'epic'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the item to vote for"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        client.upvote(type, id)
        return SchemaBuilder.successText("Successfully added vote to $type #$id.")
    }
}

object DownvoteTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_downvote",
        description = "Remove your vote from a user story, task, issue, or epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', or 'epic'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the item to remove vote from"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        client.downvote(type, id)
        return SchemaBuilder.successText("Successfully removed vote from $type #$id.")
    }
}

object WatchTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_watch",
        description = "Watch an item (user story, task, issue, epic, sprint/milestone, wiki, or project) to receive notifications on changes",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', 'epic', 'milestone', 'wiki', or 'project'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the resource to watch"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        client.watch(type, id)
        return SchemaBuilder.successText("Now watching $type #$id.")
    }
}

object UnwatchTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_unwatch",
        description = "Stop watching an item to stop receiving notifications",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', 'epic', 'milestone', 'wiki', or 'project'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the resource to stop watching"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        client.unwatch(type, id)
        return SchemaBuilder.successText("Stopped watching $type #$id.")
    }
}
