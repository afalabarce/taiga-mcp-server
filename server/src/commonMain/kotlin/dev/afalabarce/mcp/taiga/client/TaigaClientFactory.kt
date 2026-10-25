package dev.afalabarce.mcp.taiga.client

import dev.afalabarce.mcp.config.ConfigManager
import dev.afalabarce.mcp.config.TaigaConfig

class TaigaClientFactory(
    private val configManager: ConfigManager
) {
    suspend fun getClient(
        overrideApiUrl: String? = null,
        overrideAuthToken: String? = null,
        overrideProject: String? = null
    ): TaigaClient {
        val config = configManager.resolveConfig(
            overrideApiUrl = overrideApiUrl,
            overrideAuthToken = overrideAuthToken,
            overrideProject = overrideProject
        )
        return TaigaClient(config)
    }

    suspend fun getActiveClient(): TaigaClient {
        val config = configManager.getConfig()
        return TaigaClient(config)
    }
}
