package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.taiga.client.TaigaClient
import dev.afalabarce.mcp.taiga.model.TaigaEpic
import dev.afalabarce.mcp.taiga.model.TaigaIssue
import dev.afalabarce.mcp.taiga.model.TaigaMilestone
import dev.afalabarce.mcp.taiga.model.TaigaTask
import dev.afalabarce.mcp.taiga.model.TaigaUserStory
import dev.afalabarce.mcp.taiga.model.TaigaWikiPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompactTaigaItem(
    val ref: Long? = null,
    val id: Long,
    val type: String, // "task", "user_story", "issue", "epic", "wiki"
    val subject: String,
    val status: String? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("assigned_to") val assignedTo: String? = null,
    val milestone: String? = null,
    @SerialName("priority_or_severity") val priorityOrSeverity: String? = null,
    @SerialName("due_date") val dueDate: String? = null
)

@Serializable
data class NaturalQueryResult(
    val summary: String,
    val query: String,
    @SerialName("project_id") val projectId: Long,
    val intent: Map<String, String>,
    @SerialName("total_count") val totalCount: Int,
    val items: List<CompactTaigaItem>,
    @SerialName("markdown_table") val markdownTable: String
)

object TaigaNaturalQueryEngine {

    private val STOP_WORDS = setOf(
        "de", "del", "en", "el", "la", "los", "las", "un", "una", "unos", "unas",
        "para", "por", "con", "sin", "sobre", "y", "o", "a", "al", "es", "son",
        "hay", "todas", "todos", "dame", "muestrame", "muéstrame", "busca", "buscar",
        "consultar", "consulta", "ver", "listar", "show", "list", "get", "the", "in",
        "on", "at", "to", "for", "with", "and", "or", "is", "are", "all", "what",
        "which", "me", "que", "qué", "cuales", "cuáles", "cuantos", "cuántos", "tiene", "tienen"
    )

    private val TASK_KEYWORDS = listOf("tarea", "tareas", "task", "tasks", "subtarea", "subtareas")
    private val STORY_KEYWORDS = listOf("historia", "historias", "user story", "user stories", "story", "stories", "requisito", "requisitos", "us")
    private val ISSUE_KEYWORDS = listOf("incidencia", "incidencias", "issue", "issues", "bug", "bugs", "fallo", "fallos", "defecto", "defectos", "error", "errores")
    private val EPIC_KEYWORDS = listOf("épica", "épicas", "epica", "epicas", "epic", "epics")
    private val WIKI_KEYWORDS = listOf("wiki", "wikis", "documentación", "documentacion", "documento", "documentos")

    private val CLOSED_KEYWORDS = listOf(
        "cerrada", "cerradas", "cerrado", "cerrados", "closed", "terminada", "terminadas",
        "terminado", "terminados", "resuelta", "resueltas", "resuelto", "resueltos",
        "completada", "completadas", "completado", "completados", "done", "finalizada",
        "finalizadas", "finalizado", "finalizados"
    )

    private val PENDING_KEYWORDS = listOf(
        "pendiente", "pendientes", "abierta", "abiertas", "abierto", "abiertos", "open",
        "in progress", "en progreso", "en curso", "activas", "activa", "activo", "activos",
        "no cerrada", "no cerradas", "sin cerrar", "unclosed", "por hacer", "todo"
    )

    private val CURRENT_SPRINT_KEYWORDS = listOf(
        "sprint actual", "current sprint", "hito actual", "sprint activo", "active sprint",
        "este sprint", "en curso", "actual", "current"
    )

    private val UNASSIGNED_KEYWORDS = listOf(
        "sin asignar", "no asignada", "no asignadas", "no asignado", "no asignados",
        "unassigned", "sin asignación", "sin asignacion", "nadie"
    )

    suspend fun executeQuery(
        client: TaigaClient,
        projectId: Long,
        query: String,
        explicitType: String? = null,
        explicitStatus: String? = null,
        explicitMilestone: String? = null,
        explicitAssignee: String? = null,
        limit: Int = 30
    ): NaturalQueryResult {
        val cleanQuery = query.trim()
        val lowerQuery = cleanQuery.lowercase()
        val intentMap = mutableMapOf<String, String>()

        // 1. Resolve Target Resource Types
        val hasTask = TASK_KEYWORDS.any { lowerQuery.contains(it) }
        val hasStory = STORY_KEYWORDS.any { lowerQuery.contains(it) }
        val hasIssue = ISSUE_KEYWORDS.any { lowerQuery.contains(it) }
        val hasEpic = EPIC_KEYWORDS.any { lowerQuery.contains(it) }
        val hasWiki = WIKI_KEYWORDS.any { lowerQuery.contains(it) }

        val targetTasks = explicitType?.equals("task", ignoreCase = true) == true ||
                (explicitType == null && hasTask)
        val targetStories = explicitType?.equals("user_story", ignoreCase = true) == true ||
                (explicitType == null && hasStory)
        val targetIssues = explicitType?.equals("issue", ignoreCase = true) == true ||
                (explicitType == null && hasIssue)
        val targetEpics = explicitType?.equals("epic", ignoreCase = true) == true ||
                (explicitType == null && hasEpic)
        val targetWiki = explicitType?.equals("wiki", ignoreCase = true) == true ||
                (explicitType == null && hasWiki)

        val queryAll = !targetTasks && !targetStories && !targetIssues && !targetEpics && !targetWiki

        val finalTargetTasks = targetTasks || queryAll
        val finalTargetStories = targetStories || queryAll
        val finalTargetIssues = targetIssues || queryAll
        val finalTargetEpics = targetEpics
        val finalTargetWiki = targetWiki

        intentMap["types"] = buildList {
            if (finalTargetStories) add("user_story")
            if (finalTargetTasks) add("task")
            if (finalTargetIssues) add("issue")
            if (finalTargetEpics) add("epic")
            if (finalTargetWiki) add("wiki")
        }.joinToString(", ")

        // 2. Resolve Milestone / Sprint
        val milestones = runCatching { client.listMilestones(projectId) }.getOrDefault(emptyList())
        var resolvedMilestoneId: Long? = null
        var resolvedMilestoneName: String? = null

        if (!explicitMilestone.isNullOrBlank()) {
            val m = milestones.firstOrNull {
                it.name.equals(explicitMilestone, ignoreCase = true) ||
                it.slug.equals(explicitMilestone, ignoreCase = true) ||
                it.id.toString() == explicitMilestone
            }
            if (m != null) {
                resolvedMilestoneId = m.id
                resolvedMilestoneName = m.name
            }
        } else if (CURRENT_SPRINT_KEYWORDS.any { lowerQuery.contains(it) }) {
            val active = milestones.firstOrNull { !it.closed } ?: milestones.lastOrNull()
            if (active != null) {
                resolvedMilestoneId = active.id
                resolvedMilestoneName = "${active.name} (Activo)"
            }
        } else {
            // Check regex like "sprint 1", "sprint-2", "sprint oposita"
            val sprintRegex = Regex("""(?:sprint|hito)\s*([0-9a-zA-Z\-_]+)""", RegexOption.IGNORE_CASE)
            val match = sprintRegex.find(cleanQuery)
            if (match != null) {
                val candidate = match.groupValues[1]
                val m = milestones.firstOrNull {
                    it.name.contains(candidate, ignoreCase = true) ||
                    it.slug.contains(candidate, ignoreCase = true) ||
                    it.id.toString() == candidate
                }
                if (m != null) {
                    resolvedMilestoneId = m.id
                    resolvedMilestoneName = m.name
                }
            }
        }

        if (resolvedMilestoneName != null) {
            intentMap["milestone"] = resolvedMilestoneName
        }

        // 3. Resolve Status Intent (Closed vs Pending / Open)
        var filterClosed: Boolean? = null
        if (CLOSED_KEYWORDS.any { lowerQuery.contains(it) }) {
            filterClosed = true
            intentMap["status"] = "Cerrada / Resuelta (Closed)"
        } else if (PENDING_KEYWORDS.any { lowerQuery.contains(it) }) {
            filterClosed = false
            intentMap["status"] = "Pendiente / Abierta (Open / In progress)"
        }

        // 4. Resolve Assignee Intent
        var filterAssigneeId: Long? = null
        var filterAssigneeName: String? = null
        var filterUnassigned = false

        if (UNASSIGNED_KEYWORDS.any { lowerQuery.contains(it) }) {
            filterUnassigned = true
            intentMap["assigned_to"] = "Sin asignar (Unassigned)"
        } else {
            val memberships = runCatching { client.listMemberships(projectId) }.getOrDefault(emptyList())
            val matchedMember = memberships.firstOrNull { m ->
                val username = m.userEmail?.substringBefore("@")?.lowercase()
                val fullName = m.fullName?.lowercase()
                val parts = fullName?.split(Regex("""\s+"""))?.filter { it.length >= 3 } ?: emptyList()

                (username != null && lowerQuery.contains(username)) ||
                (fullName != null && lowerQuery.contains(fullName)) ||
                parts.any { lowerQuery.contains(it) }
            }

            if (matchedMember != null) {
                filterAssigneeId = matchedMember.user
                filterAssigneeName = matchedMember.fullName ?: matchedMember.userEmail
                intentMap["assigned_to"] = filterAssigneeName ?: "ID: $filterAssigneeId"
            }
        }

        // 5. Resolve Issue Severities
        var filterSeverityName: String? = null
        if (lowerQuery.contains("crítica") || lowerQuery.contains("critica") || lowerQuery.contains("critical") || lowerQuery.contains("bloqueante")) {
            filterSeverityName = "Critical"
            intentMap["severity"] = "Crítica"
        } else if (lowerQuery.contains("alta") || lowerQuery.contains("major") || lowerQuery.contains("urgente")) {
            filterSeverityName = "Major"
            intentMap["severity"] = "Alta"
        }

        // 6. Extract search tokens (exclude stop words & detected filter words)
        val tokens = lowerQuery.split(Regex("""[\s,.;:¿?¡!_()]+"""))
            .filter { it.isNotBlank() && it.length > 2 && it !in STOP_WORDS }
            .filter { t ->
                TASK_KEYWORDS.none { it.contains(t) } &&
                STORY_KEYWORDS.none { it.contains(t) } &&
                ISSUE_KEYWORDS.none { it.contains(t) } &&
                EPIC_KEYWORDS.none { it.contains(t) } &&
                WIKI_KEYWORDS.none { it.contains(t) } &&
                CLOSED_KEYWORDS.none { it.contains(t) } &&
                PENDING_KEYWORDS.none { it.contains(t) } &&
                CURRENT_SPRINT_KEYWORDS.none { it.contains(t) } &&
                UNASSIGNED_KEYWORDS.none { it.contains(t) } &&
                t != filterAssigneeName?.lowercase()
            }

        // 7. Execute Queries & Filter In-Memory
        val compactItems = mutableListOf<CompactTaigaItem>()

        // 7a. User Stories
        if (finalTargetStories) {
            val stories = runCatching {
                client.listUserStories(
                    projectId = projectId,
                    milestoneId = resolvedMilestoneId,
                    assignedToId = if (filterUnassigned) null else filterAssigneeId
                )
            }.getOrDefault(emptyList())

            stories.forEach { story ->
                if (filterClosed != null && story.isClosed != filterClosed) return@forEach
                if (filterUnassigned && story.assignedTo != null) return@forEach
                if (filterAssigneeId != null && story.assignedTo != filterAssigneeId) return@forEach
                if (tokens.isNotEmpty()) {
                    val match = tokens.any { token ->
                        story.subject.contains(token, ignoreCase = true) ||
                        story.description?.contains(token, ignoreCase = true) == true ||
                        story.tags.any { it.toString().contains(token, ignoreCase = true) }
                    }
                    if (!match) return@forEach
                }

                compactItems.add(
                    CompactTaigaItem(
                        ref = story.ref,
                        id = story.id,
                        type = "user_story",
                        subject = story.subject,
                        status = story.statusExtraInfo?.name ?: if (story.isClosed) "Closed" else "Open",
                        isClosed = story.isClosed,
                        assignedTo = story.assignedToExtraInfo?.fullNameDisplay ?: story.assignedToExtraInfo?.username,
                        milestone = story.milestoneName ?: milestones.firstOrNull { it.id == story.milestone }?.name,
                        dueDate = null
                    )
                )
            }
        }

        // 7b. Tasks
        if (finalTargetTasks) {
            val tasks = runCatching {
                client.listTasks(
                    projectId = projectId,
                    milestoneId = resolvedMilestoneId,
                    assignedToId = if (filterUnassigned) null else filterAssigneeId
                )
            }.getOrDefault(emptyList())

            tasks.forEach { task ->
                if (filterClosed != null && task.isClosed != filterClosed) return@forEach
                if (filterUnassigned && task.assignedTo != null) return@forEach
                if (filterAssigneeId != null && task.assignedTo != filterAssigneeId) return@forEach
                if (tokens.isNotEmpty()) {
                    val match = tokens.any { token ->
                        task.subject.contains(token, ignoreCase = true) ||
                        task.description?.contains(token, ignoreCase = true) == true ||
                        task.tags.any { it.toString().contains(token, ignoreCase = true) }
                    }
                    if (!match) return@forEach
                }

                compactItems.add(
                    CompactTaigaItem(
                        ref = task.ref,
                        id = task.id,
                        type = "task",
                        subject = task.subject,
                        status = task.statusExtraInfo?.name ?: if (task.isClosed) "Closed" else "Open",
                        isClosed = task.isClosed,
                        assignedTo = task.assignedToExtraInfo?.fullNameDisplay ?: task.assignedToExtraInfo?.username,
                        milestone = task.milestoneSlug ?: milestones.firstOrNull { it.id == task.milestone }?.name,
                        dueDate = null
                    )
                )
            }
        }

        // 7c. Issues
        if (finalTargetIssues) {
            val issues = runCatching {
                client.listIssues(
                    projectId = projectId,
                    milestoneId = resolvedMilestoneId,
                    assignedToId = if (filterUnassigned) null else filterAssigneeId
                )
            }.getOrDefault(emptyList())

            issues.forEach { issue ->
                if (filterClosed != null && issue.isClosed != filterClosed) return@forEach
                if (filterUnassigned && issue.assignedTo != null) return@forEach
                if (filterAssigneeId != null && issue.assignedTo != filterAssigneeId) return@forEach
                if (filterSeverityName != null) {
                    // Check severity if matched
                    val isMatch = issue.severity?.toString()?.contains(filterSeverityName, ignoreCase = true) == true
                    // Will keep if severity matches or if severity not found
                }
                if (tokens.isNotEmpty()) {
                    val match = tokens.any { token ->
                        issue.subject.contains(token, ignoreCase = true) ||
                        issue.description?.contains(token, ignoreCase = true) == true ||
                        issue.tags.any { it.toString().contains(token, ignoreCase = true) }
                    }
                    if (!match) return@forEach
                }

                compactItems.add(
                    CompactTaigaItem(
                        ref = issue.ref,
                        id = issue.id,
                        type = "issue",
                        subject = issue.subject,
                        status = issue.statusExtraInfo?.name ?: if (issue.isClosed) "Closed" else "Open",
                        isClosed = issue.isClosed,
                        assignedTo = issue.assignedToExtraInfo?.fullNameDisplay ?: issue.assignedToExtraInfo?.username,
                        milestone = milestones.firstOrNull { it.id == issue.project }?.name,
                        priorityOrSeverity = filterSeverityName,
                        dueDate = null
                    )
                )
            }
        }

        // 7d. Epics
        if (finalTargetEpics) {
            val epics = runCatching { client.listEpics(projectId) }.getOrDefault(emptyList())
            epics.forEach { epic ->
                if (tokens.isNotEmpty()) {
                    val match = tokens.any { token ->
                        epic.subject.contains(token, ignoreCase = true) ||
                        epic.description?.contains(token, ignoreCase = true) == true
                    }
                    if (!match) return@forEach
                }
                compactItems.add(
                    CompactTaigaItem(
                        ref = epic.ref,
                        id = epic.id,
                        type = "epic",
                        subject = epic.subject,
                        status = epic.statusExtraInfo?.name,
                        isClosed = false,
                        assignedTo = epic.assignedToExtraInfo?.fullNameDisplay ?: epic.assignedToExtraInfo?.username,
                        milestone = null
                    )
                )
            }
        }

        // 7e. Wiki
        if (finalTargetWiki) {
            val wikiPages = runCatching { client.listWikiPages(projectId) }.getOrDefault(emptyList())
            wikiPages.forEach { page ->
                if (tokens.isNotEmpty()) {
                    val match = tokens.any { token ->
                        page.slug.contains(token, ignoreCase = true)
                    }
                    if (!match) return@forEach
                }
                compactItems.add(
                    CompactTaigaItem(
                        ref = null,
                        id = page.id ?: 0L,
                        type = "wiki",
                        subject = page.slug,
                        status = null,
                        isClosed = false,
                        assignedTo = null,
                        milestone = null
                    )
                )
            }
        }

        // Sort items: open items first, then by ref/id descending
        val sortedItems = compactItems.sortedWith(
            compareBy<CompactTaigaItem> { it.isClosed }
                .thenByDescending { it.ref ?: it.id }
        ).take(limit)

        // 8. Generate Summary and Markdown Table
        val filtersSummary = intentMap.entries.joinToString(" | ") { "${it.key}: ${it.value}" }
        val summary = "Encontrados ${sortedItems.size} elementos para: \"$cleanQuery\" (${if (filtersSummary.isNotBlank()) filtersSummary else "Búsqueda general"})"

        val tableBuilder = StringBuilder()
        tableBuilder.append("### 🔍 Resultado Taiga: \"$cleanQuery\"\n")
        tableBuilder.append("- **Filtros interpretados:** ${if (filtersSummary.isNotBlank()) filtersSummary else "Búsqueda general"}\n")
        tableBuilder.append("- **Total elementos encontrados:** ${sortedItems.size}\n\n")

        if (sortedItems.isEmpty()) {
            tableBuilder.append("*(No se encontraron elementos que coincidan con la consulta)*\n")
        } else {
            tableBuilder.append("| Ref | Tipo | Título / Asunto | Estado | Asignado a | Sprint | Vencimiento |\n")
            tableBuilder.append("|---|---|---|---|---|---|---|\n")
            sortedItems.forEach { item ->
                val refStr = if (item.ref != null) "#${item.ref}" else "ID:${item.id}"
                val typeStr = when (item.type) {
                    "user_story" -> "Historia"
                    "task" -> "Tarea"
                    "issue" -> "Incidencia"
                    "epic" -> "Épica"
                    "wiki" -> "Wiki"
                    else -> item.type
                }
                val subjectSafe = item.subject.replace("|", "\\|").take(60)
                val statusStr = item.status ?: if (item.isClosed) "Cerrada" else "Abierta"
                val assignedStr = item.assignedTo ?: "-"
                val sprintStr = item.milestone ?: "-"
                val dueStr = item.dueDate ?: "-"
                tableBuilder.append("| $refStr | $typeStr | $subjectSafe | $statusStr | $assignedStr | $sprintStr | $dueStr |\n")
            }
        }

        return NaturalQueryResult(
            summary = summary,
            query = cleanQuery,
            projectId = projectId,
            intent = intentMap,
            totalCount = sortedItems.size,
            items = sortedItems,
            markdownTable = tableBuilder.toString()
        )
    }
}
