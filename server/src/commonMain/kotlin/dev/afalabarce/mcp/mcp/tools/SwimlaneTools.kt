package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object ListSwimlanesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_swimlanes",
        description = "List all swimlanes for a project (defaults to configured project)",
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
        val swimlanes = client.listSwimlanes(projectId)
        return SchemaBuilder.successJson(swimlanes)
    }
}

object GetSwimlaneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_swimlane",
        description = "Get detailed information about a swimlane by numeric ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the swimlane"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val swimlane = client.getSwimlane(id)
        return SchemaBuilder.successJson(swimlane)
    }
}

object CreateSwimlaneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_swimlane",
        description = "Create a new swimlane in a project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "name" to SchemaBuilder.stringProp("Name of the swimlane"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "order" to SchemaBuilder.intProp("Display order of the swimlane"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("name")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val order = args["order"]?.jsonPrimitive?.longOrNull

        val swimlane = client.createSwimlane(name, projectId, order)
        return SchemaBuilder.successJson(swimlane)
    }
}

object UpdateSwimlaneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_swimlane",
        description = "Update an existing swimlane's name or display order",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the swimlane to update"),
                "name" to SchemaBuilder.stringProp("Updated name"),
                "order" to SchemaBuilder.intProp("Updated order"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val name = args["name"]?.jsonPrimitive?.contentOrNull
        val order = args["order"]?.jsonPrimitive?.longOrNull

        val swimlane = client.updateSwimlane(id, name, order)
        return SchemaBuilder.successJson(swimlane)
    }
}

object DeleteSwimlaneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_swimlane",
        description = "Delete a swimlane by numeric ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the swimlane to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteSwimlane(id)
        return SchemaBuilder.successText("Swimlane #$id deleted successfully.")
    }
}

object ReorderSwimlanesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_reorder_swimlanes",
        description = "Bulk update display order of swimlanes in a project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_swimlanes" to SchemaBuilder.arrayProp("Array of objects with 'swimlane_id' and 'order'"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_swimlanes")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val bulkSwimlanes = args["bulk_swimlanes"]?.jsonArray ?: throw IllegalArgumentException("Missing 'bulk_swimlanes'")

        client.reorderSwimlanes(bulkSwimlanes, projectId)
        return SchemaBuilder.successText("Swimlanes reordered successfully.")
    }
}

object ListSwimlaneUserStoryStatusesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_swimlane_userstory_statuses",
        description = "List swimlane user story statuses mapping for a project",
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
        val result = client.listSwimlaneUserStoryStatuses(projectId)
        return SchemaBuilder.successJson(result)
    }
}
