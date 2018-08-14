package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import org.codehaus.groovy.runtime.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.servers.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*
import java.net.*
import java.nio.file.attribute.*
import java.security.*

/**
 * @author Sergey Mashkov
 */
open class WebPackRunTask : AbstractStartStopTask<WebPackRunTask.State>() {
    @Input
    var start: Boolean = true

    @get:Nested
    private val config by lazy { project.frontendExtension.bundles().filterIsInstance<WebPackExtension>().singleOrNull() ?: throw GradleException("Only one webpack bundle is supported") }

    @Input
    val webPackConfigFile = config.webpackConfigFile?.let { project.file(it) } ?: project.buildDir.resolve("webpack.config.js")

    @Internal
    val logTailer = LogTail({serverLog().toPath() })

    @Input
    val devServerLauncherFile = project.buildDir.resolve(DevServerLauncherFileName)

    @get:Internal
    val hashes by lazy { hashOf(devServerLauncherFile, webPackConfigFile) } // TODO get all the hashes of all included configs

    @Input
    val defined = project.frontendExtension.defined

    override val identifier = "webpack-dev-server"
    override fun checkIsRunning(stopInfo: State?) = stopInfo != null && checkIsRunning(stopInfo.port)

    init {
        project.afterEvaluate {
            logTailer.rememberLogStartPosition()
        }
        logTailer.rememberLogStartPosition()

        if (webPackConfigFile.canRead()) {
            // cast because of internal API leakage https://github.com/gradle/gradle/issues/1004
            (this as Task).inputs.file(webPackConfigFile)
        }

        doLast {
            logTailer.dumpLog()
        }
    }

    @TaskAction
    fun main() {
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

    override fun beforeStart(): State? {
        val launcherFileTemplate = javaClass.classLoader.getResourceAsStream("kotlin/webpack/webpack-dev-server-launcher.js")?.reader()?.readText() ?: throw GradleException("No dev-server launcher template found")

        devServerLauncherFile.writeText(
                launcherFileTemplate
                        .replace("require('\$RunConfig\$')",
                                JsonBuilder(GenerateWebpackHelperTask.config(project, config, webPackConfigFile)).toPrettyString()
                        )
        )

        try {
            val newPermissions = java.nio.file.Files.getPosixFilePermissions(devServerLauncherFile.toPath()) + PosixFilePermission.OWNER_EXECUTE
            java.nio.file.Files.setPosixFilePermissions(devServerLauncherFile.toPath(), newPermissions)
        } catch (ignore: UnsupportedOperationException) {
        }

        return State(config.host, config.port, defined, hashes)
    }

    override fun builder(): ProcessBuilder {
        return ProcessBuilder(nodePath(project, "node").first().absolutePath, devServerLauncherFile.absolutePath).directory(project.buildDir)
    }

    override fun readState(file: File): State? {
        val j = JsonSlurper().parse(file) as? Map<*, *> ?: return null
        val host = j["host"]?.toString() ?: return null
        val port = j["port"]?.toString()?.toInt() ?: return null

        @Suppress("UNCHECKED_CAST")
        val hashes = j["hashes"] as? Map<String, String> ?: return null

        @Suppress("UNCHECKED_CAST")
        val exts = j["defined"] as? Map<String, String> ?: return null

        return State(host, port, exts, hashes)
    }

    override fun writeState(file: File, state: State) {
        file.bufferedWriter().use { out ->
            JsonBuilder(state).writeTo(out)
        }
    }

    override fun stop(state: State?) {
        if (state != null) {
            try {
                URL("http://${state.host}:${state.port}${WebPackRunTask.ShutDownPath}").readText()
            } catch (e: IOException) {
                logger.info("Couldn't stop server on port ${state.port}")
            }
        }
    }

    override fun notRunningThenKilledMessage() {
        logger.error("webpack-dev-server didn't listen port for too long so has been killed, see ${serverLog()}")
    }

    override fun notRunningExitCodeMessage(exitCode: Int) {
        logger.error("webpack-dev-server exited with exit code $exitCode, see ${serverLog()}")
    }

    override fun startedMessage() {
        logger.lifecycle("webpack started, see http://${config.host}:${config.port}/")
    }

    override fun alreadyRunningMessage() {
        logger.warn("webpack is already running at http://${config.host}:${config.port}/")
    }

    data class State(val host: String, val port: Int, val defined: Map<String, Any?>, val hashes: Map<String, String>)

    companion object {
        private fun hashOf(vararg files: File) = files.filter(File::canRead).associateBy({ it.name }, { it.sha1() })
        private fun File.sha1() = EncodingGroovyMethods.encodeHex(MessageDigest.getInstance("SHA1").digest(readBytes())).toString()

        val DevServerLauncherFileName = "webpack-dev-server-run.js"
        val ShutDownPath = "/webpack/dev/server/shutdown"

        fun checkIsRunning(port: Int): Boolean {
            try {
                Socket("localhost", port).close()
                return true
            } catch (e: IOException) {
                return false
            }
        }
    }
}