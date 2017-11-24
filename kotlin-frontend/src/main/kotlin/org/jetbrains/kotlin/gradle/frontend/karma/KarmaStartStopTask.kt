package org.jetbrains.kotlin.gradle.frontend.karma

import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.servers.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*
import java.net.*

open class KarmaStartStopTask : AbstractStartStopTask<Int>() {
    @get:Input
    val sourceMaps: Boolean
        get() = project.frontendExtension.sourceMaps

    @get:Nested
    val extension by lazy { project.extensions.getByType(KarmaExtension::class.java)!! }

    @Input
    var start: Boolean = false

    @Internal
    private val logTailer = LogTail({ serverLog().toPath() })

    override val identifier = "karma"

    override fun builder() = ProcessBuilder(nodePath(project, "node").first().absolutePath,
            project.buildDir.resolve("node_modules/karma/bin/karma").absolutePath,
            "start").directory(project.buildDir)!!

    init {
        logTailer.rememberLogStartPosition()
    }

    override fun beforeStart(): Int? = extension.port

    @TaskAction
    fun run() {
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

    override fun startedMessage() {
        logger.lifecycle("karma started, open http://localhost:${extension.port}/ in your browser to run tests and see report")
    }

    override fun alreadyRunningMessage() {
        logger.lifecycle("karma is already running at http://localhost:${extension.port}/")
    }

    override fun checkIsRunning(stopInfo: Int?): Boolean {
        try {
            if (stopInfo != null) {
                Socket("localhost", stopInfo).close()
                return true
            }
        } catch (ignore: Throwable) {
        }

        return false
    }

    override fun stop(state: Int?) {
        try {
            if (state != null) {
                URL("http://localhost:$state/stop").openStream().readBytes()
            }
        } catch (e: IOException) {
        }
    }

    override fun readState(file: File) = file.readText().trim().toInt()

    override fun writeState(file: File, state: Int) {
        file.writeText(state.toString())
    }
}

