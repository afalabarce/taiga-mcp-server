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

object ListUserStoriesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_user_stories",
        description = "List user stories for a project, optionally filtered by milestone (sprint ID, slug, or name), status (ID or name like 'In progress'), or epic. Project defaults to configured project.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "status" to SchemaBuilder.stringProp("Filter by story status name (e.g. 'In progress', 'Ready for test') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: filter by numeric story status ID"),
                "milestone" to SchemaBuilder.stringProp("Filter by milestone/sprint name, slug, or numeric ID"),
                "milestone_id" to SchemaBuilder.intProp("Legacy alias: filter by numeric milestone ID"),
                "epic_id" to SchemaBuilder.intProp("Filter by parent epic ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = TaigaResolver.resolveUserStoryStatus(client, projectId, statusParam)

        val milestoneParam = args["milestone"] ?: args["milestone_id"]
        val milestoneId = TaigaResolver.resolveMilestone(client, projectId, milestoneParam)

        val epicId = args["epic_id"]?.jsonPrimitive?.longOrNull

        val stories = client.listUserStories(projectId, milestoneId, statusId, epicId)
        return SchemaBuilder.successJson(stories)
    }
}

object GetUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user_story",
        description = "Get detailed information about a user story by numeric ID or reference number (ref)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the user story (or specify 'ref')"),
                "ref" to SchemaBuilder.intProp("The reference number (ref) of the user story in the project"),
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

        val story = if (ref != null) {
            val projectId = client.resolveProjectId(projectParam)
            client.getUserStoryByRef(ref, projectId)
        } else if (id != null) {
            client.getUserStory(id)
        } else {
            throw IllegalArgumentException("Must specify either 'id' (numeric ID) or 'ref' (reference number)")
        }
        return SchemaBuilder.successJson(story)
    }
}

object CreateUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_user_story",
        description = "Create a new user story in a Taiga project. Supports human-readable status names (e.g. 'In progress'), sprint names, and usernames for assignment.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the user story"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Detailed description of user story (markdown supported)"),
                "status" to SchemaBuilder.stringProp("Status name (e.g. 'New', 'In progress', 'Ready for test') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: numeric status ID"),
                "milestone" to SchemaBuilder.stringProp("Milestone / Sprint name, slug, or numeric ID to assign story to"),
                "milestone_id" to SchemaBuilder.intProp("Legacy alias: numeric milestone ID"),
                "assigned_to" to SchemaBuilder.stringProp("User ID, username, or email to assign this story to"),
                "tags" to SchemaBuilder.arrayProp("List of tags (e.g. ['frontend', 'auth'])"),
                "points" to SchemaBuilder.objectProp("Map of role IDs to point IDs for story points (e.g. {\"1\": 2})"),
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

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = TaigaResolver.resolveUserStoryStatus(client, projectId, statusParam)

        val milestoneParam = args["milestone"] ?: args["milestone_id"]
        val milestoneId = TaigaResolver.resolveMilestone(client, projectId, milestoneParam)

        val assignedToParam = args["assigned_to"]
        val assignedToId = TaigaResolver.resolveAssignee(client, projectId, assignedToParam)

        val payload = buildJsonObject {
            put("subject", subject)
            put("project", projectId)
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            statusId?.let { put("status", it) }
            milestoneId?.let { put("milestone", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
            args["points"]?.let { if (it is JsonObject) put("points", it) }
            args["due_date"]?.jsonPrimitive?.contentOrNull?.let { put("due_date", it) }
            args["due_date_reason"]?.jsonPrimitive?.contentOrNull?.let { put("due_date_reason", it) }
        }

        val story = client.createUserStory(payload)
        return SchemaBuilder.successJson(story)
    }
}

object UpdateUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_user_story",
        description = "Update an existing user story. Version concurrency is automatically handled if omitted. Supports status names, sprint names, assignee usernames, and adding comments.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the user story to update"),
                "version" to SchemaBuilder.intProp("Optional current version (auto-resolved if omitted)"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "status" to SchemaBuilder.stringProp("Updated status name (e.g. 'In progress', 'Closed') or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: updated status ID"),
                "milestone" to SchemaBuilder.stringProp("Updated milestone/sprint name, slug, or numeric ID (or 'null' to remove)"),
                "milestone_id" to SchemaBuilder.intProp("Legacy alias: updated milestone ID"),
                "assigned_to" to SchemaBuilder.stringProp("Updated assigned user ID, username, or email"),
                "tags" to SchemaBuilder.arrayProp("Updated tags list"),
                "points" to SchemaBuilder.objectProp("Updated map of role IDs to point IDs for story points"),
                "due_date" to SchemaBuilder.stringProp("Optional updated due date (YYYY-MM-DD or empty to clear)"),
                "due_date_reason" to SchemaBuilder.stringProp("Optional updated reason for the due date"),
                "comment" to SchemaBuilder.stringProp("Optional comment or note to record in the story's activity history"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val current = if (args["version"] == null || args["status"] != null || args["milestone"] != null || args["assigned_to"] != null) {
            client.getUserStory(id)
        } else null

        val version = args["version"]?.jsonPrimitive?.intOrNull ?: current?.version ?: 1
        val projectId = current?.project ?: client.resolveProjectId(args["project"]?.jsonPrimitive?.contentOrNull)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveUserStoryStatus(client, projectId, it) }

        val milestoneParam = args["milestone"] ?: args["milestone_id"]
        val milestoneId = milestoneParam?.let { TaigaResolver.resolveMilestone(client, projectId, it) }

        val assignedToParam = args["assigned_to"]
        val assignedToId = assignedToParam?.let { TaigaResolver.resolveAssignee(client, projectId, it) }

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            statusId?.let { put("status", it) }
            milestoneId?.let { put("milestone", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
            args["points"]?.let { if (it is JsonObject) put("points", it) }
            args["due_date"]?.jsonPrimitive?.contentOrNull?.let { put("due_date", it) }
            args["due_date_reason"]?.jsonPrimitive?.contentOrNull?.let { put("due_date_reason", it) }
            args["comment"]?.jsonPrimitive?.contentOrNull?.let { put("comment", it) }
        }

        val updated = client.updateUserStory(id, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_user_story",
        description = "Delete a user story by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the user story to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteUserStory(id)
        return SchemaBuilder.successText("User Story #$id successfully deleted.")
    }
}
