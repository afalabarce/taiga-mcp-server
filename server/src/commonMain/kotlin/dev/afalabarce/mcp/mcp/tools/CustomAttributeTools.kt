package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object ListCustomAttributesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_custom_attributes",
        description = "List custom attribute definitions available for user stories, tasks, issues, or epics in a project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', or 'epic'"),
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

        val attrs = client.listCustomAttributes(type, projectId)
        return SchemaBuilder.successJson(attrs)
    }
}

object CreateCustomAttributeTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_custom_attribute",
        description = "Define a new custom attribute field on a project for user stories, tasks, issues, or epics",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', or 'epic'"),
                "name" to SchemaBuilder.stringProp("Name of the custom attribute (e.g. 'Release Notes', 'Reviewer')"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "description" to SchemaBuilder.stringProp("Optional description or guideline for this attribute"),
                "attribute_type" to SchemaBuilder.stringProp("Type: 'text', 'multiline', 'date', 'url', 'dropdown', 'checkbox', 'number' (default: text)"),
                "order" to SchemaBuilder.intProp("Display order in UI"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "name")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val description = args["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val attrType = args["attribute_type"]?.jsonPrimitive?.contentOrNull ?: "text"
        val order = args["order"]?.jsonPrimitive?.intOrNull ?: 100

        val payload = buildJsonObject {
            put("project", projectId)
            put("name", name)
            put("description", description)
            put("type", attrType)
            put("order", order)
        }

        val result = client.createCustomAttribute(type, payload)
        return SchemaBuilder.successJson(result)
    }
}

object DeleteCustomAttributeTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_custom_attribute",
        description = "Delete a custom attribute definition from a project by its ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target type: 'userstory', 'task', 'issue', or 'epic'"),
                "id" to SchemaBuilder.intProp("The numeric ID of the custom attribute definition"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        client.deleteCustomAttribute(type, id)
        return SchemaBuilder.successText("Custom attribute $id deleted successfully.")
    }
}

object GetCustomAttributesValuesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_custom_attributes_values",
        description = "Get the assigned custom attribute values for a specific user story, task, issue, or epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target object type: 'userstory', 'task', 'issue', or 'epic'"),
                "object_id" to SchemaBuilder.intProp("The numeric ID of the user story, task, issue, or epic"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "object_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val objectId = args["object_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'object_id'")

        val result = client.getCustomAttributesValues(type, objectId)
        return SchemaBuilder.successJson(result)
    }
}

object UpdateCustomAttributesValuesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_custom_attributes_values",
        description = "Set or update custom attribute values on a user story, task, issue, or epic. Resolves version automatically.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target object type: 'userstory', 'task', 'issue', or 'epic'"),
                "object_id" to SchemaBuilder.intProp("The numeric ID of the user story, task, issue, or epic"),
                "values" to SchemaBuilder.objectProp("Map of custom attribute ID (as string) to its desired value (e.g. {\"12\": \"v2.1\", \"15\": \"https://link.com\"})"),
                "version" to SchemaBuilder.intProp("Optional current version (if omitted, fetched automatically)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "object_id", "values")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val objectId = args["object_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'object_id'")
        val values = args["values"]?.jsonObject ?: throw IllegalArgumentException("Missing required parameter 'values'")
        val version = args["version"]?.jsonPrimitive?.intOrNull

        val result = client.updateCustomAttributesValues(type, objectId, values, version)
        return SchemaBuilder.successJson(result)
    }
}
