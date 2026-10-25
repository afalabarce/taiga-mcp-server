package io.taiga.mcp.config

/**
 * Result of resolving server configuration files (application.yaml and logback.xml)
 * and options from SERVER_OPTS, system properties, and environment variables.
 */
data class ResolvedServerConfig(
    val applicationYamlPath: String? = null,
    val logbackXmlPath: String? = null,
    val yamlProperties: Map<String, String> = emptyMap(),
    val serverOptsRaw: String? = null
)

object ServerOptsParser {

    data class ParsedOpts(
        val applicationYamlPath: String? = null,
        val logbackXmlPath: String? = null,
        val systemProperties: Map<String, String> = emptyMap()
    )

    /**
     * Parses the SERVER_OPTS string into system properties and file path overrides.
     */
    fun parse(serverOpts: String?): ParsedOpts {
        if (serverOpts.isNullOrBlank()) return ParsedOpts()

        var yamlPath: String? = null
        var logbackPath: String? = null
        val props = mutableMapOf<String, String>()

        val tokens = tokenize(serverOpts)
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.startsWith("-D") -> {
                    val kv = token.substring(2)
                    val eqIndex = kv.indexOf('=')
                    if (eqIndex != -1) {
                        val key = kv.substring(0, eqIndex).trim()
                        val value = kv.substring(eqIndex + 1).trim().trim('\'', '"')
                        props[key] = value
                        when (key.lowercase()) {
                            "config.file", "ktor.config", "application.yaml", "config" -> yamlPath = value
                            "logback.configurationfile", "logback.xml", "logback" -> logbackPath = value
                        }
                    }
                }
                token.startsWith("--config=") || token.startsWith("-config=") -> {
                    yamlPath = token.substringAfter('=').trim('\'', '"')
                }
                token == "--config" || token == "-config" -> {
                    if (i + 1 < tokens.size) {
                        yamlPath = tokens[++i].trim('\'', '"')
                    }
                }
                token.startsWith("--logback=") || token.startsWith("-logback=") -> {
                    logbackPath = token.substringAfter('=').trim('\'', '"')
                }
                token == "--logback" || token == "-logback" -> {
                    if (i + 1 < tokens.size) {
                        logbackPath = tokens[++i].trim('\'', '"')
                    }
                }
                token.endsWith("application.yaml") || token.endsWith("application.yml") -> {
                    yamlPath = token.trim('\'', '"')
                }
                token.endsWith("logback.xml") -> {
                    logbackPath = token.trim('\'', '"')
                }
            }
            i++
        }

        return ParsedOpts(
            applicationYamlPath = yamlPath,
            logbackXmlPath = logbackPath,
            systemProperties = props
        )
    }

    /**
     * Resolves and loads application.yaml and logback.xml based on:
     * 1. SERVER_OPTS
     * 2. System properties (config.file, logback.configurationFile)
     * 3. Environment variables (CONFIG_FILE, LOGBACK_CONFIGURATION_FILE)
     * 4. Conventional default paths (resources/application.yaml, resources/logback.xml)
     */
    fun resolve(serverOptsEnv: String? = getEnv("SERVER_OPTS")): ResolvedServerConfig {
        val parsed = parse(serverOptsEnv)

        // Apply system properties
        for ((k, v) in parsed.systemProperties) {
            setSystemProperty(k, v)
        }

        // 1. Resolve logback.xml path
        val logbackCandidates = listOfNotNull(
            parsed.logbackXmlPath,
            getSystemProperty("logback.configurationFile"),
            getEnv("LOGBACK_CONFIGURATION_FILE"),
            getEnv("LOGBACK_XML"),
            "resources/logback.xml",
            "server/src/commonMain/resources/logback.xml",
            "logback.xml"
        )
        val resolvedLogbackPath = logbackCandidates.firstOrNull { fileExists(it) }

        // Configure logback if resolved
        if (resolvedLogbackPath != null) {
            configureLogbackFile(resolvedLogbackPath)
        }

        // 2. Resolve application.yaml path
        val yamlCandidates = listOfNotNull(
            parsed.applicationYamlPath,
            getSystemProperty("config.file"),
            getSystemProperty("ktor.config"),
            getSystemProperty("application.yaml"),
            getEnv("CONFIG_FILE"),
            getEnv("APPLICATION_YAML"),
            "resources/application.yaml",
            "server/src/commonMain/resources/application.yaml",
            "application.yaml"
        )
        val resolvedYamlPath = yamlCandidates.firstOrNull { fileExists(it) }

        val yamlProps = if (resolvedYamlPath != null) {
            val content = readFileTextIfExists(resolvedYamlPath)
            if (content != null) parseYaml(content) else emptyMap()
        } else {
            emptyMap()
        }

        return ResolvedServerConfig(
            applicationYamlPath = resolvedYamlPath,
            logbackXmlPath = resolvedLogbackPath,
            yamlProperties = yamlProps,
            serverOptsRaw = serverOptsEnv
        )
    }

    /**
     * Parses standard YAML into a flattened key-value map.
     * Supports nested blocks and flat notation.
     */
    fun parseYaml(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val currentPrefixes = mutableListOf<Pair<Int, String>>()

        for (rawLine in content.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val indent = rawLine.indexOfFirst { !it.isWhitespace() }
            while (currentPrefixes.isNotEmpty() && currentPrefixes.last().first >= indent) {
                currentPrefixes.removeAt(currentPrefixes.size - 1)
            }

            val colonIndex = trimmed.indexOf(':')
            if (colonIndex != -1) {
                val key = trimmed.substring(0, colonIndex).trim()
                val rawValue = trimmed.substring(colonIndex + 1).trim()

                if (rawValue.isEmpty() || rawValue.startsWith("#")) {
                    currentPrefixes.add(indent to key)
                } else {
                    val fullKey = if (currentPrefixes.isEmpty()) {
                        key
                    } else {
                        currentPrefixes.joinToString(".") { it.second } + ".$key"
                    }
                    var cleanValue = rawValue.split("#")[0].trim()
                    if ((cleanValue.startsWith("\"") && cleanValue.endsWith("\"")) ||
                        (cleanValue.startsWith("'") && cleanValue.endsWith("'"))
                    ) {
                        cleanValue = cleanValue.substring(1, cleanValue.length - 1)
                    }
                    result[fullKey] = cleanValue
                }
            }
        }
        return result
    }

    private fun tokenize(text: String): List<String> {
        val list = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in text) {
            when {
                (ch == '\'' || ch == '"') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = ch
                }
                ch == quoteChar && inQuotes -> {
                    inQuotes = false
                    quoteChar = ' '
                }
                ch.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        list.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            list.add(current.toString())
        }
        return list
    }
}
