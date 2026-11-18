package dev.afalabarce.mcp.mcp.server

import dev.afalabarce.mcp.mcp.protocol.JsonRpcError
import dev.afalabarce.mcp.mcp.protocol.JsonRpcRequest
import dev.afalabarce.mcp.mcp.protocol.JsonRpcResponse
import dev.afalabarce.mcp.taiga.client.TaigaClient
import dev.afalabarce.mcp.web.auth.TaigaBasicAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.PrintStream

class McpStdioHandler(
    private val mcpServer: McpServer,
    private val output: PrintStream = System.out,
    private val input: BufferedReader = System.`in`.bufferedReader(),
    private val basicAuthManager: TaigaBasicAuthManager? = null,
    private val basicUsername: String? = null,
    private val basicPassword: String? = null
) {
    private val stdioJson = Json(TaigaClient.json) {
        prettyPrint = false
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) continue

            try {
                val request = stdioJson.decodeFromString<JsonRpcRequest>(line)
                val response = executeRequest(request)
                if (response != null) {
                    val responseJson = stdioJson.encodeToString(response)
                    output.println(responseJson)
                    output.flush()
                }
            } catch (e: Exception) {
                val errResponse = JsonRpcResponse(
                    id = null,
                    error = JsonRpcError(
                        code = JsonRpcError.PARSE_ERROR,
                        message = "Parse error: ${e.message}"
                    )
                )
                output.println(stdioJson.encodeToString(errResponse))
                output.flush()
            }
        }
    }

    private suspend fun executeRequest(request: JsonRpcRequest): JsonRpcResponse? {
        val manager = basicAuthManager
        val username = basicUsername?.takeIf { it.isNotBlank() }
        val password = basicPassword?.takeIf { it.isNotBlank() }
        val shouldUseBasicAuth = manager != null &&
            username != null &&
            password != null &&
            request.method == "tools/call" &&
            mcpServer.configManager.getConfig().authToken.isNullOrBlank()

        if (!shouldUseBasicAuth) {
            return mcpServer.handleRequest(request)
        }

        val token = manager.authenticate(username, password)
        val response = mcpServer.handleRequest(request, contextAuthToken = token)

        if (token != null && response != null && isUnauthorizedToolResponse(response)) {
            manager.invalidate(username, password)
            val refreshedToken = manager.authenticate(username, password)
            if (refreshedToken != null) {
                return mcpServer.handleRequest(request, contextAuthToken = refreshedToken)
            }
        }
        return response
    }

    private fun isUnauthorizedToolResponse(response: JsonRpcResponse): Boolean {
        val resultObj = response.result as? kotlinx.serialization.json.JsonObject ?: return false
        val isError = resultObj["isError"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!isError) return false
        val firstText = resultObj["content"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return false
        return firstText.contains("Taiga API Error (401)")
    }
}
