package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
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
        description = "Get detailed content of a wiki page by slug or numeric ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "slug" to SchemaBuilder.stringProp("The slug of the wiki page (e.g. 'home')"),
                "id" to SchemaBuilder.intProp("Optional numeric ID of the wiki page (if slug is not provided)"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val slug = args["slug"]?.jsonPrimitive?.contentOrNull
        val id = args["id"]?.jsonPrimitive?.longOrNull

        val page = when {
            slug != null -> {
                val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
                val projectId = client.resolveProjectId(projectParam)
                client.getWikiPageBySlug(projectId, slug)
            }
            id != null -> client.getWikiPage(id)
            else -> throw IllegalArgumentException("Either 'slug' or 'id' must be provided")
        }
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
        val content = args["content"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'content'")
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
        description = "Update the content of an existing wiki page. Version concurrency is automatically handled if omitted.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the wiki page"),
                "content" to SchemaBuilder.stringProp("Updated markdown content"),
                "version" to SchemaBuilder.intProp("Optional current version of the wiki page (auto-resolved if omitted)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id", "content")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        val content = args["content"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'content'")
        val version = args["version"]?.jsonPrimitive?.intOrNull ?: run {
            client.getWikiPage(id).version
        }

        val payload = buildJsonObject {
            put("content", content)
            put("version", version)
        }

        val updated = client.updateWikiPage(id, payload)
        return SchemaBuilder.successJson(updated)
    }
}

object DeleteWikiPageTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_wiki_page",
        description = "Delete a wiki page by ID or slug",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the wiki page (or specify 'slug')"),
                "slug" to SchemaBuilder.stringProp("Slug of the wiki page (e.g. 'home')"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (required if using 'slug', defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            )
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val idParam = args["id"]?.jsonPrimitive?.longOrNull
        val slugParam = args["slug"]?.jsonPrimitive?.contentOrNull
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull

        val pageId = when {
            idParam != null -> idParam
            slugParam != null -> {
                val projectId = client.resolveProjectId(projectParam)
                client.getWikiPageBySlug(projectId, slugParam).id
                    ?: throw IllegalArgumentException("Wiki page '$slugParam' not found")
            }
            else -> throw IllegalArgumentException("Either 'id' or 'slug' must be provided")
        }

        client.deleteWikiPage(pageId)
        return SchemaBuilder.successText("Wiki page #$pageId successfully deleted.")
    }
}

object ListWikiLinksTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_wiki_links",
        description = "List all wiki links / navigation table of contents for a Taiga project",
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

        val links = client.listWikiLinks(projectId)
        return SchemaBuilder.successJson(links)
    }
}

object CreateWikiLinkTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_create_wiki_link",
        description = "Create a new wiki link entry in project wiki navigation",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "title" to SchemaBuilder.stringProp("Display title of the link"),
                "href" to SchemaBuilder.stringProp("Slug or URL target of the link (e.g. 'architecture-overview')"),
                "order" to SchemaBuilder.intProp("Display order in navigation menu"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("title", "href")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'title'")
        val href = args["href"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'href'")
        val order = args["order"]?.jsonPrimitive?.longOrNull
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val payload = buildJsonObject {
            put("title", title)
            put("href", href)
            put("project", projectId)
            order?.let { put("order", it) }
        }

        val result = client.createWikiLink(payload)
        return SchemaBuilder.successJson(result)
    }
}

object DeleteWikiLinkTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_wiki_link",
        description = "Delete a wiki link entry by its numeric ID",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "id" to SchemaBuilder.intProp("Numeric ID of the wiki link to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val id = args["id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'id'")
        client.deleteWikiLink(id)
        return SchemaBuilder.successText("Wiki link #$id successfully deleted.")
    }
}
