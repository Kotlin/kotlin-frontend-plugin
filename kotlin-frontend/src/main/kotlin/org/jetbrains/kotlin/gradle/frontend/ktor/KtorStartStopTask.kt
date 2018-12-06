package org.jetbrains.kotlin.gradle.frontend.ktor

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.frontend.KotlinNewMpp
import org.jetbrains.kotlin.gradle.frontend.servers.AbstractStartStopTask
import org.jetbrains.kotlin.gradle.frontend.util.LogTail
import org.jetbrains.kotlin.gradle.frontend.util.whereIs
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.URL

/**
 * @author Sergey Mashkov
 */
open class KtorStartStopTask : AbstractStartStopTask<Int>() {
    @Input
    var start: Boolean = true

    @Internal
    private val ext = project.extensions.getByType(KtorExtension::class.java)

    @get:Input
    val port: Int
        get() = ext.port ?: throw IllegalArgumentException("ktor port not configured")

    @get:Input
    val jvmOptions: Array<String>
        get() = ext.jvmOptions

    @get:Input
    val workDir: File
        get() = ext.workDir?.let { project.file(it) } ?: project.projectDir

    private val logTailer = LogTail({ serverLog().toPath() })

    @Input
    var shutdownPath = "/ktor/application/shutdown"

    @get: Input
    val mainClass: String
        get() = ext.mainClass ?: "org.jetbrains.ktor.jetty.DevelopmentHost"

    init {
        logTailer.rememberLogStartPosition()
    }

    override val identifier = "ktor"
    override fun checkIsRunning(stopInfo: Int?): Boolean {
        return stopInfo != null &&
                try {
                    Socket("localhost", stopInfo).close()
                    true
                } catch (e: IOException) {
                    false
                }
    }

    override fun beforeStart() = port

    override fun serverLog(logsDir: File) = logsDir.resolve("$identifier-$port.log")

    override fun builder(): ProcessBuilder {
        val gradleJavaHome = project.findProperty("org.gradle.java.home")?.let { listOf(it.toString() + File.separator + "bin") } ?: emptyList()

        return ProcessBuilder(
                listOf<String>()
                        + whereIs("java", gradleJavaHome).first().absolutePath
                        + "-cp"
                        + classPath()
                        + ktorOptions()
                        + jvmOptions
                        + mainClass
        ).directory(workDir)
    }

    private fun ktorOptions(): List<String> {
        return listOf(
                "-Dktor.deployment.port=$port",
                "-Dktor.deployment.autoreload=true",
                "-Dktor.deployment.shutdown.url=$shutdownPath")
    }

    private fun classPath() = classPathFiles(project).distinct().joinToString(File.pathSeparator) { it.absolutePath }

    private fun classPathFiles(project: Project) =
            try {
                Class.forName("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")
                // This line executed only on Kotlin 1.2.70+
                KotlinNewMpp.ktorClassPath(project)
            } catch (e: ClassNotFoundException) {
                null
            } ?: (jars() + mainOutput())

    private fun mainOutput() =
            project.convention.findPlugin(JavaPluginConvention::class.java)
                    ?.sourceSets?.getByName("main")
                    ?.output?.toList().orEmpty()

    private fun jars() =
            project.configurations
                    .filter { it.isCanBeResolved }
                    .flatMap { it.files.filter { it.canRead() && it.extension == "jar" } }

    override fun stop(state: Int?) {
        if (state != null) {
            try {
                URL("http://localhost:$port$shutdownPath").readText()
            } catch (ignore: IOException) {
            }
        }
    }

    override fun readState(file: File) = file.readText().trim().toInt()
    override fun writeState(file: File, state: Int) {
        file.writeText("$state\n")
    }

    @TaskAction
    fun start() {
        if (start) {
            try {
                doStart()
            } catch (t: Throwable) {
                logTailer.dumpLog()
                throw t
            }
        } else {
            doStop()
        }
    }
}