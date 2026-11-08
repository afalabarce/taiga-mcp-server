package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object EditCommentTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_edit_comment",
        description = "Edit an existing comment in an item's activity history",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Resource type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the resource item"),
                "comment_id" to SchemaBuilder.stringProp("UUID of the comment to edit"),
                "comment" to SchemaBuilder.stringProp("Updated comment text"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id", "comment_id", "comment")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'id'")
        val commentId = args["comment_id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'comment_id'")
        val comment = args["comment"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'comment'")

        val result = client.editComment(type, id, commentId, comment)
        return SchemaBuilder.successJson(result)
    }
}

object DeleteCommentTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_comment",
        description = "Delete a comment from an item's activity history",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Resource type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the resource item"),
                "comment_id" to SchemaBuilder.stringProp("UUID of the comment to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id", "comment_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'id'")
        val commentId = args["comment_id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'comment_id'")

        val result = client.deleteComment(type, id, commentId)
        return SchemaBuilder.successJson(result)
    }
}

object UndeleteCommentTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_undelete_comment",
        description = "Restore a previously deleted comment in an item's activity history",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Resource type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the resource item"),
                "comment_id" to SchemaBuilder.stringProp("UUID of the comment to restore"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id", "comment_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'id'")
        val commentId = args["comment_id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'comment_id'")

        val result = client.undeleteComment(type, id, commentId)
        return SchemaBuilder.successJson(result)
    }
}

object GetCommentVersionsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_comment_versions",
        description = "Get edit history and versions of a comment",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Resource type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the resource item"),
                "comment_id" to SchemaBuilder.stringProp("UUID of the comment"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id", "comment_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'id'")
        val commentId = args["comment_id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'comment_id'")

        val versions = client.getCommentVersions(type, id, commentId)
        return SchemaBuilder.successJson(versions)
    }
}
