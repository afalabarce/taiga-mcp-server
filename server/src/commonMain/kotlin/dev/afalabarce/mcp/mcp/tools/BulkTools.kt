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

object BulkCreateUserStoriesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_create_user_stories",
        description = "Create multiple user stories at once from newline-delimited titles",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_stories" to SchemaBuilder.stringProp("Newline-separated story titles (one story per line)"),
                "status" to SchemaBuilder.stringProp("Status name or numeric ID for the stories"),
                "swimlane_id" to SchemaBuilder.intProp("Optional numeric ID of the swimlane"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_stories")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val bulkStories = args["bulk_stories"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'bulk_stories'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val statusParam = args["status"]
        val statusId = statusParam?.let { TaigaResolver.resolveUserStoryStatus(client, projectId, it) }
        val swimlaneId = args["swimlane_id"]?.jsonPrimitive?.longOrNull

        val stories = client.bulkCreateUserStories(bulkStories, statusId, swimlaneId, projectId)
        return SchemaBuilder.successJson(stories)
    }
}

object BulkCreateTasksTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_create_tasks",
        description = "Create multiple tasks at once from newline-delimited titles",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_tasks" to SchemaBuilder.stringProp("Newline-separated task titles (one task per line)"),
                "user_story_id" to SchemaBuilder.intProp("Optional parent user story numeric ID"),
                "milestone" to SchemaBuilder.stringProp("Optional sprint/milestone name, slug, or ID"),
                "status" to SchemaBuilder.stringProp("Optional status name or numeric ID"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_tasks")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val bulkTasks = args["bulk_tasks"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'bulk_tasks'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val usId = args["user_story_id"]?.jsonPrimitive?.longOrNull
        val milestoneParam = args["milestone"]
        val milestoneId = milestoneParam?.let { TaigaResolver.resolveMilestone(client, projectId, it) }
        val statusParam = args["status"]
        val statusId = statusParam?.let { TaigaResolver.resolveTaskStatus(client, projectId, it) }

        val tasks = client.bulkCreateTasks(bulkTasks, usId, milestoneId, statusId, projectId)
        return SchemaBuilder.successJson(tasks)
    }
}

object BulkCreateIssuesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_create_issues",
        description = "Create multiple issues at once from newline-delimited titles",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_issues" to SchemaBuilder.stringProp("Newline-separated issue titles (one issue per line)"),
                "milestone" to SchemaBuilder.stringProp("Optional sprint/milestone name, slug, or ID"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_issues")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val bulkIssues = args["bulk_issues"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'bulk_issues'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val milestoneParam = args["milestone"]
        val milestoneId = milestoneParam?.let { TaigaResolver.resolveMilestone(client, projectId, it) }

        val issues = client.bulkCreateIssues(bulkIssues, milestoneId, projectId)
        return SchemaBuilder.successJson(issues)
    }
}

object BulkCreateEpicsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_create_epics",
        description = "Create multiple epics at once from newline-delimited titles",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_epics" to SchemaBuilder.stringProp("Newline-separated epic titles (one epic per line)"),
                "status_id" to SchemaBuilder.intProp("Optional epic status ID"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_epics")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val bulkEpics = args["bulk_epics"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'bulk_epics'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val statusId = args["status_id"]?.jsonPrimitive?.longOrNull

        val epics = client.bulkCreateEpics(bulkEpics, statusId, projectId)
        return SchemaBuilder.successJson(epics)
    }
}

object BulkCreateMembershipsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_create_memberships",
        description = "Add multiple members to a project at once",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_memberships" to SchemaBuilder.arrayProp("Array of objects with 'role_id' and 'email'"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_memberships")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val bulkMemberships = args["bulk_memberships"]?.jsonArray ?: throw IllegalArgumentException("Missing 'bulk_memberships'")

        val memberships = client.bulkCreateMemberships(bulkMemberships, projectId)
        return SchemaBuilder.successJson(memberships)
    }
}

object MoveToSprintTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_move_to_sprint",
        description = "Move multiple user stories, tasks, or issues into a sprint/milestone",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Resource type: 'userstories', 'tasks', or 'issues'"),
                "milestone" to SchemaBuilder.stringProp("Target milestone/sprint name, slug, or numeric ID"),
                "item_ids" to SchemaBuilder.arrayProp("List of numeric IDs of the items to move"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "milestone", "item_ids")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'type'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val milestoneParam = args["milestone"] ?: throw IllegalArgumentException("Missing 'milestone'")
        val milestoneId = TaigaResolver.resolveMilestone(client, projectId, milestoneParam)
            ?: throw IllegalArgumentException("Milestone not found: '$milestoneParam'")
        val itemIds = args["item_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull }
            ?: throw IllegalArgumentException("Missing or empty 'item_ids'")

        val result = client.moveToSprint(type, milestoneId, itemIds, projectId)
        return SchemaBuilder.successJson(result)
    }
}

object BulkUpdateBacklogOrderTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_update_backlog_order",
        description = "Reorder user stories in the backlog",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_stories" to SchemaBuilder.arrayProp("Array of objects with 'us_id' and 'order'"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_stories")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val bulkStories = args["bulk_stories"]?.jsonArray ?: throw IllegalArgumentException("Missing 'bulk_stories'")

        client.bulkUpdateBacklogOrder(bulkStories, projectId)
        return SchemaBuilder.successText("Backlog order updated successfully.")
    }
}

object BulkUpdateKanbanOrderTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_update_kanban_order",
        description = "Reorder user stories on the Kanban board (order and status_id)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_stories" to SchemaBuilder.arrayProp("Array of objects with 'us_id', 'order', and 'status_id'"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_stories")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val bulkStories = args["bulk_stories"]?.jsonArray ?: throw IllegalArgumentException("Missing 'bulk_stories'")

        client.bulkUpdateKanbanOrder(bulkStories, projectId)
        return SchemaBuilder.successText("Kanban order updated successfully.")
    }
}

object BulkUpdateTaskboardOrderTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_bulk_update_taskboard_order",
        description = "Reorder tasks on the sprint Taskboard (order and status_id)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "bulk_tasks" to SchemaBuilder.arrayProp("Array of objects with 'task_id', 'order', and 'status_id'"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("bulk_tasks")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val bulkTasks = args["bulk_tasks"]?.jsonArray ?: throw IllegalArgumentException("Missing 'bulk_tasks'")

        client.bulkUpdateTaskboardOrder(bulkTasks, projectId)
        return SchemaBuilder.successText("Taskboard order updated successfully.")
    }
}
