package io.taiga.mcp.taiga.client

import io.taiga.mcp.config.ConfigManager
import io.taiga.mcp.config.TaigaConfig

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
