package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object ListMilestonesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_milestones",
        description = "List all milestones (sprints) for a Taiga project",
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
        val milestones = client.listMilestones(projectId)
        return SchemaBuilder.successJson(milestones)
    }
}

object GetMilestoneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_milestone",
        description = "Get detailed information about a milestone/sprint by its ID (including its user stories and sprint points)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the milestone (sprint)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val milestone = client.getMilestone(id)
        return SchemaBuilder.successJson(milestone)
    }
}

object CreateMilestoneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_milestone",
        description = "Create a new milestone (sprint) in a Taiga project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "name" to SchemaBuilder.stringProp("Sprint / Milestone name (e.g. 'Sprint 1')"),
                "estimated_start" to SchemaBuilder.stringProp("Estimated start date (YYYY-MM-DD)"),
                "estimated_finish" to SchemaBuilder.stringProp("Estimated finish date (YYYY-MM-DD)"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("name", "estimated_start", "estimated_finish")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val estimatedStart = args["estimated_start"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'estimated_start'")
        val estimatedFinish = args["estimated_finish"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'estimated_finish'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val payload = buildJsonObject {
            put("name", name)
            put("project", projectId)
            put("estimated_start", estimatedStart)
            put("estimated_finish", estimatedFinish)
        }

        val milestone = client.createMilestone(payload)
        return SchemaBuilder.successJson(milestone)
    }
}
