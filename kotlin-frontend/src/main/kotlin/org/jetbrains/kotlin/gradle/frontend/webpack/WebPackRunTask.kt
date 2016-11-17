package org.jetbrains.kotlin.gradle.frontend.webpack

import net.rubygrapefruit.platform.*
import org.codehaus.groovy.runtime.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*
import java.net.*
import java.nio.file.attribute.*
import java.security.*
import java.util.concurrent.*

/**
 * @author Sergey Mashkov
 */
open class WebPackRunTask : DefaultTask() {
    val webPackConfigFile = project.buildDir.resolve("webpack.config.js")
    val devServerLauncherFile = project.buildDir.resolve(DevServerLauncherFileName)

    @TaskAction
    fun run() {
        val config = project.extensions.getByType(WebPackExtension::class.java)!!
        val port = config.port

        val launcherFileTemplate = javaClass.classLoader.getResourceAsStream("kotlin/webpack/webpack-dev-server-launcher.js")?.reader()?.readText() ?: throw GradleException("No dev-server launcher template found")

        devServerLauncherFile.writeText(launcherFileTemplate.replace("require('\$RunConfig\$')", """
        {
            port: $port,
            shutDownPath: '$ShutDownPath',
            webPackConfig: '${webPackConfigFile.absolutePath}',
            contentPath: '${config.contentPath.absolutePath}'
        }
        """.trimIndent()))

        val newPermissions = java.nio.file.Files.getPosixFilePermissions(devServerLauncherFile.toPath()) + PosixFilePermission.OWNER_EXECUTE
        java.nio.file.Files.setPosixFilePermissions(devServerLauncherFile.toPath(), newPermissions)

        val hashes = hashOf(devServerLauncherFile, webPackConfigFile)
        // TODO get all the hashes of all included configs

        val lastHashesFile = project.buildDir.resolve(".webpack-last-hashes.txt")
        val lastHashes = lastHashesFile.let {
            if (it.canRead()) it.readLines()
                    .map(String::trim)
                    .map { it.split("\\s+".toRegex()) }
                    .filter { it.size == 2 }
                    .associateBy({ it[0] }, { it[1] })
            else emptyMap()
        }

        if (checkIsRunning(port) || readLastPort(lastPortFile(project))?.let { checkIsRunning(it) } ?: false) {
            if (hashesChanged(lastHashes, hashes)) {
                logger.warn("webpack/dev server configuration changed: need to restart")
                tryStop()
            }
        }

        if (checkIsRunning(port)) {
            logger.warn("dev server is already running on port $port")
            return
        }

        logger.info("starting server on port $port")

        val launcher = Native.get(ProcessLauncher::class.java)!!
        val devServerLog = project.buildDir.resolve("webpack-dev-server.log")
        val pb = ProcessBuilder("/bin/sh", "-c", devServerLauncherFile.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(devServerLog)

        val process = launcher.start(pb)

        for (i in 1..10) {
            if (checkIsRunning(port)) {
                break
            }
            if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                break
            }
        }

        if (!checkIsRunning(port)) {
            if (process.isAlive) {
                process.destroyForcibly()

                logger.error("webpack-dev-server didn't listen port $port so has been killed, see $devServerLog")
            } else {
                logger.error("webpack-dev-server exited with exit code ${process.exitValue()}, see $devServerLog")
            }

            throw GradleException("webpack-dev-server startup failed")
        }

        project.buildDir.resolve(DevServerPortFileName).writeText(port.toString())
        lastHashesFile.writeText(hashes.entries.sortedBy { it.key }.joinToString(separator = "\n", postfix = "\n") { "${it.key}\t${it.value}" })
    }

    private fun tryStop() {
        WebPackStopTask.shutdown(project, logger)
    }

    private fun hashOf(vararg files: File) = files.associateBy({ it.name }, { it.sha1() })
    private fun File.sha1() = EncodingGroovyMethods.encodeHex(MessageDigest.getInstance("SHA1").digest(readBytes())).toString()

    private fun hashesChanged(oldHashes: Map<String, String>, newHashes: Map<String, String>): Boolean {
        return oldHashes != newHashes
    }

    companion object {
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