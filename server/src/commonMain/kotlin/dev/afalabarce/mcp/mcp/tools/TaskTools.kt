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
        description = "List tasks in a Taiga project, with optional filters for user story, milestone, status, or assignee",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "user_story_id" to SchemaBuilder.intProp("Filter tasks associated with a specific user story ID"),
                "milestone_id" to SchemaBuilder.intProp("Filter tasks associated with a milestone (sprint) ID"),
                "status_id" to SchemaBuilder.intProp("Filter tasks by task status ID"),
                "assigned_to" to SchemaBuilder.intProp("Filter tasks by assigned user ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val userStoryId = args["user_story_id"]?.jsonPrimitive?.longOrNull
        val milestoneId = args["milestone_id"]?.jsonPrimitive?.longOrNull
        val statusId = args["status_id"]?.jsonPrimitive?.longOrNull
        val assignedToId = args["assigned_to"]?.jsonPrimitive?.longOrNull

        val tasks = client.listTasks(projectId, userStoryId, milestoneId, statusId, assignedToId)
        return SchemaBuilder.successJson(tasks)
    }
}

object GetTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_task",
        description = "Get detailed information about a task by its ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the task"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val task = client.getTask(id)
        return SchemaBuilder.successJson(task)
    }
}

object CreateTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_task",
        description = "Create a new task in Taiga (optionally under a user story or sprint)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the task"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "user_story_id" to SchemaBuilder.intProp("Optional ID of the parent user story"),
                "milestone_id" to SchemaBuilder.intProp("Optional milestone / sprint ID"),
                "description" to SchemaBuilder.stringProp("Description of the task"),
                "status_id" to SchemaBuilder.intProp("Task status ID"),
                "assigned_to" to SchemaBuilder.intProp("User ID to assign this task to"),
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
            args["user_story_id"]?.jsonPrimitive?.longOrNull?.let { put("user_story", it) }
            args["milestone_id"]?.jsonPrimitive?.longOrNull?.let { put("milestone", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val task = client.createTask(payload)
        return SchemaBuilder.successJson(task)
    }
}

object UpdateTaskTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_task",
        description = "Update an existing task in Taiga",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the task to update"),
                "version" to SchemaBuilder.intProp("Current version of the task"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "status_id" to SchemaBuilder.intProp("Updated task status ID"),
                "assigned_to" to SchemaBuilder.intProp("Updated assigned user ID"),
                "user_story_id" to SchemaBuilder.intProp("Updated parent user story ID"),
                "tags" to SchemaBuilder.arrayProp("Updated tags list"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val current = if (args["version"] == null) client.getTask(id) else null
        val version = args["version"]?.jsonPrimitive?.intOrNull ?: current?.version ?: 1

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["user_story_id"]?.jsonPrimitive?.longOrNull?.let { put("user_story", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
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
