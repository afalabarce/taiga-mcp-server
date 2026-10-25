package io.taiga.mcp.mcp.tools

import io.taiga.mcp.config.ConfigManager
import io.taiga.mcp.mcp.protocol.McpCallToolResult
import io.taiga.mcp.mcp.protocol.McpToolDefinition
import io.taiga.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object ListWikiPagesTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_wiki_pages",
        description = "List all wiki pages for a Taiga project",
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
        val pages = client.listWikiPages(projectId)
        return SchemaBuilder.successJson(pages)
    }
}

object GetWikiPageTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_get_wiki_page",
        description = "Get content and metadata of a wiki page by slug",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "slug" to SchemaBuilder.stringProp("The slug of the wiki page (e.g. 'home')"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("slug")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val slug = args["slug"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'slug'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)
        val page = client.getWikiPageBySlug(projectId, slug)
        return SchemaBuilder.successJson(page)
    }
}

object CreateWikiPageTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_wiki_page",
        description = "Create a new wiki page in a Taiga project",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "slug" to SchemaBuilder.stringProp("Unique slug for the page (e.g. 'architecture')"),
                "content" to SchemaBuilder.stringProp("Markdown content of the page"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("slug", "content")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val slug = args["slug"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'slug'")
        val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val payload = buildJsonObject {
            put("slug", slug)
            put("content", content)
            put("project", projectId)
        }

        val page = client.createWikiPage(payload)
        return SchemaBuilder.successJson(page)
    }
}

object UpdateWikiPageTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_update_wiki_page",
        description = "Update the content of an existing wiki page",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the wiki page"),
                "content" to SchemaBuilder.stringProp("Updated markdown content"),
                "version" to SchemaBuilder.intProp("Current version of the wiki page"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id", "content", "version")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val content = args["content"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'content'")
        val version = args["version"]?.jsonPrimitive?.intOrNull ?: 1

        val payload = buildJsonObject {
            put("content", content)
            put("version", version)
        }

        val updated = client.updateWikiPage(id, payload)
        return SchemaBuilder.successJson(updated)
    }
}
