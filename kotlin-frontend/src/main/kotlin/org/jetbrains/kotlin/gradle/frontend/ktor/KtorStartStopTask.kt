package org.jetbrains.kotlin.gradle.frontend.ktor

import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.servers.*
import java.io.*
import java.net.*

/**
 * @author Sergey Mashkov
 */
open class KtorStartStopTask : AbstractStartStopTask<Int>() {
    @Input
    var start: Boolean = true

    @get:Input
    val port: Int
        get() = project.extensions.getByType(KtorExtension::class.java).port ?: throw IllegalArgumentException("ktor port not configured")

    @Input
    var shutdownPath = "/ktor/application/shutdown"

    @Input
    var mainClass = "org.jetbrains.ktor.jetty.DevelopmentHost"

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

    override fun builder(): ProcessBuilder {
        return ProcessBuilder(
                listOf("/usr/java/latest/bin/java", "-cp")
                        + (project.configurations.flatMap { it.files.filter { it.canRead() && it.extension == "jar" } }
                        + project.convention.findPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main").output.toList()
                        )
                        .distinct().joinToString(File.pathSeparator) { it.absolutePath }
                        + listOf(
                        "-Dktor.deployment.port=$port",
                        "-Dktor.deployment.autoreload=true",
                        "-Dktor.deployment.shutdown.url=$shutdownPath",
                        mainClass)
        ).inheritIO().directory(project.buildDir)
    }

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
            doStart()
        } else {
            doStop()
        }
    }
}