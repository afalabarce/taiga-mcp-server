package dev.afalabarce.mcp.mcp.tools

import dev.afalabarce.mcp.taiga.client.TaigaClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object TaigaResolver {

    private fun extractString(element: JsonElement?): String? {
        if (element == null) return null
        return if (element is JsonPrimitive) element.contentOrNull?.trim()?.ifBlank { null } else null
    }

    private fun extractLong(element: JsonElement?): Long? {
        if (element == null) return null
        if (element is JsonPrimitive) {
            element.longOrNull?.let { return it }
            element.contentOrNull?.trim()?.toLongOrNull()?.let { return it }
        }
        return null
    }

    suspend fun resolveUserStoryStatus(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val statuses = client.getUserStoryStatuses(projectId)
        val matched = statuses.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.slug.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "User story status '$raw' not found in project. Available statuses: ${statuses.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolveTaskStatus(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val statuses = client.getTaskStatuses(projectId)
        val matched = statuses.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.slug.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Task status '$raw' not found in project. Available statuses: ${statuses.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolveIssueStatus(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val statuses = client.getIssueStatuses(projectId)
        val matched = statuses.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.slug.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Issue status '$raw' not found in project. Available statuses: ${statuses.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolveEpicStatus(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val statuses = runCatching { client.getEpicStatuses(projectId) }.getOrDefault(emptyList())
        if (statuses.isEmpty()) return null

        val matched = statuses.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.slug.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Epic status '$raw' not found in project. Available statuses: ${statuses.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolvePriority(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val priorities = client.getPriorities(projectId)
        val matched = priorities.firstOrNull {
            it.name.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Priority '$raw' not found in project. Available priorities: ${priorities.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolveSeverity(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val severities = client.getSeverities(projectId)
        val matched = severities.firstOrNull {
            it.name.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Severity '$raw' not found in project. Available severities: ${severities.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolveIssueType(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val types = client.getIssueTypes(projectId)
        val matched = types.firstOrNull {
            it.name.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Issue type '$raw' not found in project. Available types: ${types.joinToString { "${it.name} (ID: ${it.id})" }}"
        )
        return matched.id
    }

    suspend fun resolveMilestone(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val milestones = client.listMilestones(projectId)
        val normalized = raw.lowercase().trim()
        if (normalized in listOf("current", "actual", "active", "sprint actual", "current sprint", "hito actual", "activo", "este sprint")) {
            val active = milestones.firstOrNull { !it.closed } ?: milestones.lastOrNull()
            if (active != null) return active.id
        }

        val matched = milestones.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.slug.equals(raw, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Milestone/Sprint '$raw' not found in project. Available milestones: ${milestones.joinToString { "${it.name} (ID: ${it.id}, slug: ${it.slug})" }}"
        )
        return matched.id
    }

    suspend fun resolveAssignee(client: TaigaClient, projectId: Long, element: JsonElement?): Long? {
        val raw = extractString(element) ?: return null
        raw.toLongOrNull()?.let { return it }

        val normalized = raw.lowercase().trim()
        if (normalized in listOf("unassigned", "sin asignar", "sin asignación", "sin asignacion", "none", "nadie")) {
            return null
        }

        val memberships = runCatching { client.listMemberships(projectId) }.getOrDefault(emptyList())
        val matched = memberships.firstOrNull {
            it.fullName?.equals(raw, ignoreCase = true) == true ||
            it.userEmail?.equals(raw, ignoreCase = true) == true ||
            it.user?.toString() == raw ||
            it.fullName?.contains(raw, ignoreCase = true) == true ||
            it.userEmail?.substringBefore("@")?.equals(raw, ignoreCase = true) == true
        } ?: throw IllegalArgumentException(
            "Assignee '$raw' not found in project members. Available members: ${memberships.joinToString { "${it.fullName ?: it.userEmail} (ID: ${it.user})" }}"
        )
        return matched.user
    }
}
