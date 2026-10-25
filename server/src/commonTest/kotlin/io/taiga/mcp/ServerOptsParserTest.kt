package io.taiga.mcp

import io.taiga.mcp.config.ServerOptsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ServerOptsParserTest {

    @Test
    fun testParseUserSrvServerOptsExample() {
        val serverOpts = "-Dconfig.file=/srv/taiga-mcp/resources/application.yaml -Dlogback.configurationFile=/srv/taiga-mcp/resources/logback.xml"
        val parsed = ServerOptsParser.parse(serverOpts)

        assertEquals("/srv/taiga-mcp/resources/application.yaml", parsed.applicationYamlPath)
        assertEquals("/srv/taiga-mcp/resources/logback.xml", parsed.logbackXmlPath)
    }

    @Test
    fun testParseStandardServerOptsWithSystemProperties() {
        val serverOpts = "-Dconfig.file=/etc/custom/application.yaml -Dlogback.configurationFile=/etc/custom/logback.xml -Dcustom.prop=value123"
        val parsed = ServerOptsParser.parse(serverOpts)

        assertEquals("/etc/custom/application.yaml", parsed.applicationYamlPath)
        assertEquals("/etc/custom/logback.xml", parsed.logbackXmlPath)
        assertEquals("value123", parsed.systemProperties["custom.prop"])
    }

    @Test
    fun testParseCommandLineFlagsFormat() {
        val serverOpts = "--config /var/conf/application.yaml --logback /var/conf/logback.xml"
        val parsed = ServerOptsParser.parse(serverOpts)

        assertEquals("/var/conf/application.yaml", parsed.applicationYamlPath)
        assertEquals("/var/conf/logback.xml", parsed.logbackXmlPath)
    }

    @Test
    fun testParseQuotedValues() {
        val serverOpts = "-Dconfig.file=\"/path with spaces/application.yaml\" -Dlogback.configurationFile='/path with spaces/logback.xml'"
        val parsed = ServerOptsParser.parse(serverOpts)

        assertEquals("/path with spaces/application.yaml", parsed.applicationYamlPath)
        assertEquals("/path with spaces/logback.xml", parsed.logbackXmlPath)
    }

    @Test
    fun testParseNullOrBlankReturnsDefaults() {
        val parsedNull = ServerOptsParser.parse(null)
        assertNull(parsedNull.applicationYamlPath)
        assertNull(parsedNull.logbackXmlPath)

        val parsedBlank = ServerOptsParser.parse("   ")
        assertNull(parsedBlank.applicationYamlPath)
        assertNull(parsedBlank.logbackXmlPath)
    }

    @Test
    fun testParseYamlNestedAndFlat() {
        val yamlContent = """
            # Server configuration
            server:
              host: "127.0.0.1"
              port: 9090

            # Taiga parameters
            taiga:
              apiUrl: "https://custom.taiga.io"
              authToken: "my_secret_token"
              defaultProject: "my-custom-project"
        """.trimIndent()

        val parsedMap = ServerOptsParser.parseYaml(yamlContent)

        assertEquals("127.0.0.1", parsedMap["server.host"])
        assertEquals("9090", parsedMap["server.port"])
        assertEquals("https://custom.taiga.io", parsedMap["taiga.apiUrl"])
        assertEquals("my_secret_token", parsedMap["taiga.authToken"])
        assertEquals("my-custom-project", parsedMap["taiga.defaultProject"])
    }
}
