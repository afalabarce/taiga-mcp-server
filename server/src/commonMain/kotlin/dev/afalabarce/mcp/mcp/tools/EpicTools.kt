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

object ListEpicsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_epics",
        description = "List epics for a project, optionally filtered by status name or ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "status" to SchemaBuilder.stringProp("Filter epics by status name or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: filter epics by status ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveEpicStatus(client, projectId, it) }

        val epics = client.listEpics(projectId, statusId)
        return SchemaBuilder.successJson(epics)
    }
}

object GetEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_epic",
        description = "Get detailed information about an epic by ID or reference number (ref)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the epic (or specify 'ref')"),
                "ref" to SchemaBuilder.intProp("The reference number (ref) of the epic in the project"),
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

        val epic = if (ref != null) {
            val projectId = client.resolveProjectId(projectParam)
            client.getEpicByRef(ref, projectId)
        } else if (id != null) {
            client.getEpic(id)
        } else {
            throw IllegalArgumentException("Must specify either 'id' (numeric ID) or 'ref' (reference number)")
        }
        return SchemaBuilder.successJson(epic)
    }
}

object CreateEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_epic",
        description = "Create a new epic in a Taiga project. Supports status names and assignee usernames.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the epic"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Detailed description of the epic (markdown supported)"),
                "color" to SchemaBuilder.stringProp("Hex color code for the epic (e.g. '#A8E6CF')"),
                "status" to SchemaBuilder.stringProp("Status name or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: numeric status ID"),
                "assigned_to" to SchemaBuilder.stringProp("User ID, username, or email to assign this epic to"),
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

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveEpicStatus(client, projectId, it) }

        val assignedToParam = args["assigned_to"]
        val assignedToId = assignedToParam?.let { TaigaResolver.resolveAssignee(client, projectId, it) }

        val payload = buildJsonObject {
            put("subject", subject)
            put("project", projectId)
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["color"]?.jsonPrimitive?.contentOrNull?.let { put("color", it) }
            statusId?.let { put("status", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val epic = client.createEpic(payload)
        return SchemaBuilder.successJson(epic)
    }
}

object UpdateEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_epic",
        description = "Update an existing epic. Version concurrency is automatically handled if omitted. Supports status names, assignee usernames, and comments.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The ID of the epic to update"),
                "version" to SchemaBuilder.intProp("Optional current version of the epic (auto-resolved if omitted)"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "color" to SchemaBuilder.stringProp("Updated color hex code"),
                "status" to SchemaBuilder.stringProp("Updated status name or numeric ID"),
                "status_id" to SchemaBuilder.intProp("Legacy alias: updated status ID"),
                "assigned_to" to SchemaBuilder.stringProp("Updated assigned user ID, username, or email"),
                "tags" to SchemaBuilder.arrayProp("Updated tags list"),
                "comment" to SchemaBuilder.stringProp("Optional comment to record in the epic's activity history"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val currentEpic = if (args["version"] == null || args["status"] != null || args["assigned_to"] != null) {
            client.getEpic(id)
        } else null

        val version = args["version"]?.jsonPrimitive?.intOrNull ?: currentEpic?.version ?: 1
        val projectId = currentEpic?.project ?: client.resolveProjectId(args["project"]?.jsonPrimitive?.contentOrNull)

        val statusParam = args["status"] ?: args["status_id"]
        val statusId = statusParam?.let { TaigaResolver.resolveEpicStatus(client, projectId, it) }

        val assignedToParam = args["assigned_to"]
        val assignedToId = assignedToParam?.let { TaigaResolver.resolveAssignee(client, projectId, it) }

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["color"]?.jsonPrimitive?.contentOrNull?.let { put("color", it) }
            statusId?.let { put("status", it) }
            assignedToId?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
            args["comment"]?.jsonPrimitive?.contentOrNull?.let { put("comment", it) }
        }

        val updated = client.updateEpic(id, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_epic",
        description = "Delete an epic by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the epic to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteEpic(id)
        return SchemaBuilder.successText("Epic #$id successfully deleted.")
    }
}

object ListEpicUserStoriesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_epic_user_stories",
        description = "List all user stories linked/related to a specific epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "epic_id" to SchemaBuilder.intProp("The numeric ID of the epic"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("epic_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val epicId = args["epic_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'epic_id'")
        val stories = client.listEpicRelatedUserStories(epicId)
        return SchemaBuilder.successJson(stories)
    }
}

object LinkUserStoryToEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_link_user_story_to_epic",
        description = "Link an existing user story to an epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "epic_id" to SchemaBuilder.intProp("The numeric ID of the epic"),
                "user_story_id" to SchemaBuilder.intProp("The numeric ID of the user story to link"),
                "order" to SchemaBuilder.intProp("Optional display order of the story within the epic"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("epic_id", "user_story_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val epicId = args["epic_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'epic_id'")
        val userStoryId = args["user_story_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'user_story_id'")
        val order = args["order"]?.jsonPrimitive?.longOrNull

        val relation = client.linkUserStoryToEpic(epicId, userStoryId, order)
        return SchemaBuilder.successJson(relation)
    }
}

object UnlinkUserStoryFromEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_unlink_user_story_from_epic",
        description = "Unlink a user story from an epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "epic_id" to SchemaBuilder.intProp("The numeric ID of the epic"),
                "user_story_id" to SchemaBuilder.intProp("The numeric ID of the user story to remove from the epic"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("epic_id", "user_story_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val epicId = args["epic_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'epic_id'")
        val userStoryId = args["user_story_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'user_story_id'")

        client.unlinkUserStoryFromEpic(epicId, userStoryId)
        return SchemaBuilder.successText("User story #$userStoryId unlinked from epic #$epicId successfully.")
    }
}
