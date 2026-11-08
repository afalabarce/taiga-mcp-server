package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object ListWebhooksTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_webhooks",
        description = "List all webhooks configured for a project",
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
        val webhooks = client.listWebhooks(projectId)
        return SchemaBuilder.successJson(webhooks)
    }
}

object GetWebhookTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_webhook",
        description = "Get detailed information about a webhook by numeric ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the webhook"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val webhook = client.getWebhook(id)
        return SchemaBuilder.successJson(webhook)
    }
}

object CreateWebhookTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_webhook",
        description = "Create a new webhook for project events",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "name" to SchemaBuilder.stringProp("Name/description of the webhook"),
                "url" to SchemaBuilder.stringProp("Payload delivery URL"),
                "key" to SchemaBuilder.stringProp("Optional secret key for webhook signing"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("name", "url")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val url = args["url"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'url'")
        val key = args["key"]?.jsonPrimitive?.contentOrNull
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val webhook = client.createWebhook(name, url, key, projectId)
        return SchemaBuilder.successJson(webhook)
    }
}

object UpdateWebhookTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_webhook",
        description = "Update an existing webhook configuration",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the webhook to update"),
                "name" to SchemaBuilder.stringProp("Updated name"),
                "url" to SchemaBuilder.stringProp("Updated payload URL"),
                "key" to SchemaBuilder.stringProp("Updated secret key"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val name = args["name"]?.jsonPrimitive?.contentOrNull
        val url = args["url"]?.jsonPrimitive?.contentOrNull
        val key = args["key"]?.jsonPrimitive?.contentOrNull

        val updated = client.updateWebhook(id, name, url, key)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteWebhookTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_webhook",
        description = "Delete a webhook by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the webhook to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteWebhook(id)
        return SchemaBuilder.successText("Webhook #$id deleted successfully.")
    }
}

object TestWebhookTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_test_webhook",
        description = "Trigger a test event delivery for a webhook",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the webhook to test"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val result = client.testWebhook(id)
        return SchemaBuilder.successJson(result)
    }
}

object ListWebhookLogsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_webhook_logs",
        description = "List delivery attempts and logs for a specific webhook",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "webhook_id" to SchemaBuilder.intProp("Numeric ID of the webhook"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("webhook_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val webhookId = args["webhook_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'webhook_id'")
        val logs = client.listWebhookLogs(webhookId)
        return SchemaBuilder.successJson(logs)
    }
}

object ResendWebhookTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_resend_webhook",
        description = "Resend a previously failed webhook delivery attempt by log ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "log_id" to SchemaBuilder.intProp("Numeric ID of the webhook log entry to resend"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("log_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val logId = args["log_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'log_id'")
        val result = client.resendWebhook(logId)
        return SchemaBuilder.successJson(result)
    }
}
