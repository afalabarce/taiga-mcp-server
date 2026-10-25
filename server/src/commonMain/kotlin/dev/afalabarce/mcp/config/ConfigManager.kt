package dev.afalabarce.mcp.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConfigManager(
    initialConfig: TaigaConfig = loadFromEnvironment()
) {
    private val mutex = Mutex()
    private var currentConfig: TaigaConfig = initialConfig

    companion object {
        fun loadFromEnvironment(): TaigaConfig {
            val apiUrl = getEnv("TAIGA_API_URL")?.ifBlank { null } ?: "https://api.taiga.io"
            val authToken = getEnv("TAIGA_AUTH_TOKEN")?.ifBlank { null }
            val defaultProject = getEnv("TAIGA_DEFAULT_PROJECT")?.ifBlank { null }
            return TaigaConfig(
                apiUrl = apiUrl,
                authToken = authToken,
                defaultProject = defaultProject
            )
        }
    }

    suspend fun getConfig(): TaigaConfig = mutex.withLock {
        currentConfig
    }

    suspend fun updateConfig(update: TaigaConfigUpdateRequest): TaigaConfig = mutex.withLock {
        currentConfig = currentConfig.copy(
            apiUrl = update.apiUrl?.ifBlank { null } ?: currentConfig.apiUrl,
            authToken = update.authToken?.ifBlank { null } ?: currentConfig.authToken,
            defaultProject = update.defaultProject?.ifBlank { null } ?: currentConfig.defaultProject
        )
        currentConfig
    }

    suspend fun setAuthToken(token: String?): TaigaConfig = mutex.withLock {
        currentConfig = currentConfig.copy(authToken = token)
        currentConfig
    }

    suspend fun setDefaultProject(project: String?): TaigaConfig = mutex.withLock {
        currentConfig = currentConfig.copy(defaultProject = project)
        currentConfig
    }

    suspend fun resolveConfig(
        overrideApiUrl: String? = null,
        overrideAuthToken: String? = null,
        overrideProject: String? = null
    ): TaigaConfig = mutex.withLock {
        currentConfig.copy(
            apiUrl = overrideApiUrl?.ifBlank { null } ?: currentConfig.apiUrl,
            authToken = overrideAuthToken?.ifBlank { null } ?: currentConfig.authToken,
            defaultProject = overrideProject?.ifBlank { null } ?: currentConfig.defaultProject
        )
    }
}
