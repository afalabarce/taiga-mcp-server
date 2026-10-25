import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "dev.afalabarce.mcp"
version = providers.gradleProperty("serverVersion").orElse("1.0.0").get()

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // Ktor Server
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.serialization.json)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.server.sse)
            implementation(libs.ktor.server.call.logging)
            implementation(libs.ktor.server.default.headers)
            implementation(libs.ktor.server.auth)
            implementation(libs.ktor.server.swagger)
            implementation(libs.ktor.server.openapi)
            implementation(libs.ktor.server.routing.openapi)

            // Ktor Client
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization.json)
            implementation(libs.ktor.client.logging)

            // Logging & CLI
            implementation(libs.logback.classic)
            implementation(libs.clikt)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.server.test.host)
        }
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the MCP Taiga server application"
    mainClass.set("dev.afalabarce.mcp.MainKt")
    val jvmTarget = kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
    val compilation = jvmTarget.compilations.getByName("main")
    classpath(compilation.output.allOutputs, configurations.getByName("jvmRuntimeClasspath"))
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Packages a standalone runnable fat JAR containing all dependencies"
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val jvmTarget = kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
    val compilation = jvmTarget.compilations.getByName("main")

    from(compilation.output.allOutputs)
    from({
        configurations.getByName("jvmRuntimeClasspath").filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "dev.afalabarce.mcp.MainKt"
    }
}

val createStartScripts = tasks.register("createStartScripts") {
    val outputDir = layout.buildDirectory.dir("scripts")
    outputs.dir(outputDir)
    doLast {
        val binDir = outputDir.get().asFile
        binDir.mkdirs()

        val scriptContent = """
            #!/bin/sh
            PRG="${'$'}0"
            while [ -h "${'$'}PRG" ]; do
                ls=`ls -ld "${'$'}PRG"`
                link=`expr "${'$'}ls" : '.*-> \(.*\)${'$'}'`
                if expr "${'$'}link" : '/.*' > /dev/null; then
                    PRG="${'$'}link"
                else
                    PRG=`dirname "${'$'}PRG"`/"${'$'}link"
                fi
            done
            APP_DIR=`cd "$(dirname "${'$'}PRG")" && pwd -P`

            if [ -d "${'$'}APP_DIR/lib" ]; then
                APP_HOME="${'$'}APP_DIR"
            elif [ -d "${'$'}APP_DIR/../lib" ]; then
                APP_HOME="$(cd "${'$'}APP_DIR/.." && pwd -P)"
            else
                APP_HOME="${'$'}APP_DIR"
            fi

            if [ -n "${'$'}JAVA_HOME" ] && [ -x "${'$'}JAVA_HOME/bin/java" ]; then
                JAVACMD="${'$'}JAVA_HOME/bin/java"
            elif [ -x "/usr/bin/java" ]; then
                JAVACMD="/usr/bin/java"
            else
                JAVACMD="java"
            fi

            if [ -f "${'$'}APP_HOME/server-standalone.jar" ]; then
                JAR_PATH="${'$'}APP_HOME/server-standalone.jar"
            elif [ -f "${'$'}APP_HOME/lib/server-standalone.jar" ]; then
                JAR_PATH="${'$'}APP_HOME/lib/server-standalone.jar"
            else
                JAR_PATH="${'$'}APP_HOME/server-standalone.jar"
            fi

            exec "${'$'}JAVACMD" ${'$'}JAVA_OPTS ${'$'}SERVER_OPTS -jar "${'$'}JAR_PATH" "${'$'}@"
        """.trimIndent() + "\n"

        val batScriptContent = """
            @echo off
            set DIRNAME=%~dp0
            if exist "%DIRNAME%lib" (
                set APP_HOME=%DIRNAME%
            ) else (
                set APP_HOME=%DIRNAME%..
            )
            if exist "%APP_HOME%\server-standalone.jar" (
                set JAR_PATH=%APP_HOME%\server-standalone.jar
            ) else (
                set JAR_PATH=%APP_HOME%\lib\server-standalone.jar
            )
            java %JAVA_OPTS% %SERVER_OPTS% -jar "%JAR_PATH%" %*
        """.trimIndent() + "\r\n"

        // Script in bin/ directory
        val binSubDir = File(binDir, "bin")
        binSubDir.mkdirs()
        val shBinScript = File(binSubDir, "server")
        shBinScript.writeText(scriptContent)
        shBinScript.setExecutable(true, false)
        val batBinScript = File(binSubDir, "server.bat")
        batBinScript.writeText(batScriptContent)

        // Script at root level (so /srv/taiga-mcp/server works directly)
        val shRootScript = File(binDir, "server")
        shRootScript.writeText(scriptContent)
        shRootScript.setExecutable(true, false)
        val batRootScript = File(binDir, "server.bat")
        batRootScript.writeText(batScriptContent)
    }
}

val distZip = tasks.register<Zip>("distZip") {
    group = "distribution"
    description = "Bundles the server application as a distribution zip"
    dependsOn("fatJar", createStartScripts)

    archiveBaseName.set("server")
    archiveClassifier.set("")
    archiveVersion.set(providers.gradleProperty("serverVersion").orElse("1.0.0"))

    val baseDir = "server-${archiveVersion.get()}"

    into(baseDir) {
        into("lib") {
            from(tasks.named<Jar>("fatJar").flatMap { it.archiveFile }) {
                rename { "server-standalone.jar" }
            }
        }
        into("bin") {
            from(createStartScripts.map { File(it.outputs.files.singleFile, "bin") })
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
        from(createStartScripts.map { 
            listOf(
                File(it.outputs.files.singleFile, "server"),
                File(it.outputs.files.singleFile, "server.bat")
            )
        }) {
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
        from(tasks.named<Jar>("fatJar").flatMap { it.archiveFile }) {
            rename { "server-standalone.jar" }
        }
    }
}

tasks.register<ServerDeployTask>("deploy") {
    group = "deployment"
    description = "Deploys the server distribution zip to the remote server via SFTP and executes deployment commands via SSH"
    dependsOn("distZip")
    zipFile.set(tasks.named<Zip>("distZip").flatMap { it.archiveFile })
    applicationYamlFile.set(layout.projectDirectory.file("src/commonMain/resources/application.yaml"))
    logbackXmlFile.set(layout.projectDirectory.file("src/commonMain/resources/logback.xml"))
    userServiceFile.set(rootProject.layout.projectDirectory.file("systemd/taiga-mcp-user.service"))
}
