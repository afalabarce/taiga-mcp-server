package dev.afalabarce.mcp.mcp.server

import dev.afalabarce.mcp.mcp.protocol.JsonRpcRequest
import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlin.random.Random

data class McpSession(
    val id: String,
    val channel: Channel<String> = Channel(Channel.BUFFERED),
    val authToken: String? = null,
    val apiUrl: String? = null,
    val project: String? = null
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

    suspend fun createSession(
        authToken: String? = null,
        apiUrl: String? = null,
        project: String? = null
    ): McpSession = mutex.withLock {
        val id = generateSessionId()
        val session = McpSession(
            id = id,
            authToken = authToken,
            apiUrl = apiUrl,
            project = project
        )
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
        val response = mcpServer.handleRequest(
            request = request,
            contextAuthToken = session.authToken,
            contextApiUrl = session.apiUrl,
            contextProject = session.project
        ) ?: return true // notification, no response required
        val responseText = TaigaClient.json.encodeToString(response)
        session.channel.send(responseText)
        return true
    }
}
