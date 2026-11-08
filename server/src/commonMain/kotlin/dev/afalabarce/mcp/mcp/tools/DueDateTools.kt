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

object ListDueDatesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_due_dates",
        description = "List configured due date status colors for user stories, tasks, or issues",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', or 'issue'"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val dueDates = client.listDueDates(type, projectId)
        return SchemaBuilder.successJson(dueDates)
    }
}

object GetDueDateTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_due_date",
        description = "Get detailed information about a due date status color by numeric ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', or 'issue'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the due date config"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val dueDate = client.getDueDate(type, id)
        return SchemaBuilder.successJson(dueDate)
    }
}

object CreateDueDateTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_due_date",
        description = "Create a new due date status color configuration for stories, tasks, or issues",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', or 'issue'"),
                "name" to SchemaBuilder.stringProp("Name of the due date state (e.g. 'Due soon', 'Past due')"),
                "color" to SchemaBuilder.stringProp("Hex color string (e.g. '#e74c3c')"),
                "days_to_due" to SchemaBuilder.intProp("Days offset relative to due date (negative for days before, positive for after)"),
                "by_default" to SchemaBuilder.booleanProp("Whether this is the default state"),
                "order" to SchemaBuilder.intProp("Display order"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "name", "color")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val color = args["color"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'color'")
        val daysToDue = args["days_to_due"]?.jsonPrimitive?.intOrNull
        val byDefault = args["by_default"]?.jsonPrimitive?.booleanOrNull ?: false
        val order = args["order"]?.jsonPrimitive?.intOrNull
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val dueDate = client.createDueDate(type, name, color, daysToDue, byDefault, order, projectId)
        return SchemaBuilder.successJson(dueDate)
    }
}

object UpdateDueDateTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_due_date",
        description = "Update an existing due date status color configuration",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', or 'issue'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the due date configuration to update"),
                "name" to SchemaBuilder.stringProp("Updated name"),
                "color" to SchemaBuilder.stringProp("Updated hex color (e.g. '#e74c3c')"),
                "days_to_due" to SchemaBuilder.intProp("Updated days to due"),
                "by_default" to SchemaBuilder.booleanProp("Updated default flag"),
                "order" to SchemaBuilder.intProp("Updated display order"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val name = args["name"]?.jsonPrimitive?.contentOrNull
        val color = args["color"]?.jsonPrimitive?.contentOrNull
        val daysToDue = args["days_to_due"]?.jsonPrimitive?.intOrNull
        val byDefault = args["by_default"]?.jsonPrimitive?.booleanOrNull
        val order = args["order"]?.jsonPrimitive?.intOrNull

        val updated = client.updateDueDate(type, id, name, color, daysToDue, byDefault, order)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteDueDateTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_due_date",
        description = "Delete a due date status configuration by ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', or 'issue'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the due date config to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        client.deleteDueDate(type, id)
        return SchemaBuilder.successText("Due date configuration #$id for $type deleted successfully.")
    }
}
