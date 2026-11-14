package dev.afalabarce.mcp.mcp.server

import dev.afalabarce.mcp.mcp.protocol.JsonRpcError
import dev.afalabarce.mcp.mcp.protocol.JsonRpcRequest
import dev.afalabarce.mcp.mcp.protocol.JsonRpcResponse
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PrintStream

class McpStdioHandler(
    private val mcpServer: McpServer,
    private val output: PrintStream = System.out
) {
    private val stdioJson = Json(TaigaClient.json) {
        prettyPrint = false
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue

            try {
                val request = stdioJson.decodeFromString<JsonRpcRequest>(line)
                val response = mcpServer.handleRequest(request)
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
}
