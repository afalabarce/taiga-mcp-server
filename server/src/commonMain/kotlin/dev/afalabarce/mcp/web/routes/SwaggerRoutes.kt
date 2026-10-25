package dev.afalabarce.mcp.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.principal
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import dev.afalabarce.mcp.mcp.tools.TaigaToolRegistry
import dev.afalabarce.mcp.taiga.client.TaigaClient
import dev.afalabarce.mcp.taiga.client.TaigaClientFactory
import dev.afalabarce.mcp.web.auth.TaigaUserPrincipal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Public documentation routes (Swagger UI and OpenAPI).
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.swaggerDocsRoutes() {
    // 1. Ktor Standard Swagger UI DSL using runtime OpenApiDocSource.Routing
    swaggerUI(path = "swagger") {
        source = OpenApiDocSource.Routing()
        info = OpenApiInfo(
            title = "Taiga MCP Server API",
            version = "1.0.0",
            description = "Model Context Protocol Server for Taiga project management. Uses Ktor native runtime OpenAPI route annotations via describe DSL."
        )
        tag("Configuration & Auth")
        tag("MCP Protocol")
        tag("MCP Tools Interactive Testing")
        tag("Taiga Proxy Testing")
        tag("Health")
        deepLinking = true
    }

    // 2. Direct OpenAPI JSON endpoint generated dynamically from the routing tree
    get("/openapi.json") {
        val routingSource = OpenApiDocSource.Routing()
        val baseDoc = OpenApiDoc(
            openapi = OpenApiDoc.OPENAPI_VERSION,
            info = OpenApiInfo(
                title = "Taiga MCP Server API",
                version = "1.0.0",
                description = "Model Context Protocol Server for Taiga project management."
            )
        )
        val text = routingSource.read(call.application, baseDoc)
        call.respondText(text.content, ContentType.Application.Json)
    }.describe {
        summary = "OpenAPI Specification Document"
        description = "Provides runtime OpenAPI 3.0 specification for this MCP server"
        tag("Health")
        responses {
            HttpStatusCode.OK {
                description = "OpenAPI specification document"
            }
        }
    }
}

/**
 * Protected MCP tool test and Taiga operational routes.
 * Protected with authenticate("auth-bearer").
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.mcpToolAndTaigaRoutes(
    toolRegistry: TaigaToolRegistry,
    clientFactory: TaigaClientFactory
) {
    // 1. MCP Tools discovery & interactive execution endpoints
    get("/api/mcp/tools") {
        val tools = toolRegistry.listToolDefinitions()
        call.respond(tools)
    }.describe {
        summary = "List all MCP tools (Protected)"
        description = "Requires Bearer authentication. Lists all registered MCP tools and their full JSON schema input definitions."
        tag("MCP Tools Interactive Testing")
        responses {
            HttpStatusCode.OK {
                description = "List of registered MCP tools"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }

    post("/api/mcp/tools/{toolName}/call") {
        val toolName = call.parameters["toolName"] ?: return@post call.respond(
            HttpStatusCode.BadRequest, "Missing toolName parameter"
        )
        val bodyText = call.receiveText()
        val args = if (bodyText.isNotBlank()) {
            TaigaClient.json.decodeFromString<JsonObject>(bodyText)
        } else {
            buildJsonObject {}
        }

        val result = toolRegistry.executeTool(toolName, args)
        if (result.isError) {
            call.respond(HttpStatusCode.BadRequest, result)
        } else {
            call.respond(result)
        }
    }.describe {
        summary = "Execute an MCP tool directly (Protected)"
        description = "Requires Bearer authentication. Allows testing any MCP tool (e.g. taiga_list_projects, taiga_list_user_stories, taiga_create_task) directly."
        tag("MCP Tools Interactive Testing")
        parameters {
            path("toolName") {
                description = "Name of the tool to execute"
            }
        }
        requestBody {
            description = "Arguments object matching the tool's JSON schema"
            required = false
            ContentType.Application.Json {
                schema = jsonSchema<JsonObject>()
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "Tool execution result content"
            }
            HttpStatusCode.BadRequest {
                description = "Execution error or tool not found"
            }
            HttpStatusCode.Unauthorized {
                description = "Unauthorized - Missing or invalid Bearer token"
            }
        }
    }

    // 2. Direct Taiga REST endpoints for testing
    get("/api/taiga/projects") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val projects = client.listProjects()
            call.respond(projects)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to list projects")))
        }
    }.describe {
        summary = "List projects directly (Protected)"
        description = "Requires Bearer authentication. Returns list of projects for the authenticated user"
        tag("Taiga Proxy Testing")
        responses {
            HttpStatusCode.OK { description = "List of projects" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    get("/api/taiga/userstories") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val milestoneId = call.request.queryParameters["milestone_id"]?.toLongOrNull()
            val statusId = call.request.queryParameters["status_id"]?.toLongOrNull()
            val epicId = call.request.queryParameters["epic_id"]?.toLongOrNull()
            val stories = client.listUserStories(milestoneId = milestoneId, statusId = statusId, epicId = epicId)
            call.respond(stories)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to list user stories")))
        }
    }.describe {
        summary = "List user stories directly (Protected)"
        description = "Requires Bearer authentication. Returns list of user stories with optional sprint/status/epic filters"
        tag("Taiga Proxy Testing")
        parameters {
            query("milestone_id") { description = "Filter by sprint / milestone ID" }
            query("status_id") { description = "Filter by story status ID" }
            query("epic_id") { description = "Filter by parent epic ID" }
        }
        responses {
            HttpStatusCode.OK { description = "List of user stories" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    post("/api/taiga/userstories") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val body = call.receive<JsonObject>()
            val story = client.createUserStory(body)
            call.respond(story)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to create user story")))
        }
    }.describe {
        summary = "Create user story directly (Protected)"
        description = "Requires Bearer authentication. Creates a new user story"
        tag("Taiga Proxy Testing")
        requestBody {
            description = "User story payload"
            required = true
            ContentType.Application.Json {
                schema = jsonSchema<JsonObject>()
            }
        }
        responses {
            HttpStatusCode.OK { description = "Created user story" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    get("/api/taiga/tasks") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val storyId = call.request.queryParameters["user_story_id"]?.toLongOrNull()
            val milestoneId = call.request.queryParameters["milestone_id"]?.toLongOrNull()
            val statusId = call.request.queryParameters["status_id"]?.toLongOrNull()
            val tasks = client.listTasks(userStoryId = storyId, milestoneId = milestoneId, statusId = statusId)
            call.respond(tasks)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to list tasks")))
        }
    }.describe {
        summary = "List tasks directly (Protected)"
        description = "Requires Bearer authentication. Returns list of tasks with filters"
        tag("Taiga Proxy Testing")
        parameters {
            query("user_story_id") { description = "Filter by parent story ID" }
            query("milestone_id") { description = "Filter by milestone ID" }
            query("status_id") { description = "Filter by status ID" }
        }
        responses {
            HttpStatusCode.OK { description = "List of tasks" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    post("/api/taiga/tasks") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val body = call.receive<JsonObject>()
            val task = client.createTask(body)
            call.respond(task)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to create task")))
        }
    }.describe {
        summary = "Create task directly (Protected)"
        description = "Requires Bearer authentication. Creates a new task in Taiga"
        tag("Taiga Proxy Testing")
        requestBody {
            description = "Task payload"
            required = true
            ContentType.Application.Json {
                schema = jsonSchema<JsonObject>()
            }
        }
        responses {
            HttpStatusCode.OK { description = "Created task" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    get("/api/taiga/issues") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val statusId = call.request.queryParameters["status_id"]?.toLongOrNull()
            val priorityId = call.request.queryParameters["priority_id"]?.toLongOrNull()
            val severityId = call.request.queryParameters["severity_id"]?.toLongOrNull()
            val issues = client.listIssues(statusId = statusId, priorityId = priorityId, severityId = severityId)
            call.respond(issues)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to list issues")))
        }
    }.describe {
        summary = "List issues directly (Protected)"
        description = "Requires Bearer authentication. Returns list of issues with filters"
        tag("Taiga Proxy Testing")
        parameters {
            query("status_id") { description = "Filter by status ID" }
            query("priority_id") { description = "Filter by priority ID" }
            query("severity_id") { description = "Filter by severity ID" }
        }
        responses {
            HttpStatusCode.OK { description = "List of issues" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    get("/api/taiga/epics") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val epics = client.listEpics()
            call.respond(epics)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to list epics")))
        }
    }.describe {
        summary = "List epics directly (Protected)"
        description = "Requires Bearer authentication. Returns list of epics for the working project"
        tag("Taiga Proxy Testing")
        responses {
            HttpStatusCode.OK { description = "List of epics" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    get("/api/taiga/milestones") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val milestones = client.listMilestones()
            call.respond(milestones)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to list milestones")))
        }
    }.describe {
        summary = "List milestones (sprints) directly (Protected)"
        description = "Requires Bearer authentication. Returns list of sprints and their story points"
        tag("Taiga Proxy Testing")
        responses {
            HttpStatusCode.OK { description = "List of milestones" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }

    get("/api/taiga/statuses") {
        try {
            val userToken = call.principal<TaigaUserPrincipal>()?.token
            val client = clientFactory.getClient(overrideAuthToken = userToken)
            val statuses = client.getAllProjectStatuses()
            call.respond(statuses)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to get project statuses")))
        }
    }.describe {
        summary = "List all project statuses and metadata (Protected)"
        description = "Requires Bearer authentication. Returns all valid status, priority, severity, and type IDs"
        tag("Taiga Proxy Testing")
        responses {
            HttpStatusCode.OK { description = "Statuses metadata" }
            HttpStatusCode.Unauthorized { description = "Unauthorized - Missing or invalid Bearer token" }
        }
    }
}
