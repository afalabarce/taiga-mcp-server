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

object ListTasksTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_tasks",
        description = "List tasks in a Taiga project, with optional filters for user story, milestone/sprint (name, slug, or ID), status (name or ID), or assignee",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "user_story_id" to SchemaBuilder.intProp("Filter tasks associated with a specific user story ID"),
                "milestone" to SchemaBuilder.stringProp("Filter tasks by milestone / sprint name, slug, or ID"),
                "milestone_id" to SchemaBuilder.intProp("Legacy alias: filter by numeric milestone ID"),
                "status" to SchemaBuilder.stringProp("Filter tasks by task status name (e.g. 'In progress', 'Ready for test', 'Closed') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: filter by numeric status ID"),
                "assigned_to" to SchemaBuilder.stringProp("Filter tasks by assigned user ID, username, or email"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val userStoryId = args["user_story_id"]?.jsonPrimitive?.longOrNull

        val milestoneParam = args["milestone"] ?: args["milestone_id"]
        val milestoneId = TaigaResolver.resolveMilestone(client, projectId, milestoneParam)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = TaigaResolver.resolveTaskStatus(client, projectId, statusParam)

        val assignedToParam = args["assigned_to"]
        val assignedToId = TaigaResolver.resolveAssignee(client, projectId, assignedToParam)

        val tasks = client.listTasks(projectId, userStoryId, milestoneId, statusId, assignedToId)
        return SchemaBuilder.successJson(tasks)
    }
}

object GetTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_task",
        description = "Get detailed information about a task by numeric ID or reference number (ref)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the task (or specify 'ref')"),
                "ref" to SchemaBuilder.intProp("The reference number (ref) of the task in the project"),
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

        val task = if (ref != null) {
            val projectId = client.resolveProjectId(projectParam)
            client.getTaskByRef(ref, projectId)
        } else if (id != null) {
            client.getTask(id)
        } else {
            throw IllegalArgumentException("Must specify either 'id' (numeric ID) or 'ref' (reference number)")
        }
        return SchemaBuilder.successJson(task)
    }
}

object CreateTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_task",
        description = "Create a new task in Taiga (optionally under a user story or sprint). Supports status names, sprint names, and assignee usernames.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the task"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "user_story_id" to SchemaBuilder.intProp("Optional ID of the parent user story"),
                "milestone" to SchemaBuilder.stringProp("Optional milestone / sprint name, slug, or numeric ID"),
                "milestone_id" to SchemaBuilder.intProp("Legacy alias: numeric milestone ID"),
                "description" to SchemaBuilder.stringProp("Description of the task (markdown supported)"),
                "status" to SchemaBuilder.stringProp("Task status name (e.g. 'New', 'In progress', 'Ready for test', 'Closed') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: numeric task status ID"),
                "assigned_to" to SchemaBuilder.stringProp("User ID, username, or email to assign this task to"),
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

        val milestoneParam = args["milestone"] ?: args["milestone_id"]
        val milestoneId = TaigaResolver.resolveMilestone(client, projectId, milestoneParam)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = TaigaResolver.resolveTaskStatus(client, projectId, statusParam)

        val assignedToParam = args["assigned_to"]
        val assignedToId = TaigaResolver.resolveAssignee(client, projectId, assignedToParam)

        val payload = buildJsonObject {
            put("subject", subject)
            put("project", projectId)
            args["user_story_id"]?.jsonPrimitive?.longOrNull?.let { put("user_story", it) }
            milestoneId?.let { put("milestone", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            statusId?.let { put("status", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val task = client.createTask(payload)
        return SchemaBuilder.successJson(task)
    }
}

object UpdateTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_task",
        description = "Update an existing task in Taiga. Version concurrency is automatically handled if omitted. Supports status names, sprint names, assignee usernames, and comments.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the task to update"),
                "version" to SchemaBuilder.intProp("Optional current version of the task (auto-resolved if omitted)"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "status" to SchemaBuilder.stringProp("Updated task status name (e.g. 'In progress', 'Closed') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: numeric task status ID"),
                "assigned_to" to SchemaBuilder.stringProp("Updated assigned user ID, username, or email"),
                "user_story_id" to SchemaBuilder.intProp("Updated parent user story ID"),
                "tags" to SchemaBuilder.arrayProp("Updated tags list"),
                "comment" to SchemaBuilder.stringProp("Optional comment or note to record in task's activity history"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val current = if (args["version"] == null || args["status"] != null || args["assigned_to"] != null) {
            client.getTask(id)
        } else null

        val version = args["version"]?.jsonPrimitive?.intOrNull ?: current?.version ?: 1
        val projectId = current?.project ?: client.resolveProjectId(args["project"]?.jsonPrimitive?.contentOrNull)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveTaskStatus(client, projectId, it) }

        val assignedToParam = args["assigned_to"]
        val assignedToId = assignedToParam?.let { TaigaResolver.resolveAssignee(client, projectId, it) }

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            statusId?.let { put("status", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["user_story_id"]?.jsonPrimitive?.longOrNull?.let { put("user_story", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
            args["comment"]?.jsonPrimitive?.contentOrNull?.let { put("comment", it) }
        }

        val updated = client.updateTask(id, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_task",
        description = "Delete a task by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the task to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteTask(id)
        return SchemaBuilder.successText("Task #$id successfully deleted.")
    }
}
