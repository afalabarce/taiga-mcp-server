package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
        description = "Get detailed information about a milestone/sprint by numeric ID, sprint name, or slug (including user stories and sprint points)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the milestone (sprint)"),
                "milestone" to SchemaBuilder.stringProp("Sprint name or slug (if numeric ID is not known)"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val idParam = args["id"]?.jsonPrimitive?.longOrNull
        val milestoneId = if (idParam != null) {
            idParam
        } else {
            val milestoneParam = args["milestone"] ?: throw IllegalArgumentException("Either 'id' or 'milestone' must be provided")
            TaigaResolver.resolveMilestone(client, projectId, milestoneParam)
                ?: throw IllegalArgumentException("Could not resolve milestone '$milestoneParam'")
        }

        val milestone = client.getMilestone(milestoneId)
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

object UpdateMilestoneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_milestone",
        description = "Update an existing milestone / sprint (change name, dates, or close/open it)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the milestone (sprint)"),
                "milestone" to SchemaBuilder.stringProp("Sprint name or slug (if numeric ID is not known)"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "name" to SchemaBuilder.stringProp("New sprint name"),
                "estimated_start" to SchemaBuilder.stringProp("New estimated start date (YYYY-MM-DD)"),
                "estimated_finish" to SchemaBuilder.stringProp("New estimated finish date (YYYY-MM-DD)"),
                "closed" to SchemaBuilder.booleanProp("Whether the sprint is closed/finished"),
                "disposed" to SchemaBuilder.booleanProp("Whether the sprint is archived/disposed"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val idParam = args["id"]?.jsonPrimitive?.longOrNull
        val milestoneId = if (idParam != null) {
            idParam
        } else {
            val milestoneParam = args["milestone"] ?: throw IllegalArgumentException("Either 'id' or 'milestone' must be provided")
            TaigaResolver.resolveMilestone(client, projectId, milestoneParam)
                ?: throw IllegalArgumentException("Could not resolve milestone '$milestoneParam'")
        }

        val payload = buildJsonObject {
            args["name"]?.jsonPrimitive?.contentOrNull?.let { put("name", it) }
            args["estimated_start"]?.jsonPrimitive?.contentOrNull?.let { put("estimated_start", it) }
            args["estimated_finish"]?.jsonPrimitive?.contentOrNull?.let { put("estimated_finish", it) }
            args["closed"]?.jsonPrimitive?.booleanOrNull?.let { put("closed", it) }
            args["disposed"]?.jsonPrimitive?.booleanOrNull?.let { put("disposed", it) }
        }

        val updated = client.updateMilestone(milestoneId, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteMilestoneTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_milestone",
        description = "Delete a milestone (sprint) by ID or sprint name/slug",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the milestone"),
                "milestone" to SchemaBuilder.stringProp("Sprint name or slug (if numeric ID is not known)"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val idParam = args["id"]?.jsonPrimitive?.longOrNull
        val milestoneId = if (idParam != null) {
            idParam
        } else {
            val milestoneParam = args["milestone"] ?: throw IllegalArgumentException("Either 'id' or 'milestone' must be provided")
            TaigaResolver.resolveMilestone(client, projectId, milestoneParam)
                ?: throw IllegalArgumentException("Could not resolve milestone '$milestoneParam'")
        }

        client.deleteMilestone(milestoneId)
        return SchemaBuilder.successText("Milestone #$milestoneId deleted successfully.")
    }
}

object GetMilestoneStatsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_milestone_stats",
        description = "Get completion and burndown chart statistics for a sprint/milestone",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the milestone"),
                "milestone" to SchemaBuilder.stringProp("Sprint name or slug (if numeric ID is not known)"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val idParam = args["id"]?.jsonPrimitive?.longOrNull
        val milestoneId = if (idParam != null) {
            idParam
        } else {
            val milestoneParam = args["milestone"] ?: throw IllegalArgumentException("Either 'id' or 'milestone' must be provided")
            TaigaResolver.resolveMilestone(client, projectId, milestoneParam)
                ?: throw IllegalArgumentException("Could not resolve milestone '$milestoneParam'")
        }

        val stats = client.getMilestoneStats(milestoneId)
        return SchemaBuilder.successJson(stats)
    }
}
