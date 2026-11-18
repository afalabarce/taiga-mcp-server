package dev.afalabarce.mcp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.ServerOptsParser
import dev.afalabarce.mcp.config.TaigaConfig
import dev.afalabarce.mcp.config.getEnv
import dev.afalabarce.mcp.mcp.server.McpServer
import dev.afalabarce.mcp.mcp.server.McpSessionManager
import dev.afalabarce.mcp.mcp.server.McpStdioHandler
import dev.afalabarce.mcp.mcp.tools.TaigaToolRegistry
import dev.afalabarce.mcp.taiga.client.TaigaClientFactory
import dev.afalabarce.mcp.web.auth.TaigaBasicAuthManager
import dev.afalabarce.mcp.web.auth.parseBasicCredentials
import dev.afalabarce.mcp.web.configureServer
import kotlinx.coroutines.runBlocking

class TaigaMcpCommand : CliktCommand(
    name = "taiga-mcpserver"
) {
    override fun help(context: com.github.ajalt.clikt.core.Context): String =
        "Taiga Model Context Protocol (MCP) Server with remote API parameterization, SSE and Swagger UI"

    private val hostOption: String? by option(
        "--host", "-h",
        help = "Host interface to bind HTTP server (default: 0.0.0.0)"
    )

    private val portOption: Int? by option(
        "--port", "-p",
        help = "Port to listen on (default: 8080 or PORT env var)"
    ).int()

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

    private val taigaBasic: String? by option(
        "--taiga-basic",
        help = "Taiga Basic Auth credentials as Base64 'username:password' or 'Basic <base64>' (default: TAIGA_BASIC_AUTH)"
    )

    private val taigaUsername: String? by option(
        "--taiga-username", "--taiga-user",
        help = "Taiga username or email for Basic Auth (default: TAIGA_USERNAME or TAIGA_USER)"
    )

    private val taigaPassword: String? by option(
        "--taiga-password",
        help = "Taiga password for Basic Auth (default: TAIGA_PASSWORD)"
    )

    private val taigaProject: String? by option(
        "--taiga-project",
        help = "Default Taiga Project slug or numeric ID (default: TAIGA_DEFAULT_PROJECT)"
    )

    private val queryOption: String? by option(
        "--query", "-q",
        help = "Execute a one-shot natural language query against Taiga and print the result directly (no scripts or bridge needed)"
    )

    private val formatOption: String? by option(
        "--format", "-f",
        help = "Output format when using --query ('table' or 'json', default: 'table')"
    )

    override fun run() {
        // 1. Resolve configuration files via SERVER_OPTS, system properties, environment, and conventions
        val resolvedConfig = ServerOptsParser.resolve()
        val yaml = resolvedConfig.yamlProperties

        // 2. Resolve Host & Port with priority: CLI option > direct env var > application.yaml > default
        val host = hostOption
            ?: getEnv("TAIGA_HOST")
            ?: getEnv("HOST")
            ?: yaml["server.host"]
            ?: "0.0.0.0"

        val port = portOption
            ?: getEnv("TAIGA_PORT")?.toIntOrNull()
            ?: getEnv("PORT")?.toIntOrNull()
            ?: yaml["server.port"]?.toIntOrNull()
            ?: 8080

        // 3. Resolve Taiga configuration with priority: CLI option > direct env var > application.yaml > default
        val resolvedApiUrl = taigaUrl?.ifBlank { null }
            ?: getEnv("TAIGA_API_URL")
            ?: yaml["taiga.apiUrl"]
            ?: yaml["taiga.api_url"]
            ?: "https://api.taiga.io"

        var resolvedAuthToken = taigaToken?.ifBlank { null }
            ?: getEnv("TAIGA_AUTH_TOKEN")?.ifBlank { null }
            ?: yaml["taiga.authToken"]?.ifBlank { null }
            ?: yaml["taiga.auth_token"]?.ifBlank { null }

        val resolvedUsername = taigaUsername?.ifBlank { null }
            ?: getEnv("TAIGA_USERNAME")?.ifBlank { null }
            ?: getEnv("TAIGA_USER")?.ifBlank { null }
            ?: yaml["taiga.username"]?.ifBlank { null }
            ?: yaml["taiga.user"]?.ifBlank { null }

        val resolvedPassword = taigaPassword?.ifBlank { null }
            ?: getEnv("TAIGA_PASSWORD")?.ifBlank { null }
            ?: yaml["taiga.password"]?.ifBlank { null }

        val resolvedBasicRaw = taigaBasic?.ifBlank { null }
            ?: getEnv("TAIGA_BASIC_AUTH")?.ifBlank { null }
            ?: yaml["taiga.basicAuth"]?.ifBlank { null }
            ?: yaml["taiga.basic_auth"]?.ifBlank { null }
            ?: yaml["taiga.basic"]?.ifBlank { null }

        val hasExplicitCliBasic = !taigaBasic.isNullOrBlank() ||
            (!taigaUsername.isNullOrBlank() && !taigaPassword.isNullOrBlank())

        var basicCredentials: Pair<String, String>? = when {
            resolvedUsername != null && resolvedPassword != null -> Pair(resolvedUsername, resolvedPassword)
            resolvedBasicRaw != null -> {
                parseBasicCredentials(resolvedBasicRaw).also {
                    if (it == null) {
                        System.err.println("Warning: Invalid Base64 Basic Auth credentials provided via --taiga-basic / TAIGA_BASIC_AUTH.")
                    }
                }
            }
            else -> null
        }

        if (hasExplicitCliBasic && taigaToken.isNullOrBlank()) {
            resolvedAuthToken = null
        }

        if (resolvedAuthToken != null && resolvedAuthToken.startsWith("Basic ", ignoreCase = true)) {
            val parsedFromToken = parseBasicCredentials(resolvedAuthToken)
            if (parsedFromToken != null) {
                if (basicCredentials == null) {
                    basicCredentials = parsedFromToken
                }
                resolvedAuthToken = null
            }
        }

        val resolvedDefaultProject = taigaProject?.ifBlank { null }
            ?: getEnv("TAIGA_DEFAULT_PROJECT")
            ?: yaml["taiga.defaultProject"]
            ?: yaml["taiga.default_project"]
            ?: yaml["taiga.project"]

        val initialConfig = TaigaConfig(
            apiUrl = resolvedApiUrl,
            authToken = resolvedAuthToken,
            defaultProject = resolvedDefaultProject
        )

        val configManager = ConfigManager(initialConfig)
        val clientFactory = TaigaClientFactory(configManager)
        val basicAuthManager = TaigaBasicAuthManager(configManager, clientFactory)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        if (!queryOption.isNullOrBlank()) {
            val format = formatOption?.lowercase() ?: "table"
            runBlocking {
                val queryAuthToken = initialConfig.authToken
                    ?: basicCredentials?.let { basicAuthManager.authenticate(it.first, it.second, initialConfig.apiUrl) }
                val client = clientFactory.getClient(
                    overrideApiUrl = initialConfig.apiUrl,
                    overrideAuthToken = queryAuthToken,
                    overrideProject = initialConfig.defaultProject
                )
                val projectId = client.resolveProjectId(initialConfig.defaultProject)
                val result = dev.afalabarce.mcp.mcp.tools.TaigaNaturalQueryEngine.executeQuery(
                    client = client,
                    projectId = projectId,
                    query = queryOption!!
                )
                if (format == "json") {
                    println(dev.afalabarce.mcp.taiga.client.TaigaClient.json.encodeToString(
                        dev.afalabarce.mcp.mcp.tools.NaturalQueryResult.serializer(),
                        result
                    ))
                } else {
                    println(result.markdownTable)
                }
            }
            return
        }

        if (stdio) {
            // MCP Stdio transport mode: redirect System.out to System.err so logs never corrupt JSON-RPC stdout
            val originalOut = System.out
            System.setOut(System.err)
            val authModeLabel = when {
                !initialConfig.authToken.isNullOrBlank() -> "Bearer Token"
                basicCredentials != null -> "Basic Auth (user: ${basicCredentials.first})"
                else -> "None (use taiga_login or taiga_set_config)"
            }
            System.err.println("Starting Taiga MCP server in stdio mode (Auth: $authModeLabel)...")
            val stdioHandler = McpStdioHandler(
                mcpServer = mcpServer,
                output = originalOut,
                basicAuthManager = basicAuthManager,
                basicUsername = basicCredentials?.first,
                basicPassword = basicCredentials?.second
            )
            runBlocking {
                stdioHandler.run()
            }
            kotlin.system.exitProcess(0)
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
            println("  SERVER_OPTS:         ${resolvedConfig.serverOptsRaw ?: "<not set>"}")
            println("  application.yaml:    ${resolvedConfig.applicationYamlPath ?: "<not found / default>"}")
            println("  logback.xml:         ${resolvedConfig.logbackXmlPath ?: "<classpath default>"}")
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
