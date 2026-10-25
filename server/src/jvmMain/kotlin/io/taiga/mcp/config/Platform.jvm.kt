package io.taiga.mcp.config

actual fun getEnv(name: String): String? = System.getenv(name)
