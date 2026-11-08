package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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

object CreateProjectTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_project",
        description = "Create a new project in Taiga",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "name" to SchemaBuilder.stringProp("Project display name"),
                "description" to SchemaBuilder.stringProp("Project description"),
                "is_private" to SchemaBuilder.booleanProp("Whether project is private (true) or public (false). Default: true"),
                "tags" to SchemaBuilder.arrayProp("List of project tags"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("name", "description")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val description = args["description"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'description'")
        val isPrivate = args["is_private"]?.jsonPrimitive?.booleanOrNull ?: true

        val payload = buildJsonObject {
            put("name", name)
            put("description", description)
            put("is_private", isPrivate)
            args["tags"]?.let { tagsEl ->
                val tagsList = tagsEl.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                put("tags", buildJsonArray { tagsList.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }

        val project = client.createProject(payload)
        return SchemaBuilder.successJson(project)
    }
}

object UpdateProjectTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_project",
        description = "Update project metadata (name, description, privacy, tags)",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project_id" to SchemaBuilder.intProp("Numeric ID of the project"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "name" to SchemaBuilder.stringProp("Updated project name"),
                "description" to SchemaBuilder.stringProp("Updated project description"),
                "is_private" to SchemaBuilder.booleanProp("Update privacy setting"),
                "tags" to SchemaBuilder.arrayProp("Updated list of tags"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectId = args["project_id"]?.jsonPrimitive?.longOrNull
            ?: client.resolveProjectId(args["project"]?.jsonPrimitive?.contentOrNull)

        val payload = buildJsonObject {
            args["name"]?.jsonPrimitive?.contentOrNull?.let { put("name", it) }
            args["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
            args["is_private"]?.jsonPrimitive?.booleanOrNull?.let { put("is_private", it) }
            args["tags"]?.let { tagsEl ->
                val tagsList = tagsEl.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                put("tags", buildJsonArray { tagsList.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }

        val updated = client.updateProject(projectId, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteProjectTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_project",
        description = "Delete a project by numeric ID or slug",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project_id" to SchemaBuilder.intProp("Numeric ID of the project to delete"),
                "project" to SchemaBuilder.stringProp("Project ID or slug to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectId = args["project_id"]?.jsonPrimitive?.longOrNull
            ?: client.resolveProjectId(args["project"]?.jsonPrimitive?.contentOrNull)

        client.deleteProject(projectId)
        return SchemaBuilder.successText("Project #$projectId successfully deleted.")
    }
}

object ListMembershipsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_memberships",
        description = "List all memberships (members and their roles, invitation status) in a project",
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
        val memberships = client.listMemberships(projectId)
        return SchemaBuilder.successJson(memberships)
    }
}

object CreateMembershipTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_membership",
        description = "Add or invite a member to a project with a specific role name (e.g. 'Front', 'Back', 'Product Owner') or numeric role ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "email" to SchemaBuilder.stringProp("Email of the user to invite / add"),
                "role" to SchemaBuilder.stringProp("Role name (e.g. 'Back', 'Front', 'UX', 'Product Owner') or numeric role ID"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("email", "role")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val email = args["email"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'email'")
        val roleParam = args["role"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'role'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val roleId = roleParam.toLongOrNull() ?: run {
            val roles = client.getProjectRoles(projectId)
            roles.firstOrNull { it.name.equals(roleParam, ignoreCase = true) || it.slug.equals(roleParam, ignoreCase = true) }?.id
                ?: throw IllegalArgumentException("Role '$roleParam' not found. Available roles: ${roles.map { it.name }}")
        }

        val payload = buildJsonObject {
            put("project", projectId)
            put("role", roleId)
            put("email", email)
        }

        val membership = client.createMembership(payload)
        return SchemaBuilder.successJson(membership)
    }
}

object DeleteMembershipTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_membership",
        description = "Remove a member from a project by their membership ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("The numeric ID of the membership to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteMembership(id)
        return SchemaBuilder.successText("Membership #$id deleted successfully.")
    }
}

object ListTagsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_tags",
        description = "List all existing tags and their associated color codes in a project",
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
        val tags = client.getProjectTags(projectId)
        return SchemaBuilder.successJson(tags)
    }
}

object DuplicateProjectTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_duplicate_project",
        description = "Duplicate an existing Taiga project including its configuration",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Source project ID or slug (defaults to configured project)"),
                "name" to SchemaBuilder.stringProp("Name of the new duplicated project"),
                "description" to SchemaBuilder.stringProp("Optional description of the new project"),
                "is_private" to SchemaBuilder.booleanProp("Whether the new project is private"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("name")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'name'")
        val description = args["description"]?.jsonPrimitive?.contentOrNull
        val isPrivate = args["is_private"]?.jsonPrimitive?.booleanOrNull

        val duplicated = client.duplicateProject(projectId, name, description, isPrivate)
        return SchemaBuilder.successJson(duplicated)
    }
}

object ListProjectTemplatesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_project_templates",
        description = "List all available project templates in Taiga",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val templates = client.listProjectTemplates()
        return SchemaBuilder.successJson(templates)
    }
}

object ExportProjectTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_export_project",
        description = "Schedule or trigger an export dump of a Taiga project",
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
        val exportResult = client.exportProject(projectId)
        return SchemaBuilder.successJson(exportResult)
    }
}

object ExportCsvTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_export_csv",
        description = "Export user stories, tasks, issues, or epics of a project in CSV format",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Resource type to export: 'userstories', 'tasks', 'issues', or 'epics'"),
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
        val csv = client.exportCsv(type, projectId)
        return SchemaBuilder.successText(csv)
    }
}

object TransferProjectStartTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_transfer_project_start",
        description = "Initiate transferring project ownership to another user",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "user" to SchemaBuilder.intProp("Numeric ID of the target user to transfer ownership to"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("user")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val user = args["user"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'user'")
        val result = client.transferProjectStart(projectId, user)
        return SchemaBuilder.successJson(result)
    }
}

object TransferProjectAcceptTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_transfer_project_accept",
        description = "Accept transferring project ownership using the transfer token",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "token" to SchemaBuilder.stringProp("The transfer token received"),
                "reason" to SchemaBuilder.stringProp("Optional reason or rejection notice"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("token")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val token = args["token"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'token'")
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull
        val result = client.transferProjectAccept(projectId, token, reason)
        return SchemaBuilder.successJson(result)
    }
}
