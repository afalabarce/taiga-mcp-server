package io.taiga.mcp.mcp.tools

import io.taiga.mcp.config.ConfigManager
import io.taiga.mcp.mcp.protocol.McpCallToolResult
import io.taiga.mcp.mcp.protocol.McpToolDefinition
import io.taiga.mcp.taiga.client.TaigaClient
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
        description = "List user stories for a project, optionally filtered by milestone (sprint), status, or epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "milestone_id" to SchemaBuilder.intProp("Filter by milestone (sprint) ID"),
                "status_id" to SchemaBuilder.intProp("Filter by story status ID"),
                "epic_id" to SchemaBuilder.intProp("Filter by parent epic ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val milestoneId = args["milestone_id"]?.jsonPrimitive?.longOrNull
        val statusId = args["status_id"]?.jsonPrimitive?.longOrNull
        val epicId = args["epic_id"]?.jsonPrimitive?.longOrNull

        val stories = client.listUserStories(projectId, milestoneId, statusId, epicId)
        return SchemaBuilder.successJson(stories)
    }
}

object GetUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_user_story",
        description = "Get detailed information about a user story by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the user story"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val story = client.getUserStory(id)
        return SchemaBuilder.successJson(story)
    }
}

object CreateUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_user_story",
        description = "Create a new user story in a Taiga project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the user story"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Detailed description of user story (markdown supported)"),
                "status_id" to SchemaBuilder.intProp("Status ID"),
                "milestone_id" to SchemaBuilder.intProp("Milestone / Sprint ID to assign story to"),
                "assigned_to" to SchemaBuilder.intProp("User ID to assign this story to"),
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
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["milestone_id"]?.jsonPrimitive?.longOrNull?.let { put("milestone", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val story = client.createUserStory(payload)
        return SchemaBuilder.successJson(story)
    }
}

object UpdateUserStoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_user_story",
        description = "Update an existing user story",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the user story to update"),
                "version" to SchemaBuilder.intProp("Current version of the user story (concurrency check)"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "status_id" to SchemaBuilder.intProp("Updated status ID"),
                "milestone_id" to SchemaBuilder.intProp("Updated milestone ID (or null/0 to remove from sprint)"),
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
        val current = if (args["version"] == null) client.getUserStory(id) else null
        val version = args["version"]?.jsonPrimitive?.intOrNull ?: current?.version ?: 1

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["milestone_id"]?.jsonPrimitive?.longOrNull?.let { put("milestone", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
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
