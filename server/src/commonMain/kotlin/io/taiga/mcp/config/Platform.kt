package io.taiga.mcp.config

expect fun getEnv(name: String): String?
expect fun getSystemProperty(name: String): String?
expect fun setSystemProperty(name: String, value: String)
expect fun readFileTextIfExists(path: String): String?
expect fun fileExists(path: String): Boolean
expect fun configureLogbackFile(path: String): Boolean
