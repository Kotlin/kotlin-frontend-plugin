package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import org.codehaus.groovy.runtime.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.servers.*
import java.io.*
import java.net.*
import java.nio.file.attribute.*
import java.security.*

/**
 * @author Sergey Mashkov
 */
open class WebPackRunTask : AbstractStartStopTask<Int>() {
    var start: Boolean = true

    @get:Nested
    private val config by lazy { project.extensions.getByType(WebPackExtension::class.java)!! }

    @InputFile
    val webPackConfigFile = project.buildDir.resolve("webpack.config.js")

    val devServerLauncherFile = project.buildDir.resolve(DevServerLauncherFileName)

    val devServerLog = project.buildDir.resolve("webpack-dev-server.log")
    val lastHashesFile = project.buildDir.resolve(".webpack-last-hashes.txt")

    val hashes by lazy { hashOf(devServerLauncherFile, webPackConfigFile) } // TODO get all the hashes of all included configs

    override val identifier = "webpack-dev-server"
    override fun checkIsRunning(stopInfo: Int?) = stopInfo != null && Companion.checkIsRunning(stopInfo)

    init {
        doLast {
            lastHashesFile.writeText(hashes.entries.sortedBy { it.key }.joinToString(separator = "\n", postfix = "\n") { "${it.key}\t${it.value}" })
        }
    }

    @TaskAction
    fun main() {
        if (start) {
            doStart()
        } else {
            doStop()
        }
    }

    override fun beforeStart(): Int? {
        val launcherFileTemplate = javaClass.classLoader.getResourceAsStream("kotlin/webpack/webpack-dev-server-launcher.js")?.reader()?.readText() ?: throw GradleException("No dev-server launcher template found")

        devServerLauncherFile.writeText(launcherFileTemplate.replace("require('\$RunConfig\$')", JsonBuilder(mapOf(
                "port" to config.port,
                "shutDownPath" to ShutDownPath,
                "webPackConfig" to webPackConfigFile.absolutePath,
                "contentPath" to config.contentPath.absolutePath
        )).toPrettyString()))

        val newPermissions = java.nio.file.Files.getPosixFilePermissions(devServerLauncherFile.toPath()) + PosixFilePermission.OWNER_EXECUTE
        java.nio.file.Files.setPosixFilePermissions(devServerLauncherFile.toPath(), newPermissions)

        return config.port
    }

    override fun needRestart(oldState: Int?, newState: Int?): Boolean {
        if (oldState != newState) {
            return true
        }

        val lastHashes = lastHashesFile.let {
            if (it.canRead()) it.readLines()
                    .map(String::trim)
                    .map { it.split("\\s+".toRegex()) }
                    .filter { it.size == 2 }
                    .associateBy({ it[0] }, { it[1] })
            else emptyMap()
        }

        return hashesChanged(lastHashes, hashes)
    }

    override fun builder(): ProcessBuilder {
        return ProcessBuilder("node", devServerLauncherFile.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(devServerLog)
    }

    override fun readState(file: File): Int = file.readText().trim().toInt()
    override fun writeState(file: File, state: Int) {
        file.writeText("$state\n")
    }

    override fun stop(state: Int?) {
        if (state != null) {
            try {
                URL("http://localhost:$state${WebPackRunTask.ShutDownPath}").readText()
            } catch (e: IOException) {
                logger.info("Couldn't stop server on port $state")
            }
        }
    }

    override fun notRunningThenKilledMessage() {
        logger.error("webpack-dev-server didn't listen port so has been killed, see $devServerLog")
    }

    override fun notRunningExitCodeMessage(exitCode: Int) {
        logger.error("webpack-dev-server exited with exit code $exitCode, see $devServerLog")
    }

    companion object {
        private fun hashOf(vararg files: File) = files.associateBy({ it.name }, { it.sha1() })
        private fun File.sha1() = EncodingGroovyMethods.encodeHex(MessageDigest.getInstance("SHA1").digest(readBytes())).toString()

        private fun hashesChanged(oldHashes: Map<String, String>, newHashes: Map<String, String>): Boolean {
            return oldHashes != newHashes
        }

        val DevServerPortFileName = "dev-server-port.txt"
        val DevServerLauncherFileName = "webpack-dev-server-run.js"
        val ShutDownPath = "/webpack/dev/server/shutdown"

        fun lastPortFile(project: Project) = project.buildDir.resolve(WebPackRunTask.DevServerPortFileName)
        fun readLastPort(portFile: File) = if (portFile.canRead()) portFile.readText().trim().toInt() else null

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