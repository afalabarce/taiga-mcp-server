package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SearchTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_search",
        description = "Perform a unified search across all project resources (user stories, tasks, issues, epics, and wiki pages) with a single query. Automatically falls back to natural language query resolution if no exact keyword match is found.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "query" to SchemaBuilder.stringProp("Search query / text to look for"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("query")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'query'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val result = client.search(projectId, query)
        if (result.count == 0 && (query.contains(" ") || query.length > 5)) {
            val naturalResult = runCatching {
                TaigaNaturalQueryEngine.executeQuery(client, projectId, query)
            }.getOrNull()
            if (naturalResult != null && naturalResult.totalCount > 0) {
                return SchemaBuilder.successText(
                    "Taiga full-text search returned 0 exact matches. Natural language query engine resolved the following:\n\n" +
                    naturalResult.markdownTable
                )
            }
        }
        return SchemaBuilder.successJson(result)
    }
}

object NaturalQueryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_natural_query",
        description = "Execute a natural language query in Spanish or English against Taiga. Understands resource types ('tareas', 'historias', 'bugs', 'epics'), status ('pendientes'/'open', 'cerradas'/'closed', 'en progreso'), sprints ('sprint actual'/'current sprint', 'sprint 1'), assignees ('asignadas a Juan', 'sin asignar'), priorities, and keywords. Automatically resolves project members, active sprints, and filters, returning a compact, token-efficient summary table or JSON without requiring custom Python scripts or multi-step API roundtrips.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "query" to SchemaBuilder.stringProp("The natural language query or question (e.g. 'tareas pendientes del sprint actual', 'historias asignadas a Antonio', 'bugs críticos abiertos')"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "format" to SchemaBuilder.stringProp("Output format: 'table' (markdown table + summary, default, recommended for minimal tokens) or 'json' (compact JSON)"),
                "type" to SchemaBuilder.stringProp("Optional entity filter override: 'task', 'user_story', 'issue', 'epic', 'wiki', or 'all'"),
                "status" to SchemaBuilder.stringProp("Optional status filter override: 'open', 'closed', 'pending', or status name"),
                "milestone" to SchemaBuilder.stringProp("Optional milestone override: 'current', 'sprint 1', etc."),
                "assigned_to" to SchemaBuilder.stringProp("Optional assignee override: 'unassigned' or member name/email"),
                "limit" to SchemaBuilder.intProp("Maximum items to return (default: 25, max: 100)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("query")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'query'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val format = args["format"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "table"
        val explicitType = args["type"]?.jsonPrimitive?.contentOrNull
        val explicitStatus = args["status"]?.jsonPrimitive?.contentOrNull
        val explicitMilestone = args["milestone"]?.jsonPrimitive?.contentOrNull
        val explicitAssignee = args["assigned_to"]?.jsonPrimitive?.contentOrNull
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 25

        val result = TaigaNaturalQueryEngine.executeQuery(
            client = client,
            projectId = projectId,
            query = query,
            explicitType = explicitType,
            explicitStatus = explicitStatus,
            explicitMilestone = explicitMilestone,
            explicitAssignee = explicitAssignee,
            limit = limit
        )

        return if (format == "json") {
            SchemaBuilder.successJson(result)
        } else {
            SchemaBuilder.successText(result.markdownTable)
        }
    }
}

object QueryTool : McpTool {
    override val definition = NaturalQueryTool.definition.copy(
        name = "taiga_query",
        description = "Alias for taiga_natural_query. Execute natural language queries over Taiga user stories, tasks, issues, and sprints."
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        return NaturalQueryTool.execute(client, args, configManager)
    }
}

object GetProjectSummaryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_project_summary",
        description = "Get an executive summary of the project in a single call: active sprints, story/task/issue counts grouped by status, and team members. Essential for understanding overall project state without making dozens of API calls.",
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

        val summary = client.getProjectSummary(projectId)
        return SchemaBuilder.successJson(summary)
    }
}

object AddCommentTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_add_comment",
        description = "Add a progress update, note, or comment to any Taiga item (user story, task, issue, or epic). Concurrency versions and locks are managed automatically by the MCP server.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Item type: 'user_story', 'task', 'issue', or 'epic'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the item"),
                "comment" to SchemaBuilder.stringProp("Markdown text of the comment to post"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id", "comment")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val comment = args["comment"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'comment'")

        val result = client.addComment(type, id, comment)
        return SchemaBuilder.successText("Comment successfully added to $type #$id.")
    }
}

object ListMembersTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_members",
        description = "List all members of a project with their user IDs, usernames, full names, emails, and roles. Indispensable for discovering team members and assigning stories, tasks, or issues.",
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

        val members = client.listMemberships(projectId)
        return SchemaBuilder.successJson(members)
    }
}

object GetHistoryTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_history",
        description = "Get chronological history, activities, and comments for a user story, task, issue, or epic",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Item type: 'user_story', 'task', 'issue', or 'epic'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the item"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")

        val history = client.getHistory(type, id)
        return SchemaBuilder.successJson(history)
    }
}
