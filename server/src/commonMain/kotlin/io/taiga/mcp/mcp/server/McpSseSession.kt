package io.taiga.mcp.mcp.server

import io.taiga.mcp.mcp.protocol.JsonRpcRequest
import io.taiga.mcp.taiga.client.TaigaClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlin.random.Random

data class McpSession(
    val id: String,
    val channel: Channel<String> = Channel(Channel.BUFFERED)
)

class McpSessionManager(
    private val mcpServer: McpServer
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, McpSession>()

    private fun generateSessionId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val randomString = (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "sess_$randomString"
    }

    suspend fun createSession(): McpSession = mutex.withLock {
        val id = generateSessionId()
        val session = McpSession(id)
        sessions[id] = session
        session
    }

    suspend fun getSession(id: String): McpSession? = mutex.withLock {
        sessions[id]
    }

    suspend fun removeSession(id: String) = mutex.withLock {
        val session = sessions.remove(id)
        session?.channel?.close()
    }

    suspend fun handleIncomingMessage(sessionId: String, request: JsonRpcRequest): Boolean {
        val session = getSession(sessionId) ?: return false
        val response = mcpServer.handleRequest(request) ?: return true // notification, no response required
        val responseText = TaigaClient.json.encodeToString(response)
        session.channel.send(responseText)
        return true
    }
}
