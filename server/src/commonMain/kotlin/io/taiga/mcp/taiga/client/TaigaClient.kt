package io.taiga.mcp.taiga.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.taiga.mcp.config.TaigaConfig
import io.taiga.mcp.taiga.model.TaigaAuthResponse
import io.taiga.mcp.taiga.model.TaigaEpic
import io.taiga.mcp.taiga.model.TaigaIssue
import io.taiga.mcp.taiga.model.TaigaIssueType
import io.taiga.mcp.taiga.model.TaigaMilestone
import io.taiga.mcp.taiga.model.TaigaPriority
import io.taiga.mcp.taiga.model.TaigaProject
import io.taiga.mcp.taiga.model.TaigaProjectStatuses
import io.taiga.mcp.taiga.model.TaigaSeverity
import io.taiga.mcp.taiga.model.TaigaStatus
import io.taiga.mcp.taiga.model.TaigaTask
import io.taiga.mcp.taiga.model.TaigaUser
import io.taiga.mcp.taiga.model.TaigaUserStory
import io.taiga.mcp.taiga.model.TaigaWikiPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TaigaClient(
    val config: TaigaConfig,
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun createDefaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    private val baseUrl: String = config.normalizedApiUrl()

    private fun authenticatedRequest(builder: io.ktor.client.request.HttpRequestBuilder) {
        config.authToken?.let { token ->
            builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun handleResponse(response: HttpResponse): String {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw TaigaApiException(
                statusCode = response.status.value,
                message = "Taiga API error (${response.status.value}): $text"
            )
        }
        return text
    }

    suspend fun login(username: String, password: String, type: String = "normal"): TaigaAuthResponse {
        val payload = buildJsonObject {
            put("type", type)
            put("username", username)
            put("password", password)
        }
        val response = httpClient.post("$baseUrl/api/v1/auth") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaAuthResponse>(text)
    }

    suspend fun getMe(): TaigaUser {
        val response = httpClient.get("$baseUrl/api/v1/users/me") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUser>(text)
    }

    suspend fun listProjects(memberId: Long? = null): List<TaigaProject> {
        val response = httpClient.get("$baseUrl/api/v1/projects") {
            authenticatedRequest(this)
            memberId?.let { parameter("member", it) }
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaProject>>(text)
    }

    suspend fun getProject(id: Long): TaigaProject {
        val response = httpClient.get("$baseUrl/api/v1/projects/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaProject>(text)
    }

    suspend fun getProjectBySlug(slug: String): TaigaProject {
        val response = httpClient.get("$baseUrl/api/v1/projects/by_slug") {
            authenticatedRequest(this)
            parameter("slug", slug)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaProject>(text)
    }

    suspend fun resolveProjectId(projectIdentifier: String? = null): Long {
        val target = projectIdentifier?.ifBlank { null }
            ?: config.defaultProject?.ifBlank { null }
            ?: throw IllegalArgumentException("No project specified and no default project configured")

        return target.toLongOrNull() ?: run {
            val project = getProjectBySlug(target)
            project.id
        }
    }

    // --- Epics ---
    suspend fun listEpics(projectId: Long? = null, statusId: Long? = null): List<TaigaEpic> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/epics") {
            authenticatedRequest(this)
            parameter("project", pid)
            statusId?.let { parameter("status", it) }
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaEpic>>(text)
    }

    suspend fun getEpic(id: Long): TaigaEpic {
        val response = httpClient.get("$baseUrl/api/v1/epics/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaEpic>(text)
    }

    suspend fun createEpic(data: JsonObject): TaigaEpic {
        val response = httpClient.post("$baseUrl/api/v1/epics") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaEpic>(text)
    }

    suspend fun updateEpic(id: Long, data: JsonObject): TaigaEpic {
        val response = httpClient.patch("$baseUrl/api/v1/epics/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaEpic>(text)
    }

    suspend fun deleteEpic(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/epics/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- User Stories ---
    suspend fun listUserStories(
        projectId: Long? = null,
        milestoneId: Long? = null,
        statusId: Long? = null,
        epicId: Long? = null
    ): List<TaigaUserStory> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/userstories") {
            authenticatedRequest(this)
            parameter("project", pid)
            milestoneId?.let { parameter("milestone", it) }
            statusId?.let { parameter("status", it) }
            epicId?.let { parameter("epic", it) }
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaUserStory>>(text)
    }

    suspend fun getUserStory(id: Long): TaigaUserStory {
        val response = httpClient.get("$baseUrl/api/v1/userstories/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUserStory>(text)
    }

    suspend fun createUserStory(data: JsonObject): TaigaUserStory {
        val response = httpClient.post("$baseUrl/api/v1/userstories") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUserStory>(text)
    }

    suspend fun updateUserStory(id: Long, data: JsonObject): TaigaUserStory {
        val response = httpClient.patch("$baseUrl/api/v1/userstories/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUserStory>(text)
    }

    suspend fun deleteUserStory(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/userstories/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Tasks ---
    suspend fun listTasks(
        projectId: Long? = null,
        userStoryId: Long? = null,
        milestoneId: Long? = null,
        statusId: Long? = null,
        assignedToId: Long? = null
    ): List<TaigaTask> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/tasks") {
            authenticatedRequest(this)
            parameter("project", pid)
            userStoryId?.let { parameter("user_story", it) }
            milestoneId?.let { parameter("milestone", it) }
            statusId?.let { parameter("status", it) }
            assignedToId?.let { parameter("assigned_to", it) }
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaTask>>(text)
    }

    suspend fun getTask(id: Long): TaigaTask {
        val response = httpClient.get("$baseUrl/api/v1/tasks/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaTask>(text)
    }

    suspend fun createTask(data: JsonObject): TaigaTask {
        val response = httpClient.post("$baseUrl/api/v1/tasks") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaTask>(text)
    }

    suspend fun updateTask(id: Long, data: JsonObject): TaigaTask {
        val response = httpClient.patch("$baseUrl/api/v1/tasks/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaTask>(text)
    }

    suspend fun deleteTask(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/tasks/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Issues ---
    suspend fun listIssues(
        projectId: Long? = null,
        statusId: Long? = null,
        priorityId: Long? = null,
        severityId: Long? = null,
        typeId: Long? = null
    ): List<TaigaIssue> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/issues") {
            authenticatedRequest(this)
            parameter("project", pid)
            statusId?.let { parameter("status", it) }
            priorityId?.let { parameter("priority", it) }
            severityId?.let { parameter("severity", it) }
            typeId?.let { parameter("type", it) }
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaIssue>>(text)
    }

    suspend fun getIssue(id: Long): TaigaIssue {
        val response = httpClient.get("$baseUrl/api/v1/issues/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaIssue>(text)
    }

    suspend fun createIssue(data: JsonObject): TaigaIssue {
        val response = httpClient.post("$baseUrl/api/v1/issues") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaIssue>(text)
    }

    suspend fun updateIssue(id: Long, data: JsonObject): TaigaIssue {
        val response = httpClient.patch("$baseUrl/api/v1/issues/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaIssue>(text)
    }

    suspend fun deleteIssue(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/issues/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Milestones (Sprints) ---
    suspend fun listMilestones(projectId: Long? = null): List<TaigaMilestone> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/milestones") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaMilestone>>(text)
    }

    suspend fun getMilestone(id: Long): TaigaMilestone {
        val response = httpClient.get("$baseUrl/api/v1/milestones/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaMilestone>(text)
    }

    suspend fun createMilestone(data: JsonObject): TaigaMilestone {
        val response = httpClient.post("$baseUrl/api/v1/milestones") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaMilestone>(text)
    }

    // --- Wiki ---
    suspend fun listWikiPages(projectId: Long? = null): List<TaigaWikiPage> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/wiki") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaWikiPage>>(text)
    }

    suspend fun getWikiPageBySlug(projectId: Long? = null, slug: String): TaigaWikiPage {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/wiki/by_slug") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("slug", slug)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWikiPage>(text)
    }

    suspend fun createWikiPage(data: JsonObject): TaigaWikiPage {
        val response = httpClient.post("$baseUrl/api/v1/wiki") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWikiPage>(text)
    }

    suspend fun updateWikiPage(id: Long, data: JsonObject): TaigaWikiPage {
        val response = httpClient.patch("$baseUrl/api/v1/wiki/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWikiPage>(text)
    }

    // --- Project Statuses / Metadata ---
    suspend fun getUserStoryStatuses(projectId: Long? = null): List<TaigaStatus> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/userstory-statuses") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaStatus>>(text)
    }

    suspend fun getTaskStatuses(projectId: Long? = null): List<TaigaStatus> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/task-statuses") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaStatus>>(text)
    }

    suspend fun getIssueStatuses(projectId: Long? = null): List<TaigaStatus> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/issue-statuses") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaStatus>>(text)
    }

    suspend fun getPriorities(projectId: Long? = null): List<TaigaPriority> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/priorities") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaPriority>>(text)
    }

    suspend fun getSeverities(projectId: Long? = null): List<TaigaSeverity> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/severities") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaSeverity>>(text)
    }

    suspend fun getIssueTypes(projectId: Long? = null): List<TaigaIssueType> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/issue-types") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaIssueType>>(text)
    }

    suspend fun getAllProjectStatuses(projectId: Long? = null): TaigaProjectStatuses {
        val pid = projectId ?: resolveProjectId()
        return TaigaProjectStatuses(
            projectId = pid,
            userStoryStatuses = getUserStoryStatuses(pid),
            taskStatuses = getTaskStatuses(pid),
            issueStatuses = getIssueStatuses(pid),
            priorities = getPriorities(pid),
            severities = getSeverities(pid),
            issueTypes = getIssueTypes(pid)
        )
    }
}

class TaigaApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)
