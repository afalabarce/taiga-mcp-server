package io.taiga.mcp.config

import java.io.File

actual fun getEnv(name: String): String? = System.getenv(name)

actual fun getSystemProperty(name: String): String? = System.getProperty(name)

actual fun setSystemProperty(name: String, value: String) {
    System.setProperty(name, value)
}

actual fun readFileTextIfExists(path: String): String? {
    val file = File(path)
    return if (file.exists() && file.isFile) file.readText() else null
}

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun configureLogbackFile(path: String): Boolean {
    val file = File(path)
    if (!file.exists() || !file.isFile) return false
    return try {
        val loggerContext = org.slf4j.LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext ?: return false
        val configurator = ch.qos.logback.classic.joran.JoranConfigurator()
        configurator.context = loggerContext
        loggerContext.reset()
        configurator.doConfigure(file)
        loggerContext.start()
        true
    } catch (e: Exception) {
        System.err.println("Warning: Failed to configure Logback from ${file.absolutePath}: ${e.message}")
        false
    }
}
