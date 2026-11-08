package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object GetTimelineTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_timeline",
        description = "Get activity timeline for a user, project, or profile",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Timeline target type: 'user', 'project', or 'profile'"),
                "id" to SchemaBuilder.intProp("Numeric ID of the target"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing 'type'")
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing 'id'")

        val timeline = client.getTimeline(type, id)
        return SchemaBuilder.successJson(timeline)
    }
}
