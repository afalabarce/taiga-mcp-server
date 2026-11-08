package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.TaigaConfig
import dev.afalabarce.mcp.config.TaigaConfigUpdateRequest
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpContent
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaApiException
import dev.afalabarce.mcp.taiga.client.TaigaClient
import dev.afalabarce.mcp.taiga.client.TaigaClientFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

interface McpTool {
    val definition: McpToolDefinition
    suspend fun execute(
        client: TaigaClient,
        args: JsonObject,
        configManager: ConfigManager
    ): McpCallToolResult
}

class TaigaToolRegistry(
    private val configManager: ConfigManager,
    private val clientFactory: TaigaClientFactory
) {
    private val tools = mutableMapOf<String, McpTool>()

    init {
        registerAllTools()
    }

    private fun register(tool: McpTool) {
        tools[tool.definition.name] = tool
    }

    fun listToolDefinitions(): List<McpToolDefinition> = tools.values.map { it.definition }

    fun getTool(name: String): McpTool? = tools[name]

    suspend fun executeTool(
        name: String,
        arguments: JsonObject,
        defaultAuthToken: String? = null,
        defaultApiUrl: String? = null,
        defaultProject: String? = null
    ): McpCallToolResult {
        val tool = tools[name] ?: return McpCallToolResult(
            content = listOf(McpContent(text = "Error: Tool '$name' not found")),
            isError = true
        )

        val overrideApiUrl = arguments["api_url"]?.jsonPrimitive?.contentOrNull ?: defaultApiUrl
        val overrideToken = arguments["auth_token"]?.jsonPrimitive?.contentOrNull ?: defaultAuthToken
        val overrideProject = arguments["project"]?.jsonPrimitive?.contentOrNull ?: defaultProject

        return try {
            val client = clientFactory.getClient(
                overrideApiUrl = overrideApiUrl,
                overrideAuthToken = overrideToken,
                overrideProject = overrideProject
            )
            tool.execute(client, arguments, configManager)
        } catch (e: TaigaApiException) {
            val hint = when (e.statusCode) {
                401 -> " [Hint: Authentication failed. Verify your auth token or call taiga_login/taiga_set_config]"
                403 -> " [Hint: Permission denied. Your Taiga user account does not have sufficient permissions for this action]"
                404 -> " [Hint: Resource or project not found. Verify the numeric ID or project slug]"
                409 -> " [Hint: Concurrency conflict (409). The item version changed. Omit the 'version' parameter to let the MCP server fetch and apply the latest version automatically]"
                else -> ""
            }
            McpCallToolResult(
                content = listOf(McpContent(text = "Taiga API Error (${e.statusCode}): ${e.message}$hint")),
                isError = true
            )
        } catch (e: IllegalArgumentException) {
            McpCallToolResult(
                content = listOf(McpContent(text = "Validation Error: ${e.message}")),
                isError = true
            )
        } catch (e: Exception) {
            McpCallToolResult(
                content = listOf(McpContent(text = "Execution Error: ${e.message ?: e::class.simpleName}")),
                isError = true
            )
        }
    }

    private fun registerAllTools() {
        // --- Projects ---
        register(ListProjectsTool)
        register(GetProjectTool)
        register(CreateProjectTool)
        register(UpdateProjectTool)
        register(DeleteProjectTool)
        register(ListMembershipsTool)
        register(CreateMembershipTool)
        register(DeleteMembershipTool)
        register(ListTagsTool)
        register(DuplicateProjectTool)
        register(ListProjectTemplatesTool)
        register(ExportProjectTool)
        register(ExportCsvTool)
        register(TransferProjectStartTool)
        register(TransferProjectAcceptTool)

        // --- Swimlanes ---
        register(ListSwimlanesTool)
        register(GetSwimlaneTool)
        register(CreateSwimlaneTool)
        register(UpdateSwimlaneTool)
        register(DeleteSwimlaneTool)
        register(ReorderSwimlanesTool)
        register(ListSwimlaneUserStoryStatusesTool)

        // --- Due Dates ---
        register(ListDueDatesTool)
        register(GetDueDateTool)
        register(CreateDueDateTool)
        register(UpdateDueDateTool)
        register(DeleteDueDateTool)

        // --- Bulk Operations ---
        register(BulkCreateUserStoriesTool)
        register(BulkCreateTasksTool)
        register(BulkCreateIssuesTool)
        register(BulkCreateEpicsTool)
        register(BulkCreateMembershipsTool)
        register(MoveToSprintTool)
        register(BulkUpdateBacklogOrderTool)
        register(BulkUpdateKanbanOrderTool)
        register(BulkUpdateTaskboardOrderTool)

        // --- Webhooks ---
        register(ListWebhooksTool)
        register(GetWebhookTool)
        register(CreateWebhookTool)
        register(UpdateWebhookTool)
        register(DeleteWebhookTool)
        register(TestWebhookTool)
        register(ListWebhookLogsTool)
        register(ResendWebhookTool)

        // --- Comment History ---
        register(EditCommentTool)
        register(DeleteCommentTool)
        register(UndeleteCommentTool)
        register(GetCommentVersionsTool)

        // --- Web Notifications & Notify Policies ---
        register(ListWebNotificationsTool)
        register(MarkWebNotificationsReadTool)
        register(ListNotifyPoliciesTool)
        register(UpdateNotifyPolicyTool)

        // --- Timelines ---
        register(GetTimelineTool)

        // --- Epics ---
        register(ListEpicsTool)
        register(GetEpicTool)
        register(CreateEpicTool)
        register(UpdateEpicTool)
        register(DeleteEpicTool)
        register(ListEpicUserStoriesTool)
        register(LinkUserStoryToEpicTool)
        register(UnlinkUserStoryFromEpicTool)

        // --- User Stories ---
        register(ListUserStoriesTool)
        register(GetUserStoryTool)
        register(CreateUserStoryTool)
        register(UpdateUserStoryTool)
        register(DeleteUserStoryTool)

        // --- Tasks ---
        register(ListTasksTool)
        register(GetTaskTool)
        register(CreateTaskTool)
        register(UpdateTaskTool)
        register(DeleteTaskTool)

        // --- Issues ---
        register(ListIssuesTool)
        register(GetIssueTool)
        register(CreateIssueTool)
        register(UpdateIssueTool)
        register(DeleteIssueTool)
        register(PromoteIssueToUserStoryTool)

        // --- Milestones (Sprints) ---
        register(ListMilestonesTool)
        register(GetMilestoneTool)
        register(CreateMilestoneTool)
        register(UpdateMilestoneTool)
        register(DeleteMilestoneTool)
        register(GetMilestoneStatsTool)

        // --- Wiki ---
        register(ListWikiPagesTool)
        register(GetWikiPageTool)
        register(CreateWikiPageTool)
        register(UpdateWikiPageTool)
        register(DeleteWikiPageTool)
        register(ListWikiLinksTool)
        register(CreateWikiLinkTool)
        register(DeleteWikiLinkTool)

        // --- Attachments ---
        register(ListAttachmentsTool)
        register(UploadAttachmentTool)
        register(DeleteAttachmentTool)

        // --- Custom Attributes ---
        register(ListCustomAttributesTool)
        register(CreateCustomAttributeTool)
        register(DeleteCustomAttributeTool)
        register(GetCustomAttributesValuesTool)
        register(UpdateCustomAttributesValuesTool)

        // --- Users ---
        register(GetMeTool)
        register(GetUserTool)
        register(GetUserContactsTool)
        register(GetUserLikedTool)
        register(GetUserVotedTool)
        register(GetUserWatchedTool)

        // --- Social & Engagement (Votes & Watchers) ---
        register(UpvoteTool)
        register(DownvoteTool)
        register(WatchTool)
        register(UnwatchTool)
        register(ListVotersTool)
        register(ListWatchersTool)

        // --- Project Statuses / Metadata ---
        register(GetProjectStatusesTool)

        // --- Search, High-Level Overview & Collaboration ---
        register(SearchTool)
        register(GetProjectSummaryTool)
        register(AddCommentTool)
        register(ListMembersTool)
        register(GetHistoryTool)

        // --- Configuration & Authentication ---
        register(GetConfigTool)
        register(SetConfigTool)
        register(LoginTool)
        register(TestConnectionTool)
    }
}

// Helpers for Building JSON Schemas easily
object SchemaBuilder {
    fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String> = emptyList()
    ): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (name, schema) -> put(name, schema) }
        }
        if (required.isNotEmpty()) {
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }
    }

    fun stringProp(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    fun intProp(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    fun numberProp(description: String): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    fun booleanProp(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    fun arrayProp(description: String, itemType: String = "string"): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        putJsonObject("items") {
            put("type", itemType)
        }
    }

    fun objectProp(description: String): JsonObject = buildJsonObject {
        put("type", "object")
        put("description", description)
    }

    inline fun <reified T> successJson(data: T): McpCallToolResult {
        val text = TaigaClient.json.encodeToString(data)
        return McpCallToolResult(content = listOf(McpContent(text = text)), isError = false)
    }

    fun successText(text: String): McpCallToolResult {
        return McpCallToolResult(content = listOf(McpContent(text = text)), isError = false)
    }
}
