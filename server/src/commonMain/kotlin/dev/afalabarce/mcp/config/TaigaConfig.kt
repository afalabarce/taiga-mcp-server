package dev.afalabarce.mcp.config

import kotlinx.serialization.Serializable

@Serializable
data class TaigaConfig(
    val apiUrl: String = "https://api.taiga.io",
    val authToken: String? = null,
    val defaultProject: String? = null
) {
    fun normalizedApiUrl(): String {
        val trimmed = apiUrl.trim()
        return if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
    }

    fun masked(): TaigaConfigResponse {
        val maskedToken = authToken?.let {
            if (it.length > 8) "${it.take(4)}...${it.takeLast(4)}" else "***"
        }
        return TaigaConfigResponse(
            apiUrl = apiUrl,
            hasAuthToken = !authToken.isNullOrBlank(),
            maskedToken = maskedToken,
            defaultProject = defaultProject
        )
    }
}

@Serializable
data class TaigaConfigUpdateRequest(
    val apiUrl: String? = null,
    val authToken: String? = null,
    val defaultProject: String? = null
)

@Serializable
data class TaigaConfigResponse(
    val apiUrl: String,
    val hasAuthToken: Boolean,
    val maskedToken: String?,
    val defaultProject: String?
)

@Serializable
data class TaigaLoginRequest(
    val username: String,
    val password: String,
    val type: String = "normal",
    val apiUrl: String? = null
)

@Serializable
data class TaigaLoginResponse(
    val success: Boolean,
    val message: String,
    val authToken: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val email: String? = null
)
