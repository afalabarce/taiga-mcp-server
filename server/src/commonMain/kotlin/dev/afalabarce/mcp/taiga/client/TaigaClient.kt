package dev.afalabarce.mcp.taiga.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import dev.afalabarce.mcp.config.TaigaConfig
import dev.afalabarce.mcp.taiga.model.TaigaAttachment
import dev.afalabarce.mcp.taiga.model.TaigaAuthResponse
import dev.afalabarce.mcp.taiga.model.TaigaCommentVersion
import dev.afalabarce.mcp.taiga.model.TaigaCustomAttribute
import dev.afalabarce.mcp.taiga.model.TaigaCustomAttributesValues
import dev.afalabarce.mcp.taiga.model.TaigaDueDate
import dev.afalabarce.mcp.taiga.model.TaigaEpic
import dev.afalabarce.mcp.taiga.model.TaigaEpicRelatedUserStory
import dev.afalabarce.mcp.taiga.model.TaigaHistoryEntry
import dev.afalabarce.mcp.taiga.model.TaigaIssue
import dev.afalabarce.mcp.taiga.model.TaigaIssueType
import dev.afalabarce.mcp.taiga.model.TaigaMembership
import dev.afalabarce.mcp.taiga.model.TaigaMilestone
import dev.afalabarce.mcp.taiga.model.TaigaNotifyPolicy
import dev.afalabarce.mcp.taiga.model.TaigaPriority
import dev.afalabarce.mcp.taiga.model.TaigaProject
import dev.afalabarce.mcp.taiga.model.TaigaProjectStatuses
import dev.afalabarce.mcp.taiga.model.TaigaProjectSummary
import dev.afalabarce.mcp.taiga.model.TaigaProjectTemplate
import dev.afalabarce.mcp.taiga.model.TaigaPoint
import dev.afalabarce.mcp.taiga.model.TaigaRole
import dev.afalabarce.mcp.taiga.model.TaigaSearchResult
import dev.afalabarce.mcp.taiga.model.TaigaSeverity
import dev.afalabarce.mcp.taiga.model.TaigaStatus
import dev.afalabarce.mcp.taiga.model.TaigaSwimlane
import dev.afalabarce.mcp.taiga.model.TaigaTask
import dev.afalabarce.mcp.taiga.model.TaigaTimelineEntry
import dev.afalabarce.mcp.taiga.model.TaigaUser
import dev.afalabarce.mcp.taiga.model.TaigaUserStory
import dev.afalabarce.mcp.taiga.model.TaigaVoter
import dev.afalabarce.mcp.taiga.model.TaigaWatcher
import dev.afalabarce.mcp.taiga.model.TaigaWebhook
import dev.afalabarce.mcp.taiga.model.TaigaWebhookLog
import dev.afalabarce.mcp.taiga.model.TaigaWebNotification
import dev.afalabarce.mcp.taiga.model.TaigaWikiLink
import dev.afalabarce.mcp.taiga.model.TaigaWikiPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
            explicitNulls = false
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
        epicId: Long? = null,
        assignedToId: Long? = null
    ): List<TaigaUserStory> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/userstories") {
            authenticatedRequest(this)
            parameter("project", pid)
            milestoneId?.let { parameter("milestone", it) }
            statusId?.let { parameter("status", it) }
            epicId?.let { parameter("epic", it) }
            assignedToId?.let { parameter("assigned_to", it) }
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
        typeId: Long? = null,
        milestoneId: Long? = null,
        assignedToId: Long? = null
    ): List<TaigaIssue> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/issues") {
            authenticatedRequest(this)
            parameter("project", pid)
            statusId?.let { parameter("status", it) }
            priorityId?.let { parameter("priority", it) }
            severityId?.let { parameter("severity", it) }
            typeId?.let { parameter("type", it) }
            milestoneId?.let { parameter("milestone", it) }
            assignedToId?.let { parameter("assigned_to", it) }
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
    suspend fun getWikiPage(id: Long): TaigaWikiPage {
        val response = httpClient.get("$baseUrl/api/v1/wiki/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWikiPage>(text)
    }

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

    suspend fun getProjectRoles(projectId: Long? = null): List<TaigaRole> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/roles") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaRole>>(text)
    }

    suspend fun getProjectPoints(projectId: Long? = null): List<TaigaPoint> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/points") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaPoint>>(text)
    }

    suspend fun getEpicStatuses(projectId: Long? = null): List<TaigaStatus> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/epic-statuses") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaStatus>>(text)
    }

    suspend fun listMemberships(projectId: Long? = null): List<TaigaMembership> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/memberships") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaMembership>>(text)
    }

    suspend fun search(projectId: Long? = null, text: String): TaigaSearchResult {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/search") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("text", text)
        }
        val responseText = handleResponse(response)
        return json.decodeFromString<TaigaSearchResult>(responseText)
    }

    suspend fun getHistory(type: String, id: Long): List<TaigaHistoryEntry> {
        val normalizedType = when (type.lowercase().trim()) {
            "user_story", "userstory", "story" -> "userstory"
            "task" -> "task"
            "issue", "bug" -> "issue"
            "epic" -> "epic"
            else -> throw IllegalArgumentException("Unsupported type '$type'. Supported types: user_story, task, issue, epic")
        }
        val response = httpClient.get("$baseUrl/api/v1/history/$normalizedType/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaHistoryEntry>>(text)
    }

    suspend fun addComment(type: String, id: Long, comment: String): JsonObject {
        val normalizedType = when (type.lowercase().trim()) {
            "user_story", "userstory", "story" -> "userstories"
            "task" -> "tasks"
            "issue", "bug" -> "issues"
            "epic" -> "epics"
            else -> throw IllegalArgumentException("Unsupported type '$type'. Supported types: user_story, task, issue, epic")
        }

        val version = when (normalizedType) {
            "userstories" -> getUserStory(id).version
            "tasks" -> getTask(id).version
            "issues" -> getIssue(id).version
            "epics" -> getEpic(id).version
            else -> 1
        }

        val payload = buildJsonObject {
            put("version", version)
            put("comment", comment)
        }

        val response = httpClient.patch("$baseUrl/api/v1/$normalizedType/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<JsonObject>(text)
    }

    suspend fun getProjectSummary(projectId: Long? = null): TaigaProjectSummary {
        val pid = projectId ?: resolveProjectId()
        val project = getProject(pid)
        val milestones = runCatching { listMilestones(pid) }.getOrDefault(emptyList())
        val userStories = runCatching { listUserStories(pid) }.getOrDefault(emptyList())
        val tasks = runCatching { listTasks(pid) }.getOrDefault(emptyList())
        val issues = runCatching { listIssues(pid) }.getOrDefault(emptyList())
        val memberships = runCatching { listMemberships(pid) }.getOrDefault(emptyList())
        val statuses = runCatching { getAllProjectStatuses(pid) }.getOrNull()

        val storyStatusMap = statuses?.userStoryStatuses?.associate { it.id to it.name } ?: emptyMap()
        val taskStatusMap = statuses?.taskStatuses?.associate { it.id to it.name } ?: emptyMap()
        val issueStatusMap = statuses?.issueStatuses?.associate { it.id to it.name } ?: emptyMap()

        val storyCounts = userStories.groupingBy { storyStatusMap[it.status] ?: "ID ${it.status}" }.eachCount()
        val taskCounts = tasks.groupingBy { taskStatusMap[it.status] ?: "ID ${it.status}" }.eachCount()
        val issueCounts = issues.groupingBy { issueStatusMap[it.status] ?: "ID ${it.status}" }.eachCount()

        val activeSprints = milestones.filter { !it.closed }.map { "${it.name} (ID: ${it.id}, slug: ${it.slug})" }
        val memberNames = memberships.mapNotNull { it.fullName?.takeIf { n -> n.isNotBlank() } ?: it.userEmail ?: it.roleName }

        return TaigaProjectSummary(
            id = project.id,
            name = project.name,
            slug = project.slug,
            description = project.description,
            totalMilestones = project.totalMilestones ?: milestones.size,
            totalStoryPoints = project.totalStoryPoints,
            activeSprints = activeSprints,
            userStoryCountByStatus = storyCounts,
            taskCountByStatus = taskCounts,
            issueCountByStatus = issueCounts,
            members = memberNames
        )
    }

    suspend fun getAllProjectStatuses(projectId: Long? = null): TaigaProjectStatuses {
        val pid = projectId ?: resolveProjectId()
        return TaigaProjectStatuses(
            projectId = pid,
            userStoryStatuses = getUserStoryStatuses(pid),
            taskStatuses = getTaskStatuses(pid),
            issueStatuses = getIssueStatuses(pid),
            epicStatuses = runCatching { getEpicStatuses(pid) }.getOrDefault(emptyList()),
            priorities = getPriorities(pid),
            severities = getSeverities(pid),
            issueTypes = getIssueTypes(pid),
            roles = getProjectRoles(pid),
            points = getProjectPoints(pid)
        )
    }

    // --- Users ---
    suspend fun getUser(id: Long): TaigaUser {
        val response = httpClient.get("$baseUrl/api/v1/users/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUser>(text)
    }

    // --- By Ref Lookups ---
    suspend fun getEpicByRef(ref: Long, projectId: Long? = null): TaigaEpic {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/epics/by_ref") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("ref", ref)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaEpic>(text)
    }

    suspend fun getUserStoryByRef(ref: Long, projectId: Long? = null): TaigaUserStory {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/userstories/by_ref") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("ref", ref)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUserStory>(text)
    }

    suspend fun getTaskByRef(ref: Long, projectId: Long? = null): TaigaTask {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/tasks/by_ref") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("ref", ref)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaTask>(text)
    }

    suspend fun getIssueByRef(ref: Long, projectId: Long? = null): TaigaIssue {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/issues/by_ref") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("ref", ref)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaIssue>(text)
    }

    // --- Project Management ---
    suspend fun createProject(data: JsonObject): TaigaProject {
        val response = httpClient.post("$baseUrl/api/v1/projects") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaProject>(text)
    }

    suspend fun updateProject(id: Long, data: JsonObject): TaigaProject {
        val response = httpClient.patch("$baseUrl/api/v1/projects/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaProject>(text)
    }

    suspend fun deleteProject(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/projects/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun getProjectTags(projectId: Long? = null): JsonObject {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/projects/$pid/tags_colors") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<JsonObject>(text)
    }

    // --- Memberships ---
    suspend fun createMembership(data: JsonObject): TaigaMembership {
        val response = httpClient.post("$baseUrl/api/v1/memberships") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaMembership>(text)
    }

    suspend fun deleteMembership(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/memberships/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Milestone Extended ---
    suspend fun updateMilestone(id: Long, data: JsonObject): TaigaMilestone {
        val response = httpClient.patch("$baseUrl/api/v1/milestones/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaMilestone>(text)
    }

    suspend fun deleteMilestone(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/milestones/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun getMilestoneStats(id: Long): JsonObject {
        val response = httpClient.get("$baseUrl/api/v1/milestones/$id/stats") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<JsonObject>(text)
    }

    // --- Wiki Extended ---
    suspend fun deleteWikiPage(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/wiki/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun listWikiLinks(projectId: Long? = null): List<TaigaWikiLink> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/wiki-links") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaWikiLink>>(text)
    }

    suspend fun createWikiLink(data: JsonObject): TaigaWikiLink {
        val response = httpClient.post("$baseUrl/api/v1/wiki-links") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWikiLink>(text)
    }

    suspend fun deleteWikiLink(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/wiki-links/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Epic Related User Stories ---
    suspend fun listEpicRelatedUserStories(epicId: Long): List<TaigaEpicRelatedUserStory> {
        val response = httpClient.get("$baseUrl/api/v1/epics/$epicId/related_userstories") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaEpicRelatedUserStory>>(text)
    }

    suspend fun linkUserStoryToEpic(epicId: Long, userStoryId: Long, order: Long? = null): TaigaEpicRelatedUserStory {
        val payload = buildJsonObject {
            put("epic", epicId)
            put("user_story", userStoryId)
            order?.let { put("order", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/epics/$epicId/related_userstories") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaEpicRelatedUserStory>(text)
    }

    suspend fun unlinkUserStoryFromEpic(epicId: Long, userStoryId: Long): Boolean {
        val relations = listEpicRelatedUserStories(epicId)
        val relation = relations.firstOrNull { it.userStory == userStoryId || it.id == userStoryId }
            ?: throw IllegalArgumentException("User story $userStoryId is not linked to epic $epicId")
        val response = httpClient.delete("$baseUrl/api/v1/epics/$epicId/related_userstories/${relation.id}") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Custom Attributes ---
    private fun normalizeCustomAttributeType(type: String): String = when (type.lowercase().trim()) {
        "user_story", "userstory", "story", "userstories" -> "userstory"
        "task", "tasks" -> "task"
        "issue", "issues", "bug", "bugs" -> "issue"
        "epic", "epics" -> "epic"
        else -> throw IllegalArgumentException("Unsupported custom attribute type '$type'. Supported: userstory, task, issue, epic")
    }

    suspend fun listCustomAttributes(type: String, projectId: Long? = null): List<TaigaCustomAttribute> {
        val pid = projectId ?: resolveProjectId()
        val t = normalizeCustomAttributeType(type)
        val response = httpClient.get("$baseUrl/api/v1/$t-custom-attributes") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaCustomAttribute>>(text)
    }

    suspend fun createCustomAttribute(type: String, data: JsonObject): TaigaCustomAttribute {
        val t = normalizeCustomAttributeType(type)
        val response = httpClient.post("$baseUrl/api/v1/$t-custom-attributes") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(data)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaCustomAttribute>(text)
    }

    suspend fun deleteCustomAttribute(type: String, id: Long): Boolean {
        val t = normalizeCustomAttributeType(type)
        val response = httpClient.delete("$baseUrl/api/v1/$t-custom-attributes/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun getCustomAttributesValues(type: String, objectId: Long): TaigaCustomAttributesValues {
        val plural = when (normalizeCustomAttributeType(type)) {
            "userstory" -> "userstories"
            "task" -> "tasks"
            "issue" -> "issues"
            "epic" -> "epics"
            else -> "userstories"
        }
        val response = httpClient.get("$baseUrl/api/v1/$plural/custom-attributes-values/$objectId") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaCustomAttributesValues>(text)
    }

    suspend fun updateCustomAttributesValues(
        type: String,
        objectId: Long,
        values: JsonObject,
        version: Int? = null
    ): TaigaCustomAttributesValues {
        val plural = when (normalizeCustomAttributeType(type)) {
            "userstory" -> "userstories"
            "task" -> "tasks"
            "issue" -> "issues"
            "epic" -> "epics"
            else -> "userstories"
        }
        val actualVersion = version ?: getCustomAttributesValues(type, objectId).version
        val payload = buildJsonObject {
            put("attributes_values", values)
            put("version", actualVersion)
        }
        val response = httpClient.patch("$baseUrl/api/v1/$plural/custom-attributes-values/$objectId") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaCustomAttributesValues>(text)
    }

    // --- Attachments ---
    private fun normalizeAttachmentEndpoint(type: String): String = when (type.lowercase().trim()) {
        "user_story", "userstory", "story", "userstories" -> "userstories/attachments"
        "task", "tasks" -> "tasks/attachments"
        "issue", "issues", "bug", "bugs" -> "issues/attachments"
        "epic", "epics" -> "epics/attachments"
        "wiki", "wikipage", "wiki_page" -> "wiki/attachments"
        else -> throw IllegalArgumentException("Unsupported attachment type '$type'. Supported: user_story, task, issue, epic, wiki")
    }

    suspend fun listAttachments(type: String, objectId: Long, projectId: Long? = null): List<TaigaAttachment> {
        val pid = projectId ?: resolveProjectId()
        val ep = normalizeAttachmentEndpoint(type)
        val response = httpClient.get("$baseUrl/api/v1/$ep") {
            authenticatedRequest(this)
            parameter("project", pid)
            parameter("object_id", objectId)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaAttachment>>(text)
    }

    suspend fun uploadAttachment(
        type: String,
        objectId: Long,
        projectId: Long? = null,
        filename: String,
        fileBytes: ByteArray,
        mimeType: String = "application/octet-stream",
        description: String? = null
    ): TaigaAttachment {
        val pid = projectId ?: resolveProjectId()
        val ep = normalizeAttachmentEndpoint(type)
        val response = httpClient.post("$baseUrl/api/v1/$ep") {
            authenticatedRequest(this)
            setBody(MultiPartFormDataContent(
                formData {
                    append("project", pid.toString())
                    append("object_id", objectId.toString())
                    description?.let { append("description", it) }
                    append("attached_file", fileBytes, Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    })
                }
            ))
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaAttachment>(text)
    }

    suspend fun deleteAttachment(type: String, attachmentId: Long): Boolean {
        val ep = normalizeAttachmentEndpoint(type)
        val response = httpClient.delete("$baseUrl/api/v1/$ep/$attachmentId") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Interactions (Voting & Watching) ---
    private fun normalizeInteractionResource(type: String): String = when (type.lowercase().trim()) {
        "user_story", "userstory", "story", "userstories" -> "userstories"
        "task", "tasks" -> "tasks"
        "issue", "issues", "bug", "bugs" -> "issues"
        "epic", "epics" -> "epics"
        "milestone", "milestones", "sprint" -> "milestones"
        "wiki", "wikipage", "wiki_page" -> "wiki"
        "project", "projects" -> "projects"
        else -> throw IllegalArgumentException("Unsupported interaction resource '$type'")
    }

    suspend fun upvote(type: String, id: Long): Boolean {
        val plural = normalizeInteractionResource(type)
        val response = httpClient.post("$baseUrl/api/v1/$plural/$id/upvote") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun downvote(type: String, id: Long): Boolean {
        val plural = normalizeInteractionResource(type)
        val response = httpClient.post("$baseUrl/api/v1/$plural/$id/downvote") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun watch(type: String, id: Long): Boolean {
        val plural = normalizeInteractionResource(type)
        val response = httpClient.post("$baseUrl/api/v1/$plural/$id/watch") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun unwatch(type: String, id: Long): Boolean {
        val plural = normalizeInteractionResource(type)
        val response = httpClient.post("$baseUrl/api/v1/$plural/$id/unwatch") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Promotion ---
    suspend fun promoteIssueToUserStory(issueId: Long, projectId: Long? = null): TaigaUserStory {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
        }
        val response = httpClient.post("$baseUrl/api/v1/issues/$issueId/promote_to_user_story") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaUserStory>(text)
    }

    // --- Swimlanes ---
    suspend fun listSwimlanes(projectId: Long? = null): List<TaigaSwimlane> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/swimlanes") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaSwimlane>>(text)
    }

    suspend fun getSwimlane(id: Long): TaigaSwimlane {
        val response = httpClient.get("$baseUrl/api/v1/swimlanes/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaSwimlane>(text)
    }

    suspend fun createSwimlane(name: String, projectId: Long? = null, order: Long? = null): TaigaSwimlane {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("name", name)
            put("project", pid)
            order?.let { put("order", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/swimlanes") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaSwimlane>(text)
    }

    suspend fun updateSwimlane(id: Long, name: String? = null, order: Long? = null): TaigaSwimlane {
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            order?.let { put("order", it) }
        }
        val response = httpClient.patch("$baseUrl/api/v1/swimlanes/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaSwimlane>(text)
    }

    suspend fun deleteSwimlane(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/swimlanes/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun reorderSwimlanes(bulkSwimlanes: JsonArray, projectId: Long? = null): Boolean {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_swimlanes", bulkSwimlanes)
        }
        val response = httpClient.post("$baseUrl/api/v1/swimlanes/bulk_update_order") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        handleResponse(response)
        return true
    }

    suspend fun listSwimlaneUserStoryStatuses(projectId: Long? = null): JsonElement {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/swimlane-userstory-statuses") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    // --- Due Dates ---
    private fun normalizeDueDateType(type: String): String = when (type.lowercase().trim()) {
        "user_story", "userstory", "story", "userstories" -> "userstory"
        "task", "tasks" -> "task"
        "issue", "issues", "bug", "bugs" -> "issue"
        else -> throw IllegalArgumentException("Unsupported due date type '$type'. Supported: userstory, task, issue")
    }

    suspend fun listDueDates(type: String, projectId: Long? = null): List<TaigaDueDate> {
        val prefix = normalizeDueDateType(type)
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/$prefix-due-dates") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaDueDate>>(text)
    }

    suspend fun getDueDate(type: String, id: Long): TaigaDueDate {
        val prefix = normalizeDueDateType(type)
        val response = httpClient.get("$baseUrl/api/v1/$prefix-due-dates/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaDueDate>(text)
    }

    suspend fun createDueDate(
        type: String,
        name: String,
        color: String,
        daysToDue: Int? = null,
        byDefault: Boolean = false,
        order: Int? = null,
        projectId: Long? = null
    ): TaigaDueDate {
        val prefix = normalizeDueDateType(type)
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("name", name)
            put("color", color)
            put("project", pid)
            daysToDue?.let { put("days_to_due", it) }
            put("by_default", byDefault)
            order?.let { put("order", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/$prefix-due-dates") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaDueDate>(text)
    }

    suspend fun updateDueDate(
        type: String,
        id: Long,
        name: String? = null,
        color: String? = null,
        daysToDue: Int? = null,
        byDefault: Boolean? = null,
        order: Int? = null
    ): TaigaDueDate {
        val prefix = normalizeDueDateType(type)
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            color?.let { put("color", it) }
            daysToDue?.let { put("days_to_due", it) }
            byDefault?.let { put("by_default", it) }
            order?.let { put("order", it) }
        }
        val response = httpClient.patch("$baseUrl/api/v1/$prefix-due-dates/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaDueDate>(text)
    }

    suspend fun deleteDueDate(type: String, id: Long): Boolean {
        val prefix = normalizeDueDateType(type)
        val response = httpClient.delete("$baseUrl/api/v1/$prefix-due-dates/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    // --- Bulk Operations ---
    suspend fun bulkCreateUserStories(
        bulkStories: String,
        statusId: Long? = null,
        swimlaneId: Long? = null,
        projectId: Long? = null
    ): List<TaigaUserStory> {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_stories", bulkStories)
            statusId?.let { put("status_id", it) }
            swimlaneId?.let { put("swimlane_id", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/userstories/bulk_create") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaUserStory>>(text)
    }

    suspend fun bulkCreateTasks(
        bulkTasks: String,
        userStoryId: Long? = null,
        milestoneId: Long? = null,
        statusId: Long? = null,
        projectId: Long? = null
    ): List<TaigaTask> {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_tasks", bulkTasks)
            userStoryId?.let { put("us_id", it) }
            milestoneId?.let { put("milestone_id", it) }
            statusId?.let { put("status_id", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/tasks/bulk_create") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaTask>>(text)
    }

    suspend fun bulkCreateIssues(
        bulkIssues: String,
        milestoneId: Long? = null,
        projectId: Long? = null
    ): List<TaigaIssue> {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_issues", bulkIssues)
            milestoneId?.let { put("milestone_id", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/issues/bulk_create") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaIssue>>(text)
    }

    suspend fun bulkCreateEpics(
        bulkEpics: String,
        statusId: Long? = null,
        projectId: Long? = null
    ): List<TaigaEpic> {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_epics", bulkEpics)
            statusId?.let { put("status_id", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/epics/bulk_create") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaEpic>>(text)
    }

    suspend fun bulkCreateMemberships(
        bulkMemberships: JsonArray,
        projectId: Long? = null
    ): List<TaigaMembership> {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_memberships", bulkMemberships)
        }
        val response = httpClient.post("$baseUrl/api/v1/memberships/bulk_create") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaMembership>>(text)
    }

    suspend fun moveToSprint(
        type: String,
        milestoneId: Long,
        itemIds: List<Long>,
        projectId: Long? = null
    ): JsonElement {
        val pid = projectId ?: resolveProjectId()
        val resource = when (type.lowercase().trim()) {
            "user_story", "userstory", "story", "userstories" -> "userstories"
            "task", "tasks" -> "tasks"
            "issue", "issues", "bug", "bugs" -> "issues"
            else -> throw IllegalArgumentException("Unsupported type '$type' for move to sprint. Supported: userstories, tasks, issues")
        }
        val payload = buildJsonObject {
            put("project_id", pid)
            put(resource, JsonArray(itemIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }
        val response = httpClient.post("$baseUrl/api/v1/milestones/$milestoneId/move_${resource}_to_sprint") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun bulkUpdateBacklogOrder(
        bulkStories: JsonArray,
        projectId: Long? = null
    ): Boolean {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_stories", bulkStories)
        }
        val response = httpClient.post("$baseUrl/api/v1/userstories/bulk_update_backlog_order") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        handleResponse(response)
        return true
    }

    suspend fun bulkUpdateKanbanOrder(
        bulkStories: JsonArray,
        projectId: Long? = null
    ): Boolean {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_stories", bulkStories)
        }
        val response = httpClient.post("$baseUrl/api/v1/userstories/bulk_update_kanban_order") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        handleResponse(response)
        return true
    }

    suspend fun bulkUpdateTaskboardOrder(
        bulkTasks: JsonArray,
        projectId: Long? = null
    ): Boolean {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("project_id", pid)
            put("bulk_tasks", bulkTasks)
        }
        val response = httpClient.post("$baseUrl/api/v1/tasks/bulk_update_taskboard_order") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        handleResponse(response)
        return true
    }

    // --- Webhooks ---
    suspend fun listWebhooks(projectId: Long? = null): List<TaigaWebhook> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/webhooks") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaWebhook>>(text)
    }

    suspend fun getWebhook(id: Long): TaigaWebhook {
        val response = httpClient.get("$baseUrl/api/v1/webhooks/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWebhook>(text)
    }

    suspend fun createWebhook(name: String, url: String, key: String? = null, projectId: Long? = null): TaigaWebhook {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("name", name)
            put("url", url)
            put("project", pid)
            key?.let { put("key", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/webhooks") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWebhook>(text)
    }

    suspend fun updateWebhook(id: Long, name: String? = null, url: String? = null, key: String? = null): TaigaWebhook {
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            url?.let { put("url", it) }
            key?.let { put("key", it) }
        }
        val response = httpClient.patch("$baseUrl/api/v1/webhooks/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaWebhook>(text)
    }

    suspend fun deleteWebhook(id: Long): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/webhooks/$id") {
            authenticatedRequest(this)
        }
        handleResponse(response)
        return true
    }

    suspend fun testWebhook(id: Long): JsonElement {
        val response = httpClient.post("$baseUrl/api/v1/webhooks/$id/test") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun listWebhookLogs(webhookId: Long): List<TaigaWebhookLog> {
        val response = httpClient.get("$baseUrl/api/v1/webhooklogs") {
            authenticatedRequest(this)
            parameter("webhook", webhookId)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaWebhookLog>>(text)
    }

    suspend fun resendWebhook(logId: Long): JsonElement {
        val response = httpClient.post("$baseUrl/api/v1/webhooklogs/$logId/resend") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    // --- Comment History ---
    suspend fun editComment(type: String, id: Long, commentId: String, comment: String): JsonElement {
        val normalizedType = normalizeCommentResourceType(type)
        val payload = buildJsonObject {
            put("id", commentId)
            put("comment", comment)
        }
        val response = httpClient.post("$baseUrl/api/v1/history/$normalizedType/$id/edit_comment") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun deleteComment(type: String, id: Long, commentId: String): JsonElement {
        val normalizedType = normalizeCommentResourceType(type)
        val payload = buildJsonObject {
            put("id", commentId)
        }
        val response = httpClient.post("$baseUrl/api/v1/history/$normalizedType/$id/delete_comment") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun undeleteComment(type: String, id: Long, commentId: String): JsonElement {
        val normalizedType = normalizeCommentResourceType(type)
        val payload = buildJsonObject {
            put("id", commentId)
        }
        val response = httpClient.post("$baseUrl/api/v1/history/$normalizedType/$id/undelete_comment") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun getCommentVersions(type: String, id: Long, commentId: String): List<TaigaCommentVersion> {
        val normalizedType = normalizeCommentResourceType(type)
        val response = httpClient.get("$baseUrl/api/v1/history/$normalizedType/$id/comment_versions") {
            authenticatedRequest(this)
            parameter("id", commentId)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaCommentVersion>>(text)
    }

    private fun normalizeCommentResourceType(type: String): String = when (type.lowercase().trim()) {
        "user_story", "userstory", "story" -> "userstory"
        "task" -> "task"
        "issue", "bug" -> "issue"
        "epic" -> "epic"
        "wiki", "wiki_page", "wikipage" -> "wiki"
        else -> throw IllegalArgumentException("Unsupported comment history resource '$type'")
    }

    // --- Web Notifications & Notify Policies ---
    suspend fun listWebNotifications(onlyUnread: Boolean = false): List<TaigaWebNotification> {
        val response = httpClient.get("$baseUrl/api/v1/web-notifications") {
            authenticatedRequest(this)
            if (onlyUnread) parameter("only_unread", "true")
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaWebNotification>>(text)
    }

    suspend fun markWebNotificationsRead(id: Long? = null): Boolean {
        val payload = buildJsonObject {
            id?.let { put("id", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/web-notifications/set-as-read") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        handleResponse(response)
        return true
    }

    suspend fun listNotifyPolicies(projectId: Long? = null): List<TaigaNotifyPolicy> {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/notify-policies") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaNotifyPolicy>>(text)
    }

    suspend fun updateNotifyPolicy(id: Long, notifyLevel: Int): TaigaNotifyPolicy {
        val payload = buildJsonObject {
            put("notify_level", notifyLevel)
        }
        val response = httpClient.patch("$baseUrl/api/v1/notify-policies/$id") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaNotifyPolicy>(text)
    }

    // --- Voters & Watchers ---
    suspend fun listVoters(type: String, id: Long): List<TaigaVoter> {
        val plural = normalizeInteractionResource(type)
        val response = httpClient.get("$baseUrl/api/v1/$plural/$id/voters") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaVoter>>(text)
    }

    suspend fun listWatchers(type: String, id: Long): List<TaigaWatcher> {
        val plural = normalizeInteractionResource(type)
        val response = httpClient.get("$baseUrl/api/v1/$plural/$id/watchers") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaWatcher>>(text)
    }

    // --- Project Advanced Operations ---
    suspend fun duplicateProject(
        projectId: Long? = null,
        name: String,
        description: String? = null,
        isPrivate: Boolean? = null,
        users: JsonArray? = null
    ): TaigaProject {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("name", name)
            description?.let { put("description", it) }
            isPrivate?.let { put("is_private", it) }
            users?.let { put("users", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/projects/$pid/duplicate") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.decodeFromString<TaigaProject>(text)
    }

    suspend fun listProjectTemplates(): List<TaigaProjectTemplate> {
        val response = httpClient.get("$baseUrl/api/v1/project-templates") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaProjectTemplate>>(text)
    }

    suspend fun exportProject(projectId: Long? = null): JsonElement {
        val pid = projectId ?: resolveProjectId()
        val response = httpClient.get("$baseUrl/api/v1/exporter/$pid") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun exportCsv(type: String, projectId: Long? = null): String {
        val pid = projectId ?: resolveProjectId()
        val plural = when (type.lowercase().trim()) {
            "epic", "epics" -> "epics"
            "user_story", "userstory", "userstories", "story" -> "userstories"
            "task", "tasks" -> "tasks"
            "issue", "issues", "bug", "bugs" -> "issues"
            else -> throw IllegalArgumentException("Unsupported CSV export type '$type'. Supported: epics, userstories, tasks, issues")
        }
        val response = httpClient.get("$baseUrl/api/v1/$plural/csv") {
            authenticatedRequest(this)
            parameter("project", pid)
        }
        return handleResponse(response)
    }

    suspend fun transferProjectStart(projectId: Long? = null, userId: Long): JsonElement {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("user", userId)
        }
        val response = httpClient.post("$baseUrl/api/v1/projects/$pid/transfer_start") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun transferProjectAccept(projectId: Long? = null, token: String, reason: String? = null): JsonElement {
        val pid = projectId ?: resolveProjectId()
        val payload = buildJsonObject {
            put("token", token)
            reason?.let { put("reason", it) }
        }
        val response = httpClient.post("$baseUrl/api/v1/projects/$pid/transfer_accept") {
            authenticatedRequest(this)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    // --- Timelines ---
    suspend fun getTimeline(type: String, id: Long): List<TaigaTimelineEntry> {
        val normType = when (type.lowercase().trim()) {
            "user" -> "user"
            "project" -> "project"
            "profile" -> "profile"
            else -> throw IllegalArgumentException("Unsupported timeline type '$type'. Supported: user, project, profile")
        }
        val response = httpClient.get("$baseUrl/api/v1/timeline/$normType/$id") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaTimelineEntry>>(text)
    }

    // --- Extended User Profiles ---
    suspend fun getUserContacts(userId: Long): List<TaigaUser> {
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/contacts") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.decodeFromString<List<TaigaUser>>(text)
    }

    suspend fun getUserLiked(userId: Long): JsonElement {
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/liked") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun getUserVoted(userId: Long): JsonElement {
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/voted") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }

    suspend fun getUserWatched(userId: Long): JsonElement {
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/watched") {
            authenticatedRequest(this)
        }
        val text = handleResponse(response)
        return json.parseToJsonElement(text)
    }
}

class TaigaApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)
