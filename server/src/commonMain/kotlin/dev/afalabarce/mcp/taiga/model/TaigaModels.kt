package dev.afalabarce.mcp.taiga.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TaigaAuthResponse(
    val id: Long,
    val username: String,
    @SerialName("auth_token") val authToken: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null
)

@Serializable
data class TaigaUser(
    val id: Long,
    val username: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("full_name_display") val fullNameDisplay: String? = null,
    val email: String? = null,
    val photo: String? = null,
    val bio: String? = null
)

@Serializable
data class TaigaProject(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String? = null,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("total_milestones") val totalMilestones: Int? = null,
    @SerialName("total_story_points") val totalStoryPoints: Double? = null,
    val tags: List<String> = emptyList(),
    @SerialName("my_permissions") val myPermissions: List<String> = emptyList()
)

@Serializable
data class TaigaExtraInfo(
    val id: Long? = null,
    val name: String? = null,
    val color: String? = null,
    @SerialName("full_name_display") val fullNameDisplay: String? = null,
    val username: String? = null,
    val subject: String? = null,
    val ref: Long? = null
)

@Serializable
data class TaigaEpic(
    val id: Long,
    val ref: Long? = null,
    val subject: String,
    val description: String? = null,
    val project: Long,
    val status: Long? = null,
    @SerialName("status_extra_info") val statusExtraInfo: TaigaExtraInfo? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_extra_info") val assignedToExtraInfo: TaigaExtraInfo? = null,
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val version: Int = 1,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("modified_date") val modifiedDate: String? = null
)

@Serializable
data class TaigaUserStory(
    val id: Long,
    val ref: Long? = null,
    val subject: String,
    val description: String? = null,
    val project: Long,
    val milestone: Long? = null,
    @SerialName("milestone_name") val milestoneName: String? = null,
    val status: Long? = null,
    @SerialName("status_extra_info") val statusExtraInfo: TaigaExtraInfo? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("total_points") val totalPoints: Double? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_extra_info") val assignedToExtraInfo: TaigaExtraInfo? = null,
    val tags: List<String> = emptyList(),
    val version: Int = 1,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("modified_date") val modifiedDate: String? = null
)

@Serializable
data class TaigaTask(
    val id: Long,
    val ref: Long? = null,
    val subject: String,
    val description: String? = null,
    val project: Long,
    @SerialName("user_story") val userStory: Long? = null,
    @SerialName("user_story_extra_info") val userStoryExtraInfo: TaigaExtraInfo? = null,
    val milestone: Long? = null,
    @SerialName("milestone_slug") val milestoneSlug: String? = null,
    val status: Long? = null,
    @SerialName("status_extra_info") val statusExtraInfo: TaigaExtraInfo? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_extra_info") val assignedToExtraInfo: TaigaExtraInfo? = null,
    val tags: List<String> = emptyList(),
    val version: Int = 1,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("modified_date") val modifiedDate: String? = null
)

@Serializable
data class TaigaIssue(
    val id: Long,
    val ref: Long? = null,
    val subject: String,
    val description: String? = null,
    val project: Long,
    val status: Long? = null,
    @SerialName("status_extra_info") val statusExtraInfo: TaigaExtraInfo? = null,
    val priority: Long? = null,
    val severity: Long? = null,
    val type: Long? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_extra_info") val assignedToExtraInfo: TaigaExtraInfo? = null,
    val tags: List<String> = emptyList(),
    val version: Int = 1,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("modified_date") val modifiedDate: String? = null
)

@Serializable
data class TaigaMilestone(
    val id: Long,
    val name: String,
    val slug: String,
    val project: Long,
    @SerialName("estimated_start") val estimatedStart: String? = null,
    @SerialName("estimated_finish") val estimatedFinish: String? = null,
    val closed: Boolean = false,
    @SerialName("total_points") val totalPoints: Double? = null,
    @SerialName("closed_points") val closedPoints: Double? = null,
    @SerialName("user_stories") val userStories: List<TaigaUserStory> = emptyList()
)

@Serializable
data class TaigaWikiPage(
    val id: Long? = null,
    val project: Long,
    val slug: String,
    val content: String? = null,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("modified_date") val modifiedDate: String? = null,
    val version: Int = 1
)

@Serializable
data class TaigaStatus(
    val id: Long,
    val name: String,
    val slug: String? = null,
    val order: Int = 0,
    @SerialName("is_closed") val isClosed: Boolean = false,
    val color: String? = null,
    val project: Long
)

@Serializable
data class TaigaPriority(
    val id: Long,
    val name: String,
    val order: Int = 0,
    val color: String? = null,
    val project: Long
)

@Serializable
data class TaigaSeverity(
    val id: Long,
    val name: String,
    val order: Int = 0,
    val color: String? = null,
    val project: Long
)

@Serializable
data class TaigaIssueType(
    val id: Long,
    val name: String,
    val order: Int = 0,
    val color: String? = null,
    val project: Long
)

@Serializable
data class TaigaProjectStatuses(
    val projectId: Long,
    val userStoryStatuses: List<TaigaStatus> = emptyList(),
    val taskStatuses: List<TaigaStatus> = emptyList(),
    val issueStatuses: List<TaigaStatus> = emptyList(),
    val priorities: List<TaigaPriority> = emptyList(),
    val severities: List<TaigaSeverity> = emptyList(),
    val issueTypes: List<TaigaIssueType> = emptyList()
)
