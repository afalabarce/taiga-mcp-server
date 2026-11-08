package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object ListWebNotificationsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_web_notifications",
        description = "List in-app web notifications for the current user",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "only_unread" to SchemaBuilder.booleanProp("Filter to only unread notifications"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val onlyUnread = args["only_unread"]?.jsonPrimitive?.booleanOrNull ?: false
        val notifications = client.listWebNotifications(onlyUnread)
        return SchemaBuilder.successJson(notifications)
    }
}

object MarkWebNotificationsReadTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_mark_web_notifications_read",
        description = "Mark a specific web notification or all notifications as read",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Optional numeric ID of notification to mark read (marks all as read if omitted)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull
        client.markWebNotificationsRead(id)
        return SchemaBuilder.successText(if (id != null) "Notification #$id marked as read." else "All notifications marked as read.")
    }
}

object ListNotifyPoliciesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_notify_policies",
        description = "List notification policies for the user in a project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val policies = client.listNotifyPolicies(projectId)
        return SchemaBuilder.successJson(policies)
    }
}

object UpdateNotifyPolicyTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_notify_policy",
        description = "Update notification level policy for a project (1: None, 2: Involved, 3: All)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the notify policy"),
                "notify_level" to SchemaBuilder.intProp("Notification level: 1 (None), 2 (Involved), 3 (All)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id", "notify_level")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'id'")
        val notifyLevel = args["notify_level"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("Missing 'notify_level'")

        val updated = client.updateNotifyPolicy(id, notifyLevel)
        return SchemaBuilder.successJson(updated)
    }
}
