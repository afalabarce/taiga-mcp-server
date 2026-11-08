package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.readFileBytesIfExists
import dev.afalabarce.mcp.mcp.protocol.McpCallToolResult
import dev.afalabarce.mcp.mcp.protocol.McpToolDefinition
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ListAttachmentsTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_list_attachments",
        description = "List all file attachments associated with a user story, task, issue, epic, or wiki page",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target object type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "object_id" to SchemaBuilder.intProp("The numeric ID of the parent object"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "object_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val objectId = args["object_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'object_id'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val attachments = client.listAttachments(type, objectId, projectId)
        return SchemaBuilder.successJson(attachments)
    }
}

object UploadAttachmentTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_upload_attachment",
        description = "Upload a file attachment to a user story, task, issue, epic, or wiki page. Can load from a local file path or accept base64-encoded file data directly.",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target object type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "object_id" to SchemaBuilder.intProp("The numeric ID of the parent object"),
                "project" to SchemaBuilder.stringProp("Project ID or slug (defaults to configured project)"),
                "file_path" to SchemaBuilder.stringProp("Path to the local file to attach"),
                "file_content_base64" to SchemaBuilder.stringProp("Base64-encoded string of the file payload (alternative to file_path)"),
                "file_name" to SchemaBuilder.stringProp("File name (e.g. 'diagram.png', 'test_results.log'). Required if file_content_base64 is used"),
                "mime_type" to SchemaBuilder.stringProp("Optional MIME type (e.g. 'image/png', 'application/pdf'). Auto-detected if omitted"),
                "description" to SchemaBuilder.stringProp("Optional text description for the attachment"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "object_id")
        )
    )

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val objectId = args["object_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'object_id'")
        val projectParam = args["project"]?.jsonPrimitive?.contentOrNull
        val projectId = client.resolveProjectId(projectParam)

        val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull
        val base64Content = args["file_content_base64"]?.jsonPrimitive?.contentOrNull
        val providedFileName = args["file_name"]?.jsonPrimitive?.contentOrNull
        val description = args["description"]?.jsonPrimitive?.contentOrNull

        val (filename, fileBytes) = when {
            base64Content != null -> {
                val cleanBase64 = base64Content.substringAfter("base64,").trim()
                val fname = providedFileName ?: "attachment.bin"
                val bytes = try {
                    Base64.decode(cleanBase64)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid base64 payload in 'file_content_base64': ${e.message}")
                }
                fname to bytes
            }
            filePath != null -> {
                val bytes = readFileBytesIfExists(filePath)
                    ?: throw IllegalArgumentException("Local file not found or inaccessible at path: $filePath")
                val fname = providedFileName ?: filePath.substringAfterLast("/").substringAfterLast("\\")
                fname to bytes
            }
            else -> throw IllegalArgumentException("Must provide either 'file_path' or 'file_content_base64'")
        }

        val mimeType = args["mime_type"]?.jsonPrimitive?.contentOrNull ?: guessMimeType(filename)

        val result = client.uploadAttachment(
            type = type,
            objectId = objectId,
            projectId = projectId,
            filename = filename,
            fileBytes = fileBytes,
            mimeType = mimeType,
            description = description
        )
        return SchemaBuilder.successJson(result)
    }

    private fun guessMimeType(filename: String): String {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "txt", "log", "md" -> "text/plain"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "tar", "gz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }
}

object DeleteAttachmentTool : McpTool {
    override val definition = McpToolDefinition(
        name = "taiga_delete_attachment",
        description = "Delete a file attachment by its attachment ID and parent resource type",
        inputSchema = SchemaBuilder.objectSchema(
            properties = mapOf(
                "type" to SchemaBuilder.stringProp("Target object type: 'userstory', 'task', 'issue', 'epic', or 'wiki'"),
                "attachment_id" to SchemaBuilder.intProp("The numeric ID of the attachment to delete"),
                "api_url" to SchemaBuilder.stringProp("Optional override for Taiga API URL"),
                "auth_token" to SchemaBuilder.stringProp("Optional override for Taiga Auth Token")
            ),
            required = listOf("type", "attachment_id")
        )
    )

    override suspend fun execute(client: TaigaClient, args: JsonObject, configManager: ConfigManager): McpCallToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing required parameter 'type'")
        val attachmentId = args["attachment_id"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Missing required parameter 'attachment_id'")

        client.deleteAttachment(type, attachmentId)
        return SchemaBuilder.successText("Attachment $attachmentId successfully deleted from $type.")
    }
}
