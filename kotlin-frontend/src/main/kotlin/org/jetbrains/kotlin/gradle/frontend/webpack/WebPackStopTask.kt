package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.logging.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.webpack.WebPackRunTask.Companion.checkIsRunning
import java.io.*
import java.net.*

/**
 * @author Sergey Mashkov
 */
open class WebPackStopTask : DefaultTask() {
    @TaskAction
    fun shutdown() {
        shutdown(project, logger)
    }

    companion object {
        fun shutdown(project: Project, logger: Logger) {
            val portFile = WebPackRunTask.lastPortFile(project)
            val port = WebPackRunTask.readLastPort(portFile)
            if (port == null) {
                logger.info("Server not found: nothing to stop")
                return
            }

            if (!checkIsRunning(port)) {
                logger.info("Server is not running")
                return
            }

            logger.info("Stopping server on port $port")
            for (i in 1..10) {
                try {
                    URL("http://localhost:$port${WebPackRunTask.ShutDownPath}").readText()
                } catch (e: IOException) {
                    logger.info("Couldn't stop server on port $port")
                }

                Thread.sleep(500)

                if (!checkIsRunning(port)) {
                    break
                }
            }

            if (checkIsRunning(port)) {
                logger.error("Couldn't stop dev server: still running")
            } else {
                portFile.delete()
                logger.info("Server stopped")
            }
        }
    }
}