import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory

abstract class ServerDeployTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val zipFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val applicationYamlFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val logbackXmlFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val userServiceFile: RegularFileProperty

    @TaskAction
    fun deploy() {
        val out: StyledTextOutput = services.get(StyledTextOutputFactory::class.java).create("ServerDeployTask")

        val host = System.getenv("TAIGA_MCP_SERVER")
        val user = System.getenv("TAIGA_MCP_SHELL_USER")
        val pass = System.getenv("TAIGA_MCP_SHELL_PASS")

        if (host.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
            throw GradleException(
                "Environment variables TAIGA_MCP_SERVER, TAIGA_MCP_SHELL_USER, and TAIGA_MCP_SHELL_PASS must be defined in the system environment."
            )
        }

        val fileToUpload = zipFile.get().asFile
        if (!fileToUpload.exists()) {
            throw GradleException("Distribution zip file does not exist at: ${fileToUpload.absolutePath}")
        }

        val port = System.getenv("TAIGA_MCP_SHELL_PORT")?.toIntOrNull() ?: 22
        val serviceName = System.getenv("TAIGA_MCP_SERVICE") ?: "taiga-mcp.service"
        val zipFileName = fileToUpload.name

        val jsch = JSch()
        val session: Session = jsch.getSession(user, host, port).apply {
            setPassword(pass)
            setConfig("StrictHostKeyChecking", "no")
            connect(30000)
        }

        try {
            // 1. Subiendo archivo <archivo.zip> y configuraciones base...
            runStep(out, "Subiendo archivo $zipFileName") {
                val sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect(30000)
                try {
                    sftpChannel.put(fileToUpload.absolutePath, zipFileName, ChannelSftp.OVERWRITE)

                    val yamlFile = applicationYamlFile.orNull?.asFile
                    if (yamlFile != null && yamlFile.exists()) {
                        sftpChannel.put(yamlFile.absolutePath, "application.yaml.tmp", ChannelSftp.OVERWRITE)
                    }

                    val logbackFile = logbackXmlFile.orNull?.asFile
                    if (logbackFile != null && logbackFile.exists()) {
                        sftpChannel.put(logbackFile.absolutePath, "logback.xml.tmp", ChannelSftp.OVERWRITE)
                    }

                    val serviceFile = userServiceFile.orNull?.asFile
                    if (serviceFile != null && serviceFile.exists()) {
                        sftpChannel.put(serviceFile.absolutePath, "taiga-mcp.service.tmp", ChannelSftp.OVERWRITE)
                    }
                } finally {
                    sftpChannel.disconnect()
                }
            }

            // 2. Descomprimiendo archivo...
            runStep(out, "Descomprimiendo archivo") {
                executeSshCommand(session, "unzip -o -L $zipFileName")
            }

            // 3. Moviendo librerías de servicio a la raiz (sobrescribiendo siempre)...
            runStep(out, "Moviendo librerías de servicio a la raiz") {
                val moveCommand = "extractedDir=\$(ls -d */ 2>/dev/null | grep -E '^server-' | head -n 1 | tr -d '/'); " +
                        "if [ -z \"\$extractedDir\" ]; then extractedDir=\$(basename \"$zipFileName\" .zip | tr '[:upper:]' '[:lower:]'); fi; " +
                        "if [ -n \"\$extractedDir\" ] && [ -d \"\$extractedDir\" ]; then cp -rf \"\$extractedDir\"/* . && chmod +x bin/* 2>/dev/null || true; fi"
                executeSshCommand(session, moveCommand)
            }

            // 4. Configurando carpeta resources y archivos de configuración (solo si no existen en el servidor)...
            runStep(out, "Configurando carpeta resources y servicio") {
                val resourcesCommand = "mkdir -p resources ~/.config/systemd/user && " +
                        "if [ ! -f resources/application.yaml ] && [ -f application.yaml.tmp ]; then cp application.yaml.tmp resources/application.yaml; fi && " +
                        "if [ ! -f resources/logback.xml ] && [ -f logback.xml.tmp ]; then cp logback.xml.tmp resources/logback.xml; fi && " +
                        "if [ ! -f ~/.config/systemd/user/$serviceName ] && [ -f taiga-mcp.service.tmp ]; then cp taiga-mcp.service.tmp ~/.config/systemd/user/$serviceName && systemctl --user enable $serviceName; fi"
                executeSshCommand(session, resourcesCommand)
            }

            // 5. Eliminando archivos y carpetas temporales...
            runStep(out, "Eliminando archivos y carpetas temporales") {
                val deleteCommand = "extractedDir=\$(ls -d */ 2>/dev/null | grep -E '^server-' | head -n 1 | tr -d '/'); " +
                        "if [ -z \"\$extractedDir\" ]; then extractedDir=\$(basename \"$zipFileName\" .zip | tr '[:upper:]' '[:lower:]'); fi; " +
                        "rm -f \"$zipFileName\" application.yaml.tmp logback.xml.tmp taiga-mcp.service.tmp; " +
                        "if [ -n \"\$extractedDir\" ] && [ -d \"\$extractedDir\" ]; then rm -rf \"\$extractedDir\"; fi"
                executeSshCommand(session, deleteCommand)
            }

            // 6. Reiniciando servicio...
            runStep(out, "Reiniciando servicio") {
                val restartCommand = "systemctl --user daemon-reload && systemctl --user restart $serviceName"
                executeSshCommand(session, restartCommand)
            }
        } finally {
            session.disconnect()
        }
    }

    private fun runStep(
        out: StyledTextOutput,
        stepMessage: String,
        action: () -> Unit
    ) {
        out.text("$stepMessage... ")
        try {
            action()
            out.withStyle(StyledTextOutput.Style.Success).println("OK")
        } catch (e: Exception) {
            out.withStyle(StyledTextOutput.Style.Failure).println("ERROR")
            throw e
        }
    }

    private fun executeSshCommand(session: Session, command: String) {
        val execChannel = session.openChannel("exec") as ChannelExec
        execChannel.setCommand(command)
        execChannel.setOutputStream(System.out, true)
        execChannel.setErrStream(System.err, true)

        execChannel.connect(30000)

        while (!execChannel.isClosed) {
            Thread.sleep(100)
        }

        val exitStatus = execChannel.exitStatus
        execChannel.disconnect()

        if (exitStatus != 0) {
            throw GradleException("Remote SSH command failed with exit code $exitStatus: $command")
        }
    }
}
