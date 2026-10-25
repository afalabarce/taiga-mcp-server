import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

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
    mainClass.set("io.taiga.mcp.MainKt")
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
        attributes["Main-Class"] = "io.taiga.mcp.MainKt"
    }
}

val createStartScripts = tasks.register("createStartScripts") {
    val outputDir = layout.buildDirectory.dir("scripts")
    outputs.dir(outputDir)
    doLast {
        val binDir = outputDir.get().asFile
        binDir.mkdirs()
        val shScript = File(binDir, "server")
        shScript.writeText(
            """
            #!/bin/sh
            DIRNAME="${'$'}(dirname "${'$'}0")"
            APP_HOME="${'$'}(cd "${'$'}DIRNAME/.." && pwd)"
            exec java ${'$'}JAVA_OPTS -jar "${'$'}APP_HOME/lib/server-standalone.jar" "${'$'}@"
            """.trimIndent() + "\n"
        )
        shScript.setExecutable(true, false)

        val batScript = File(binDir, "server.bat")
        batScript.writeText(
            """
            @echo off
            set DIRNAME=%~dp0
            set APP_HOME=%DIRNAME%..
            java %JAVA_OPTS% -jar "%APP_HOME%\lib\server-standalone.jar" %*
            """.trimIndent() + "\r\n"
        )
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
            from(createStartScripts.map { it.outputs.files })
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
