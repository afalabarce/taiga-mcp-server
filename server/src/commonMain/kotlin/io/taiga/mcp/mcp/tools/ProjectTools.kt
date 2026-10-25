package io.taiga.mcp.mcp.tools

import io.taiga.mcp.config.ConfigManager
import io.taiga.mcp.mcp.protocol.McpCallToolResult
import io.taiga.mcp.mcp.protocol.McpToolDefinition
import io.taiga.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object ListProjectsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_projects",
        description = "List all projects accessible to the authenticated user on Taiga",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "member_id" to SchemaBuilder.intProp("Optional member ID to filter projects by member"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val memberId = args["member_id"]?.jsonPrimitive?.longOrNull
        val projects = client.listProjects(memberId)
        return SchemaBuilder.successJson(projects)
    }
}

object GetProjectTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_project",
        description = "Get detailed information about a Taiga project by ID or slug",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project_id" to SchemaBuilder.intProp("Numeric ID of the project"),
                "slug" to SchemaBuilder.stringProp("Project slug (e.g. 'my-team-project')"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (fallback)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectId = args["project_id"]?.jsonPrimitive?.longOrNull
        val slug = args["slug"]?.jsonPrimitive?.contentOrNull
        val fallback = args["project"]?.jsonPrimitive?.contentOrNull

        val project = when {
            projectId != null -> client.getProject(projectId)
            slug != null -> client.getProjectBySlug(slug)
            fallback != null -> {
                val numeric = fallback.toLongOrNull()
                if (numeric != null) client.getProject(numeric) else client.getProjectBySlug(fallback)
            }
            else -> {
                val resolvedId = client.resolveProjectId()
                client.getProject(resolvedId)
            }
        }
        return SchemaBuilder.successJson(project)
    }
}
