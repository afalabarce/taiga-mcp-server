package dev.afalabarce.mcp

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.TaigaConfig
import dev.afalabarce.mcp.config.TaigaConfigUpdateRequest
import dev.afalabarce.mcp.mcp.protocol.JsonRpcRequest
import dev.afalabarce.mcp.mcp.server.McpServer
import dev.afalabarce.mcp.mcp.tools.TaigaToolRegistry
import dev.afalabarce.mcp.taiga.client.TaigaClient
import dev.afalabarce.mcp.taiga.client.TaigaClientFactory
import dev.afalabarce.mcp.web.configureServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaigaMcpServerTest {

    @Test
    fun testConfigManagerDefaultsAndMasking() = runBlocking {
        val initialConfig = TaigaConfig(
            apiUrl = "https://api.taiga.io",
            authToken = "super_secret_token_12345",
            defaultProject = "sample-project"
        )
        val manager = ConfigManager(initialConfig)
        val config = manager.getConfig()

        assertEquals("https://api.taiga.io", config.apiUrl)
        assertEquals("super_secret_token_12345", config.authToken)
        assertEquals("sample-project", config.defaultProject)

        val masked = config.masked()
        assertTrue(masked.hasAuthToken)
        assertFalse(masked.maskedToken!!.contains("secret"))
        assertEquals("supe...2345", masked.maskedToken)
    }

    @Test
    fun testConfigManagerRemoteUpdate() = runBlocking {
        val manager = ConfigManager(TaigaConfig(apiUrl = "https://default.url"))
        val update = TaigaConfigUpdateRequest(
            apiUrl = "https://custom-taiga.example.com",
            authToken = "my-new-token-abc",
            defaultProject = "project-alpha"
        )
        val updated = manager.updateConfig(update)

        assertEquals("https://custom-taiga.example.com", updated.apiUrl)
        assertEquals("my-new-token-abc", updated.authToken)
        assertEquals("project-alpha", updated.defaultProject)
    }

    @Test
    fun testMcpServerInitialization() = runBlocking {
        val configManager = ConfigManager()
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        val initRequest = JsonRpcRequest(
            id = JsonPrimitive(1),
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", JsonPrimitive("2024-11-05"))
            }
        )

        val response = mcpServer.handleRequest(initRequest)
        assertNotNull(response)
        assertEquals(JsonPrimitive(1), response.id)
        assertNull(response.error)
        assertNotNull(response.result)

        val resultObj = response.result!!.jsonObject
        assertEquals("2024-11-05", resultObj["protocolVersion"]?.jsonPrimitive?.content)
        val serverInfo = resultObj["serverInfo"]?.jsonObject
        assertEquals("taiga-mcpserver", serverInfo?.get("name")?.jsonPrimitive?.content)
    }

    @Test
    fun testToolsListContainsAllTaigaTools() = runBlocking {
        val configManager = ConfigManager()
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        val toolsListRequest = JsonRpcRequest(
            id = JsonPrimitive(2),
            method = "tools/list"
        )

        val response = mcpServer.handleRequest(toolsListRequest)
        assertNotNull(response)
        assertNull(response.error)

        val tools = response.result!!.jsonObject["tools"]!!.jsonArray
        val toolNames = tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()

        // Projects
        assertTrue(toolNames.contains("taiga_list_projects"))
        assertTrue(toolNames.contains("taiga_get_project"))

        // User Stories
        assertTrue(toolNames.contains("taiga_list_user_stories"))
        assertTrue(toolNames.contains("taiga_get_user_story"))
        assertTrue(toolNames.contains("taiga_create_user_story"))
        assertTrue(toolNames.contains("taiga_update_user_story"))
        assertTrue(toolNames.contains("taiga_delete_user_story"))

        // Tasks
        assertTrue(toolNames.contains("taiga_list_tasks"))
        assertTrue(toolNames.contains("taiga_get_task"))
        assertTrue(toolNames.contains("taiga_create_task"))
        assertTrue(toolNames.contains("taiga_update_task"))
        assertTrue(toolNames.contains("taiga_delete_task"))

        // Epics
        assertTrue(toolNames.contains("taiga_list_epics"))
        assertTrue(toolNames.contains("taiga_get_epic"))
        assertTrue(toolNames.contains("taiga_create_epic"))
        assertTrue(toolNames.contains("taiga_update_epic"))
        assertTrue(toolNames.contains("taiga_delete_epic"))

        // Issues
        assertTrue(toolNames.contains("taiga_list_issues"))
        assertTrue(toolNames.contains("taiga_get_issue"))
        assertTrue(toolNames.contains("taiga_create_issue"))
        assertTrue(toolNames.contains("taiga_update_issue"))
        assertTrue(toolNames.contains("taiga_delete_issue"))

        // Milestones
        assertTrue(toolNames.contains("taiga_list_milestones"))
        assertTrue(toolNames.contains("taiga_get_milestone"))
        assertTrue(toolNames.contains("taiga_create_milestone"))

        // Wiki
        assertTrue(toolNames.contains("taiga_list_wiki_pages"))
        assertTrue(toolNames.contains("taiga_get_wiki_page"))

        // Metadata
        assertTrue(toolNames.contains("taiga_get_project_statuses"))

        // Config & Auth
        assertTrue(toolNames.contains("taiga_get_config"))
        assertTrue(toolNames.contains("taiga_set_config"))
        assertTrue(toolNames.contains("taiga_login"))
        assertTrue(toolNames.contains("taiga_test_connection"))
    }

    @Test
    fun testExecuteConfigTool() = runBlocking {
        val configManager = ConfigManager(
            TaigaConfig(
                apiUrl = "https://custom.taiga.internal",
                authToken = "secret_auth_token_999",
                defaultProject = "core-backend"
            )
        )
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        // Call taiga_get_config
        val callRequest = JsonRpcRequest(
            id = JsonPrimitive(3),
            method = "tools/call",
            params = buildJsonObject {
                put("name", JsonPrimitive("taiga_get_config"))
                put("arguments", buildJsonObject {})
            }
        )

        val response = mcpServer.handleRequest(callRequest)
        assertNotNull(response)
        assertNull(response.error)

        val content = response.result!!.jsonObject["content"]!!.jsonArray
        val text = content[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("https://custom.taiga.internal"))
        assertTrue(text.contains("core-backend"))
        // Check that auth token is masked
        assertFalse(text.contains("secret_auth_token_999"))
    }

    @Test
    fun testUpdateConfigViaTool() = runBlocking {
        val configManager = ConfigManager(TaigaConfig(apiUrl = "https://initial.taiga"))
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        val setConfigRequest = JsonRpcRequest(
            id = JsonPrimitive(4),
            method = "tools/call",
            params = buildJsonObject {
                put("name", JsonPrimitive("taiga_set_config"))
                put("arguments", buildJsonObject {
                    put("api_url", JsonPrimitive("https://updated.taiga.internal"))
                    put("auth_token", JsonPrimitive("updated_secret_token_12345"))
                    put("default_project", JsonPrimitive("new-project-slug"))
                })
            }
        )

        val response = mcpServer.handleRequest(setConfigRequest)
        assertNotNull(response)
        assertNull(response.error)

        val config = configManager.getConfig()
        assertEquals("https://updated.taiga.internal", config.apiUrl)
        assertEquals("updated_secret_token_12345", config.authToken)
        assertEquals("new-project-slug", config.defaultProject)
    }

    @Test
    fun testPromptsListAndGet() = runBlocking {
        val configManager = ConfigManager()
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        val listPromptsReq = JsonRpcRequest(id = JsonPrimitive(5), method = "prompts/list")
        val listResp = mcpServer.handleRequest(listPromptsReq)
        assertNotNull(listResp)
        val prompts = listResp.result!!.jsonObject["prompts"]!!.jsonArray
        assertTrue(prompts.any { it.jsonObject["name"]!!.jsonPrimitive.content == "summarize-sprint" })
        assertTrue(prompts.any { it.jsonObject["name"]!!.jsonPrimitive.content == "plan-user-story" })

        val getPromptReq = JsonRpcRequest(
            id = JsonPrimitive(6),
            method = "prompts/get",
            params = buildJsonObject {
                put("name", JsonPrimitive("summarize-sprint"))
                put("arguments", buildJsonObject {
                    put("milestone_id", JsonPrimitive("42"))
                })
            }
        )
        val getResp = mcpServer.handleRequest(getPromptReq)
        assertNotNull(getResp)
        val messages = getResp.result!!.jsonObject["messages"]!!.jsonArray
        val text = messages[0].jsonObject["content"]!!.jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("milestone #42"))
    }

    @Test
    fun testResourcesListAndRead() = runBlocking {
        val configManager = ConfigManager(TaigaConfig(apiUrl = "https://resource-test.taiga"))
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        val listResReq = JsonRpcRequest(id = JsonPrimitive(7), method = "resources/list")
        val listResp = mcpServer.handleRequest(listResReq)
        assertNotNull(listResp)
        val resources = listResp.result!!.jsonObject["resources"]!!.jsonArray
        assertEquals("taiga://config", resources[0].jsonObject["uri"]!!.jsonPrimitive.content)

        val readResReq = JsonRpcRequest(
            id = JsonPrimitive(8),
            method = "resources/read",
            params = buildJsonObject {
                put("uri", JsonPrimitive("taiga://config"))
            }
        )
        val readResp = mcpServer.handleRequest(readResReq)
        assertNotNull(readResp)
        val contents = readResp.result!!.jsonObject["contents"]!!.jsonArray
        val text = contents[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("https://resource-test.taiga"))
    }

    @OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)
    @Test
    fun testKtorRouteDescribeExtension() = io.ktor.server.testing.testApplication {
        routing {
            val route = get("/test-describe") {
                // handler
            }.describe {
                summary = "Test summary"
                description = "Test description"
                tag("Testing")
            }

            val descriptions = route.attributes.getOrNull(io.ktor.server.routing.openapi.OperationDescribeAttributeKey)
            assertNotNull(descriptions)
            assertEquals(1, descriptions.size)
        }
    }

    @Test
    fun testAuthenticationProtectionOnMcpRoutes() = io.ktor.server.testing.testApplication {
        val testConfig = TaigaConfig(
            apiUrl = "https://api.taiga.io",
            authToken = "valid_secret_token_12345"
        )
        val configManager = ConfigManager(testConfig)
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)
        val sessionManager = dev.afalabarce.mcp.mcp.server.McpSessionManager(mcpServer)

        application {
            configureServer(
                configManager = configManager,
                mcpServer = mcpServer,
                sessionManager = sessionManager,
                toolRegistry = toolRegistry,
                clientFactory = clientFactory
            )
        }

        // 1. Public route /health should be accessible without auth
        val healthResp = client.get("/health")
        println("HEALTH STATUS: ${healthResp.status}, BODY: ${healthResp.bodyAsText()}")
        assertEquals(HttpStatusCode.OK, healthResp.status)

        // 2. Protected route /api/config without auth header should return 401 Unauthorized
        val unauthConfigResp = client.get("/api/config")
        assertEquals(HttpStatusCode.Unauthorized, unauthConfigResp.status)

        // 3. Protected route /api/config with Bearer token should return 200 OK
        val authConfigResp = client.get("/api/config") {
            header(HttpHeaders.Authorization, "Bearer valid_secret_token_12345")
        }
        assertEquals(HttpStatusCode.OK, authConfigResp.status)

        // 4. Protected route /mcp without auth should return 401 Unauthorized
        val unauthMcpResp = client.post("/mcp") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthMcpResp.status)

        // 5. Protected route /mcp with Bearer token should return 200 OK
        val authMcpResp = client.post("/mcp") {
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, "Bearer valid_secret_token_12345")
            setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")
        }
        assertEquals(HttpStatusCode.OK, authMcpResp.status)
    }

    @Test
    fun testOpenApiSpecificationStrictValidation() = io.ktor.server.testing.testApplication {
        val configManager = ConfigManager(TaigaConfig())
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)
        val sessionManager = dev.afalabarce.mcp.mcp.server.McpSessionManager(mcpServer)

        application {
            configureServer(
                configManager = configManager,
                mcpServer = mcpServer,
                sessionManager = sessionManager,
                toolRegistry = toolRegistry,
                clientFactory = clientFactory
            )
        }

        val openApiResp = client.get("/openapi.json")
        assertEquals(HttpStatusCode.OK, openApiResp.status)
        val jsonText = openApiResp.bodyAsText()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(jsonText).jsonObject
        val paths = json["paths"]!!.jsonObject

        for ((path, pathItem) in paths) {
            for ((method, op) in pathItem.jsonObject) {
                if (method in listOf("get", "post", "put", "delete", "patch")) {
                    val opObj = op.jsonObject
                    assertTrue(opObj.containsKey("responses"), "Operation $method $path MUST have 'responses'")
                    if (opObj.containsKey("requestBody")) {
                        val reqBody = opObj["requestBody"]!!.jsonObject
                        assertTrue(reqBody.containsKey("content"), "Operation $method $path requestBody MUST have 'content'")
                    }
                }
            }
        }
    }

    @Test
    fun testUnknownMethodReturnsError() = runBlocking {
        val configManager = ConfigManager()
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)

        val request = JsonRpcRequest(
            id = JsonPrimitive(99),
            method = "unknown/nonexistent"
        )

        val response = mcpServer.handleRequest(request)
        assertNotNull(response)
        assertNotNull(response.error)
        assertEquals(-32601, response.error!!.code)
    }

    @Test
    fun testStreamableHttpPostOnSseAndMcp() = io.ktor.server.testing.testApplication {
        val configManager = ConfigManager(TaigaConfig(authToken = "test_valid_token"))
        val clientFactory = TaigaClientFactory(configManager)
        val toolRegistry = TaigaToolRegistry(configManager, clientFactory)
        val mcpServer = McpServer(toolRegistry, configManager)
        val sessionManager = dev.afalabarce.mcp.mcp.server.McpSessionManager(mcpServer)

        application {
            configureServer(
                configManager = configManager,
                mcpServer = mcpServer,
                sessionManager = sessionManager,
                toolRegistry = toolRegistry,
                clientFactory = clientFactory
            )
        }

        val initPayload = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}"""

        // Test POST /mcp with Bearer token
        val mcpResp = client.post("/mcp") {
            header(HttpHeaders.Authorization, "Bearer test_valid_token")
            header(HttpHeaders.ContentType, "application/json")
            header("Mcp-Session-Id", "session-xyz")
            setBody(initPayload)
        }
        assertEquals(HttpStatusCode.OK, mcpResp.status)
        assertEquals("session-xyz", mcpResp.headers["Mcp-Session-Id"])
        assertTrue(mcpResp.bodyAsText().contains("taiga-mcpserver"))

        // Test POST /sse (Streamable HTTP fallback - ensures no 405 Method Not Allowed)
        val sseResp = client.post("/sse") {
            header(HttpHeaders.Authorization, "Bearer test_valid_token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(initPayload)
        }
        assertEquals(HttpStatusCode.OK, sseResp.status)
        assertTrue(sseResp.bodyAsText().contains("taiga-mcpserver"))

        // Test Batch JSON-RPC over POST /mcp
        val batchPayload = """[{"jsonrpc":"2.0","id":2,"method":"ping"},{"jsonrpc":"2.0","id":3,"method":"tools/list"}]"""
        val batchResp = client.post("/mcp") {
            header(HttpHeaders.Authorization, "Bearer test_valid_token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(batchPayload)
        }
        assertEquals(HttpStatusCode.OK, batchResp.status)
        val batchBody = batchResp.bodyAsText()
        assertTrue(batchBody.startsWith("["))
        assertTrue(batchBody.contains("taiga_list_projects"))
    }
}
