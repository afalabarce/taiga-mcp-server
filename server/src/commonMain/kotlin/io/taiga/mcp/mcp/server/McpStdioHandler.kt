package io.taiga.mcp.mcp.server

import io.taiga.mcp.mcp.protocol.JsonRpcError
import io.taiga.mcp.mcp.protocol.JsonRpcRequest
import io.taiga.mcp.mcp.protocol.JsonRpcResponse
import io.taiga.mcp.taiga.client.TaigaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class McpStdioHandler(
    private val mcpServer: McpServer
) {
    suspend fun run() = withContext(Dispatchers.Default) {
        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue

            try {
                val request = TaigaClient.json.decodeFromString<JsonRpcRequest>(line)
                val response = mcpServer.handleRequest(request)
                if (response != null) {
                    val responseJson = TaigaClient.json.encodeToString(response)
                    println(responseJson)
                    System.out.flush()
                }
            } catch (e: Exception) {
                val errResponse = JsonRpcResponse(
                    id = null,
                    error = JsonRpcError(
                        code = JsonRpcError.PARSE_ERROR,
                        message = "Parse error: ${e.message}"
                    )
                )
                println(TaigaClient.json.encodeToString(errResponse))
                System.out.flush()
            }
        }
    }
}
