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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object ListEpicsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_epics",
        description = "List all epics for a Taiga project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "status_id" to SchemaBuilder.intProp("Optional filter by status ID"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val statusId = args["status_id"]?.jsonPrimitive?.longOrNull
        val epics = client.listEpics(projectId, statusId)
        return SchemaBuilder.successJson(epics)
    }
}

object GetEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_epic",
        description = "Get an epic by its ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the epic"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val epic = client.getEpic(id)
        return SchemaBuilder.successJson(epic)
    }
}

object CreateEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_epic",
        description = "Create a new epic in a Taiga project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "subject" to SchemaBuilder.stringProp("Subject or title of the epic"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Detailed description of the epic (markdown supported)"),
                "color" to SchemaBuilder.stringProp("Hex color code (e.g. '#A8E6CF')"),
                "status_id" to SchemaBuilder.intProp("Status ID"),
                "assigned_to" to SchemaBuilder.intProp("User ID to assign this epic to"),
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
            args["color"]?.jsonPrimitive?.contentOrNull?.let { put("color", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
        }

        val epic = client.createEpic(payload)
        return SchemaBuilder.successJson(epic)
    }
}

object UpdateEpicTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_epic",
        description = "Update an existing epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The ID of the epic to update"),
                "version" to SchemaBuilder.intProp("Current version of the epic (concurrency check)"),
                "subject" to SchemaBuilder.stringProp("Updated subject"),
                "description" to SchemaBuilder.stringProp("Updated description"),
                "color" to SchemaBuilder.stringProp("Updated color hex code"),
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
        val currentEpic = if (args["version"] == null) client.getEpic(id) else null
        val version = args["version"]?.jsonPrimitive?.intOrNull ?: currentEpic?.version ?: 1

        val payload = buildJsonObject {
            put("version", version)
            args["subject"]?.jsonPrimitive?.contentOrNull?.let { put("subject", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["color"]?.jsonPrimitive?.contentOrNull?.let { put("color", it) }
            args["status_id"]?.jsonPrimitive?.longOrNull?.let { put("status", it) }
            args["assigned_to"]?.jsonPrimitive?.longOrNull?.let { put("assigned_to", it) }
            args["tags"]?.let { if (it is JsonArray) put("tags", it) }
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
