plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

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
