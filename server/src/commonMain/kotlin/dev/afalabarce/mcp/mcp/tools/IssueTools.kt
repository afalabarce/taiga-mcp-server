package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object ListIssuesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_issues",
        description = "List issues in a Taiga project, with filters for status, priority, severity, or type",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "status_id" to SchemaBuilder.intProp("Filter by issue status ID"),
                "priority_id" to SchemaBuilder.intProp("Filter by priority ID"),
                "severity_id" to SchemaBuilder.intProp("Filter by severity ID"),
                "type_id" to SchemaBuilder.intProp("Filter by type ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val statusId = args["status_id"]?.jsonPrimitive?.longOrNull
        val priorityId = args["priority_id"]?.jsonPrimitive?.longOrNull
        val severityId = args["severity_id"]?.jsonPrimitive?.longOrNull
        val typeId = args["type_id"]?.jsonPrimitive?.longOrNull

        val issues = client.listIssues(projectId, statusId, priorityId, severityId, typeId)
        return SchemaBuilder.successJson(issues)
    }
}

object GetIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_issue",
        description = "Get detailed information about an issue by its ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the issue"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val issue = client.getIssue(id)
        return SchemaBuilder.successJson(issue)
    }
}

object CreateIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_issue",
        description = "Create a new issue/bug in a Taiga project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the issue"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Detailed description of the issue"),
                "type_id" to SchemaBuilder.intProp("Issue type ID (Bug, Question, Enhancement, etc.)"),
                "priority_id" to SchemaBuilder.intProp("Priority ID (Low, Normal, High)"),
                "severity_id" to SchemaBuilder.intProp("Severity ID (Wishlist, Minor, Major, Critical)"),
                "status_id" to SchemaBuilder.intProp("Status ID (New, In progress, Ready for test, Closed)"),
                "assigned_to" to SchemaBuilder.intProp("User ID to assign this issue to"),
                "tags" to SchemaBuilder.arrayProp("List of tags"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("subject")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val subject = args["subject"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'subject'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val payload = buildJsonObject {
            put("subject", subject)
            put("project", projectId)
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["type_id"]?.jsonPrimitive?.longOrNull?.let { put("type", it) }
            args["priority_id"]?.jsonPrimitive?.longOrNull?.let { put("priority", it) }
            args["severity_id"]?.jsonPrimitive?.longOrNull?.let { put("severity", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val issue = client.createIssue(payload)
        return SchemaBuilder.successJson(issue)
    }
}

object UpdateIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_issue",
        description = "Update an existing issue in Taiga",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the issue to update"),
                "version" to SchemaBuilder.intProp("Current version of the issue"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "type_id" to SchemaBuilder.intProp("Updated type ID"),
                "priority_id" to SchemaBuilder.intProp("Updated priority ID"),
                "severity_id" to SchemaBuilder.intProp("Updated severity ID"),
                "status_id" to SchemaBuilder.intProp("Updated status ID"),
                "assigned_to" to SchemaBuilder.intProp("Updated assigned user ID"),
                "tags" to SchemaBuilder.arrayProp("Updated tags list"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val current = if (args["version"] == null) client.getIssue(id) else null
        val version = args["version"]?.jsonPrimitive?.intOrNull ?: current?.version ?: 1

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["type_id"]?.jsonPrimitive?.longOrNull?.let { put("type", it) }
            args["priority_id"]?.jsonPrimitive?.longOrNull?.let { put("priority", it) }
            args["severity_id"]?.jsonPrimitive?.longOrNull?.let { put("severity", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val updated = client.updateIssue(id, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_issue",
        description = "Delete an issue by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the issue to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteIssue(id)
        return SchemaBuilder.successText("Issue #$id successfully deleted.")
    }
}
