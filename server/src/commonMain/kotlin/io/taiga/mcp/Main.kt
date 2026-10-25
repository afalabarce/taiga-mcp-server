package io.taiga.mcp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.taiga.mcp.config.ConfigManager
import io.taiga.mcp.config.TaigaConfig
import io.taiga.mcp.config.getEnv
import io.taiga.mcp.mcp.server.McpServer
import io.taiga.mcp.mcp.server.McpSessionManager
import io.taiga.mcp.mcp.server.McpStdioHandler
import io.taiga.mcp.mcp.tools.TaigaToolRegistry
import io.taiga.mcp.taiga.client.TaigaClientFactory
import io.taiga.mcp.web.configureServer
import kotlinx.coroutines.runBlocking

class TaigaMcpCommand : CliktCommand(
    name = "taiga-mcpserver"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String =
        "Taiga Model Context Protocol (MCP) Server with remote API parameterization, SSE and Swagger UI"
    private val host: String by option(
        "--host", "-h",
        help = "Host interface to bind HTTP server (default: 0.0.0.0)"
    ).default(getEnv("TAIGA_HOST") ?: getEnv("HOST") ?: "0.0.0.0")

    private val port: Int by option(
        "--port", "-p",
        help = "Port to listen on (default: 8080 or PORT env var)"
    ).int().default(getEnv("TAIGA_PORT")?.toIntOrNull() ?: getEnv("PORT")?.toIntOrNull() ?: 8080)

    private val stdio: Boolean by option(
        "--stdio",
        help = "Run in MCP Stdio mode (for local CLI / desktop integrations like Claude Desktop)"
    ).flag(default = false)

    private val taigaUrl: String? by option(
        "--taiga-url",
        help = "Taiga API URL (default: TAIGA_API_URL or https://api.taiga.io)"
    )

    private val taigaToken: String? by option(
        "--taiga-token",
        help = "Taiga Auth / Bearer Token (default: TAIGA_AUTH_TOKEN)"
    )

    private val taigaProject: String? by option(
        "--taiga-project",
        help = "Default Taiga Project slug or numeric ID (default: TAIGA_DEFAULT_PROJECT)"
    )

    override fun run() {
        // Initialize config from environment + CLI arguments
        val envConfig = ConfigManager.loadFromEnvironment()
        val initialConfig = envConfig.copy(
            apiUrl = taigaUrl?.ifBlank { null } ?: envConfig.apiUrl,
            authToken = taigaToken?.ifBlank { null } ?: envConfig.authToken,
            defaultProject = taigaProject?.ifBlank { null } ?: envConfig.defaultProject
        )

        val configManager = ConfigManager(initialConfig)
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        if (stdio) {
            // MCP Stdio transport mode
            System.err.println("Starting Taiga MCP server in stdio mode...")
            val stdioHandler = McpStdioHandler(mcpServer)
            runBlocking {
                stdioHandler.run()
            }
        } else {
            // HTTP / SSE / Swagger Server mode
            val sessionManager = McpSessionManager(mcpServer)

            println("=================================================================")
            println("            TAIGA MODEL CONTEXT PROTOCOL (MCP) SERVER            ")
            println("=================================================================")
            println("  Taiga API URL:       ${initialConfig.apiUrl}")
            println("  Default Project:     ${initialConfig.defaultProject ?: "<not configured>"}")
            println("  Auth Token:          ${if (!initialConfig.authToken.isNullOrBlank()) "Configured (masked)" else "<none>"}")
            println("-----------------------------------------------------------------")
            println("  Server listening:    http://$host:$port")
            println("  Swagger UI:          http://$host:$port/swagger")
            println("  OpenAPI Spec:        http://$host:$port/openapi.json")
            println("  MCP SSE Endpoint:    http://$host:$port/sse")
            println("  Config API:          http://$host:$port/api/config")
            println("  Healthcheck:         http://$host:$port/health")
            println("=================================================================")

            embeddedServer(CIO, host = host, port = port) {
                configureServer(
                    configManager = configManager,
                    mcpServer = mcpServer,
                    sessionManager = sessionManager,
                    toolRegistry = toolRegistry,
                    clientFactory = clientFactory
                )
            }.start(wait = true)
        }
    }
}

fun main(args: Array<String>) = TaigaMcpCommand().main(args)
