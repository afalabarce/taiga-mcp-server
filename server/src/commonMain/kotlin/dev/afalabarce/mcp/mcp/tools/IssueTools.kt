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
        description = "List issues in a Taiga project, with filters for status, priority, severity, or type (names or numeric IDs accepted)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "status" to SchemaBuilder.stringProp("Filter by issue status name (e.g. 'New', 'In progress', 'Closed') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: numeric status ID"),
                "priority" to SchemaBuilder.stringProp("Filter by priority name (e.g. 'Low', 'Normal', 'High') or numeric ID"),
                "priority_id" to SchemaBuilder.intProp("Legacy alias: numeric priority ID"),
                "severity" to SchemaBuilder.stringProp("Filter by severity name (e.g. 'Minor', 'Normal', 'Critical') or numeric ID"),
                "severity_id" to SchemaBuilder.intProp("Legacy alias: numeric severity ID"),
                "type" to SchemaBuilder.stringProp("Filter by type name (e.g. 'Bug', 'Question', 'Enhancement') or numeric ID"),
                "type_id" to SchemaBuilder.intProp("Legacy alias: numeric type ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveIssueStatus(client, projectId, it) }

        val priorityParam = args["priority"] ?: args["priority_id"]
        val priorityId = priorityParam?.let { TaigaResolver.resolvePriority(client, projectId, it) }

        val severityParam = args["severity"] ?: args["severity_id"]
        val severityId = severityParam?.let { TaigaResolver.resolveSeverity(client, projectId, it) }

        val typeParam = args["type"] ?: args["type_id"]
        val typeId = typeParam?.let { TaigaResolver.resolveIssueType(client, projectId, it) }

        val issues = client.listIssues(projectId, statusId, priorityId, severityId, typeId)
        return SchemaBuilder.successJson(issues)
    }
}

object GetIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_issue",
        description = "Get detailed information about an issue by numeric ID or reference number (ref)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the issue (or specify 'ref')"),
                "ref" to SchemaBuilder.intProp("The reference number (ref) of the issue in the project"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (required if using 'ref', defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val ref = args["ref"]?.jsonPrimitive?.longOrNull
        val id = args["id"]?.jsonPrimitive?.longOrNull
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull

        val issue = if (ref != null) {
            val projectId = client.resolveProjectId(projectParam)
            client.getIssueByRef(ref, projectId)
        } else if (id != null) {
            client.getIssue(id)
        } else {
            throw IllegalArgumentException("Must specify either 'id' (numeric ID) or 'ref' (reference number)")
        }
        return SchemaBuilder.successJson(issue)
    }
}

object CreateIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_issue",
        description = "Create a new issue/bug in a Taiga project. Supports type names ('Bug'), priority names ('High'), severity names ('Critical'), and status names.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the issue"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Detailed description of the issue (markdown supported)"),
                "type" to SchemaBuilder.stringProp("Issue type name (e.g. 'Bug', 'Question', 'Enhancement') or numeric ID"),
                "type_id" to SchemaBuilder.intProp("Legacy alias: numeric type ID"),
                "priority" to SchemaBuilder.stringProp("Priority name (e.g. 'Low', 'Normal', 'High') or numeric ID"),
                "priority_id" to SchemaBuilder.intProp("Legacy alias: numeric priority ID"),
                "severity" to SchemaBuilder.stringProp("Severity name (e.g. 'Wishlist', 'Minor', 'Normal', 'Major', 'Critical') or numeric ID"),
                "severity_id" to SchemaBuilder.intProp("Legacy alias: numeric severity ID"),
                "status" to SchemaBuilder.stringProp("Status name (e.g. 'New', 'In progress', 'Ready for test', 'Closed') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: numeric status ID"),
                "assigned_to" to SchemaBuilder.stringProp("User ID, username, or email to assign this issue to"),
                "tags" to SchemaBuilder.arrayProp("List of tags"),
                "due_date" to SchemaBuilder.stringProp("Optional due date (YYYY-MM-DD)"),
                "due_date_reason" to SchemaBuilder.stringProp("Optional reason for the due date"),
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

        val typeParam = args["type"] ?: args["type_id"]
        val typeId = typeParam?.let { TaigaResolver.resolveIssueType(client, projectId, it) }

        val priorityParam = args["priority"] ?: args["priority_id"]
        val priorityId = priorityParam?.let { TaigaResolver.resolvePriority(client, projectId, it) }

        val severityParam = args["severity"] ?: args["severity_id"]
        val severityId = severityParam?.let { TaigaResolver.resolveSeverity(client, projectId, it) }

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveIssueStatus(client, projectId, it) }

        val assignedToParam = args["assigned_to"]
        val assignedToId = assignedToParam?.let { TaigaResolver.resolveAssignee(client, projectId, it) }

        val payload = buildJsonObject {
            put("subject", subject)
            put("project", projectId)
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            typeId?.let { put("type", it) }
            priorityId?.let { put("priority", it) }
            severityId?.let { put("severity", it) }
            statusId?.let { put("status", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
            args["due_date"]?.jsonPrimitive?.contentOrNull?.let { put("due_date", it) }
            args["due_date_reason"]?.jsonPrimitive?.contentOrNull?.let { put("due_date_reason", it) }
        }

        val issue = client.createIssue(payload)
        return SchemaBuilder.successJson(issue)
    }
}

object UpdateIssueTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_issue",
        description = "Update an existing issue in Taiga. Version concurrency is automatically handled if omitted. Supports names for status, priority, severity, type, and comments.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the issue to update"),
                "version" to SchemaBuilder.intProp("Optional current version of the issue (auto-resolved if omitted)"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "type" to SchemaBuilder.stringProp("Updated type name or numeric ID"),
                "type_id" to SchemaBuilder.intProp("Legacy alias: updated type ID"),
                "priority" to SchemaBuilder.stringProp("Updated priority name or numeric ID"),
                "priority_id" to SchemaBuilder.intProp("Legacy alias: updated priority ID"),
                "severity" to SchemaBuilder.stringProp("Updated severity name or numeric ID"),
                "severity_id" to SchemaBuilder.intProp("Legacy alias: updated severity ID"),
                "status" to SchemaBuilder.stringProp("Updated status name or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: updated status ID"),
                "assigned_to" to SchemaBuilder.stringProp("Updated assigned user ID, username, or email"),
                "tags" to SchemaBuilder.arrayProp("Updated tags list"),
                "due_date" to SchemaBuilder.stringProp("Optional updated due date (YYYY-MM-DD or empty to clear)"),
                "due_date_reason" to SchemaBuilder.stringProp("Optional updated reason for the due date"),
                "comment" to SchemaBuilder.stringProp("Optional comment or note to record in the issue's activity history"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val current = if (args["version"] == null || args["status"] != null || args["priority"] != null || args["severity"] != null || args["type"] != null || args["assigned_to"] != null) {
            client.getIssue(id)
        } else null

        val version = args["version"]?.jsonPrimitive?.intOrNull ?: current?.version ?: 1
        val projectId = current?.project ?: client.resolveProjectId(args["project"]?.jsonPrimitive?.contentOrNull)

        val typeParam = args["type"] ?: args["type_id"]
        val typeId = typeParam?.let { TaigaResolver.resolveIssueType(client, projectId, it) }

        val priorityParam = args["priority"] ?: args["priority_id"]
        val priorityId = priorityParam?.let { TaigaResolver.resolvePriority(client, projectId, it) }

        val severityParam = args["severity"] ?: args["severity_id"]
        val severityId = severityParam?.let { TaigaResolver.resolveSeverity(client, projectId, it) }

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveIssueStatus(client, projectId, it) }

        val assignedToParam = args["assigned_to"]
        val assignedToId = assignedToParam?.let { TaigaResolver.resolveAssignee(client, projectId, it) }

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            typeId?.let { put("type", it) }
            priorityId?.let { put("priority", it) }
            severityId?.let { put("severity", it) }
            statusId?.let { put("status", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
            args["due_date"]?.jsonPrimitive?.contentOrNull?.let { put("due_date", it) }
            args["due_date_reason"]?.jsonPrimitive?.contentOrNull?.let { put("due_date_reason", it) }
            args["comment"]?.jsonPrimitive?.contentOrNull?.let { put("comment", it) }
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

object PromoteIssueToUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_promote_issue_to_user_story",
        description = "Promote an existing issue/bug into a user story",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the issue to promote"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val story = client.promoteIssueToUserStory(id, projectId)
        return SchemaBuilder.successJson(story)
    }
}
