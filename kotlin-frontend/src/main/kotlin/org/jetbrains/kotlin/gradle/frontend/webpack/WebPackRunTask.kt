package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import org.codehaus.groovy.runtime.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.servers.*
import org.jetbrains.kotlin.preprocessor.*
import java.io.*
import java.net.*
import java.nio.file.*
import java.nio.file.attribute.*
import java.security.*

/**
 * @author Sergey Mashkov
 */
open class WebPackRunTask : AbstractStartStopTask<Int>() {
    var start: Boolean = true

    @get:Nested
    private val config by lazy { project.extensions.getByType(WebPackExtension::class.java)!! }

    val webPackConfigFile = project.buildDir.resolve("webpack.config.js")

    @Internal
    private var logPosition = 0L

    @Internal
    private var logCreationDate = 0L

    val devServerLauncherFile = project.buildDir.resolve(DevServerLauncherFileName)

    val devServerLog = project.buildDir.resolve("webpack-dev-server.log")
    val lastHashesFile = project.buildDir.resolve(".webpack-last-hashes.txt")

    val hashes by lazy { hashOf(devServerLauncherFile, webPackConfigFile) } // TODO get all the hashes of all included configs

    override val identifier = "webpack-dev-server"
    override fun checkIsRunning(stopInfo: Int?) = stopInfo != null && Companion.checkIsRunning(stopInfo)

    init {
        project.afterEvaluate {
            checkLogFilePosition()
        }
        checkLogFilePosition()

        if (webPackConfigFile.canRead()) {
            inputs.file(webPackConfigFile)
        }

        doLast {
            lastHashesFile.parentFile.mkdirsOrFail()
            lastHashesFile.writeText(hashes.entries.sortedBy { it.key }.joinToString(separator = "\n", postfix = "\n") { "${it.key}\t${it.value}" })
            dumpLog()
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

        devServerLauncherFile.writeText(
                launcherFileTemplate
                        .replace("require('\$RunConfig\$')", JsonBuilder(mapOf(
                                "port" to config.port,
                                "shutDownPath" to ShutDownPath,
                                "webPackConfig" to webPackConfigFile.absolutePath,
                                "contentPath" to config.contentPath?.absolutePath,
                                "proxyUrl" to config.proxyUrl.let { if (it.isBlank()) null else it },
                                "publicPath" to config.publicPath,
                                "sourceMap" to project.extensions.getByType(KotlinFrontendExtension::class.java).sourceMaps
                        )).toPrettyString()))

        try {
            val newPermissions = java.nio.file.Files.getPosixFilePermissions(devServerLauncherFile.toPath()) + PosixFilePermission.OWNER_EXECUTE
            java.nio.file.Files.setPosixFilePermissions(devServerLauncherFile.toPath(), newPermissions)
        } catch (ignore: UnsupportedOperationException) {
        }

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

    override fun serverLog() = devServerLog

    override fun builder(): ProcessBuilder {
        return ProcessBuilder("node", devServerLauncherFile.absolutePath).directory(project.buildDir)
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

    private fun checkLogFilePosition() {
        try {
            // it is important to execute it BEFORE any task run because we need to remember file position
            // before any changes detected
            val attrs = Files.readAttributes(serverLog().toPath(), BasicFileAttributes::class.java)

            logCreationDate = attrs.creationTime().toMillis()
            logPosition = Math.max(logPosition, attrs.size())
        } catch (ignore: IOException) {
        }
    }

    private fun dumpLog() {
        try {
            val file = serverLog().toPath()
            var lastCreationDate = logCreationDate
            var lastSize = logPosition
            var lastCheck = System.currentTimeMillis()

            while (true) {
                val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
                val size = attributes.size()
                val date = attributes.creationTime().toMillis()

                if (size != lastSize || date != lastCreationDate) {
                    lastCheck = System.currentTimeMillis()

                    Files.newInputStream(file).use { s ->
                        val bytesCount = if (date == lastCreationDate && size >= lastSize) {
                            if (s.skip(lastSize) != lastSize) {
                                0   // truncated during read
                            } else {
                                size - lastSize
                            }
                        } else {
                            size
                        }

                        val bytes = s.readFully(bytesCount.toInt())
                        System.err.write(bytes)
                        System.err.flush()
                    }

                    lastSize = size
                    lastCreationDate = date
                } else if (System.currentTimeMillis() - lastCheck > 1000) {
                    break
                }

                Thread.sleep(75)
            }
        } catch (ignore: IOException) {
        }
    }

    companion object {
        private fun hashOf(vararg files: File) = files.filter(File::canRead).associateBy({ it.name }, { it.sha1() })
        private fun File.sha1() = EncodingGroovyMethods.encodeHex(MessageDigest.getInstance("SHA1").digest(readBytes())).toString()

        private fun hashesChanged(oldHashes: Map<String, String>, newHashes: Map<String, String>): Boolean {
            return oldHashes != newHashes
        }

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

        private fun InputStream.readFully(size: Int): ByteArray {
            val buffer = ByteArray(size)
            var read = 0

            do {
                val rc = read(buffer, read, size - read)
                if (rc == -1) {
                    throw IOException("Unexpected EOF")
                }

                read += rc
            } while (read < size)

            return buffer
        }
    }
}