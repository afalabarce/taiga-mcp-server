package dev.afalabarce.mcp.taiga.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

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
    val tags: List<JsonElement> = emptyList(),
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
    val tags: List<JsonElement> = emptyList(),
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
    val points: JsonObject? = null,
    @SerialName("assigned_to") val assignedTo: Long? = null,
    @SerialName("assigned_to_extra_info") val assignedToExtraInfo: TaigaExtraInfo? = null,
    val tags: List<JsonElement> = emptyList(),
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
    val tags: List<JsonElement> = emptyList(),
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
    val tags: List<JsonElement> = emptyList(),
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
data class TaigaRole(
    val id: Long,
    val name: String,
    val slug: String? = null,
    val order: Int = 0,
    val project: Long? = null
)

@Serializable
data class TaigaPoint(
    val id: Long,
    val name: String,
    val value: Double,
    val role: Long,
    val order: Int = 0
)

@Serializable
data class TaigaProjectStatuses(
    val projectId: Long,
    val userStoryStatuses: List<TaigaStatus> = emptyList(),
    val taskStatuses: List<TaigaStatus> = emptyList(),
    val issueStatuses: List<TaigaStatus> = emptyList(),
    val epicStatuses: List<TaigaStatus> = emptyList(),
    val priorities: List<TaigaPriority> = emptyList(),
    val severities: List<TaigaSeverity> = emptyList(),
    val issueTypes: List<TaigaIssueType> = emptyList(),
    val roles: List<TaigaRole> = emptyList(),
    val points: List<TaigaPoint> = emptyList()
)

@Serializable
data class TaigaSearchResult(
    val count: Int = 0,
    val epics: List<TaigaEpic> = emptyList(),
    @SerialName("userstories") val userStories: List<TaigaUserStory> = emptyList(),
    val tasks: List<TaigaTask> = emptyList(),
    val issues: List<TaigaIssue> = emptyList(),
    @SerialName("wikipages") val wikiPages: List<TaigaWikiPage> = emptyList()
)

@Serializable
data class TaigaMembership(
    val id: Long,
    val user: Long? = null,
    @SerialName("user_email") val userEmail: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("role_name") val roleName: String? = null,
    val role: Long? = null,
    @SerialName("is_owner") val isOwner: Boolean = false,
    val color: String? = null,
    @SerialName("project_name") val projectName: String? = null
)

@Serializable
data class TaigaHistoryEntry(
    val id: String? = null,
    @SerialName("user") val user: TaigaExtraInfo? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val comment: String? = null,
    @SerialName("comment_html") val commentHtml: String? = null,
    @SerialName("diff") val diff: JsonElement? = null,
    val values: JsonElement? = null
)

@Serializable
data class TaigaProjectSummary(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String? = null,
    val totalMilestones: Int = 0,
    val totalStoryPoints: Double? = null,
    val activeSprints: List<String> = emptyList(),
    val userStoryCountByStatus: Map<String, Int> = emptyMap(),
    val taskCountByStatus: Map<String, Int> = emptyMap(),
    val issueCountByStatus: Map<String, Int> = emptyMap(),
    val members: List<String> = emptyList()
)

@Serializable
data class TaigaAttachment(
    val id: Long,
    val name: String,
    val size: Long? = null,
    val url: String? = null,
    @SerialName("thumbnail_card_url") val thumbnailCardUrl: String? = null,
    val description: String? = null,
    @SerialName("attached_file") val attachedFile: String? = null,
    @SerialName("is_deprecated") val isDeprecated: Boolean = false,
    @SerialName("from_comment") val fromComment: Boolean = false,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("modified_date") val modifiedDate: String? = null,
    @SerialName("object_id") val objectId: Long? = null,
    val project: Long? = null,
    val owner: Long? = null,
    val sha1: String? = null
)

@Serializable
data class TaigaCustomAttribute(
    val id: Long,
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val order: Int = 0,
    val project: Long
)

@Serializable
data class TaigaCustomAttributesValues(
    @SerialName("attributes_values") val attributesValues: JsonObject = buildJsonObject { },
    val version: Int = 1
)

@Serializable
data class TaigaEpicRelatedUserStory(
    val id: Long,
    val epic: Long,
    @SerialName("user_story") val userStory: Long,
    val order: Long? = null,
    @SerialName("user_story_extra_info") val userStoryExtraInfo: TaigaExtraInfo? = null
)

@Serializable
data class TaigaWikiLink(
    val id: Long,
    val title: String,
    val href: String,
    val order: Long? = null,
    val project: Long
)

@Serializable
data class TaigaSwimlane(
    val id: Long,
    val name: String,
    val project: Long,
    val order: Long = 0
)

@Serializable
data class TaigaDueDate(
    val id: Long,
    val name: String,
    val color: String? = null,
    @SerialName("days_to_due") val daysToDue: Int? = null,
    @SerialName("by_default") val byDefault: Boolean = false,
    val project: Long,
    val order: Int = 0
)

@Serializable
data class TaigaWebhook(
    val id: Long,
    val name: String,
    val url: String,
    val key: String? = null,
    val project: Long
)

@Serializable
data class TaigaWebhookLog(
    val id: Long,
    val webhook: Long,
    val url: String,
    val status: Int,
    val duration: Double? = null,
    val created: String? = null
)

@Serializable
data class TaigaWebNotification(
    val id: Long,
    val type: String? = null,
    val read: String? = null,
    val data: JsonElement? = null,
    val created: String? = null
)

@Serializable
data class TaigaNotifyPolicy(
    val id: Long,
    val project: Long,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("notify_level") val notifyLevel: Int = 1
)

@Serializable
data class TaigaProjectTemplate(
    val id: Long,
    val name: String,
    val slug: String? = null,
    val description: String? = null,
    val order: Int = 0
)

@Serializable
data class TaigaCommentVersion(
    val id: String? = null,
    val comment: String? = null,
    @SerialName("comment_html") val commentHtml: String? = null,
    val user: TaigaExtraInfo? = null,
    val date: String? = null
)

@Serializable
data class TaigaTimelineEntry(
    val id: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
    val eventType: String? = null,
    val created: String? = null,
    val data: JsonElement? = null
)

@Serializable
data class TaigaVoter(
    val id: Long,
    val username: String,
    @SerialName("full_name_display") val fullNameDisplay: String? = null,
    val photo: String? = null
)

@Serializable
data class TaigaWatcher(
    val id: Long,
    val username: String,
    @SerialName("full_name_display") val fullNameDisplay: String? = null,
    @SerialName("full_name") val fullName: String? = null
)
