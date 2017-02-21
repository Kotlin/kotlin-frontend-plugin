package org.jetbrains.kotlin.gradle.frontend.karma

import groovy.json.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.servers.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*
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

    @Suppress("RemoveRedundantCallsOfConversionMethods")
    override fun beforeStart(): Int? {
        val prepares = mutableListOf<String>()
        val plugins = mutableListOf("karma-phantomjs-launcher")
        val preprocessors = extension.preprocessors.toMutableList()
        val clientConfig = mutableMapOf<String, Any>()

        val config = linkedMapOf(
                "basePath" to project.buildDir.absolutePath,
                "frameworks" to extension.frameworks.toMutableList(),
                "reporters" to extension.reporters.toMutableList(),
                "files" to listOf<String>(
                        kotlinTestOutput(project).absolutePath
                ),
                "exclude" to listOf("*~", "*.swp", "*.swo"),
                "port" to extension.port,
                "runnerPort" to extension.runnerPort,
                "colors" to false,
                "autoWatch" to true,
                "browsers" to listOf("PhantomJS"),
                "captureTimeout" to 5000,
                "singleRun" to false,
                "preprocessors" to mapOf(
                        kotlinTestOutput(project).absolutePath to preprocessors
                ),
                "plugins" to plugins,
                "client" to clientConfig
        )

        if ("junit" in extension.reporters) {
            config["junitReporter"] = mapOf(
                    "outputFile" to "", // TODO junit report
                    "suite" to ""
            )
        }
        if ("qunit" in extension.frameworks) {
            if ("karma-qunit" !in plugins) {
                plugins += "karma-qunit"
            }

            clientConfig["clearContext"] = false
            clientConfig["qunit"] = hashMapOf(
                    "showUI" to true,
                    "testTimeout" to 5000
            )
        }
        if (sourceMaps) {
            preprocessors.add("sourcemap")
            plugins.add("karma-sourcemap-loader")
        }
        if (extension.enableWebPack) {
            project.tasks.withType(GenerateWebPackConfigTask::class.java).single().let { webpackTask ->
                webpackTask.webPackConfigFile.ifCanRead { file ->
                    prepares += "var webpackConfig = require(${JsonOutput.toJson(file.absolutePath)})"
                    prepares += "webpackConfig.entry = {}"
                    prepares += "webpackConfig.resolve.modules.push(" + JsonOutput.toJson(kotlinTestOutput(project).absolutePath) + ")"

                    plugins += "karma-webpack"
                    preprocessors += "webpack"
                    config["webpack"] = "\$webpackConfig"
                }
            }
        }

        val configText = """
        #PREPARES
        module.exports = function (config) {
        config.set(#CONFIG)
        };
        """.trimIndent()
                .replace("#CONFIG", JsonBuilder(config).toPrettyString())
                .replace("#PREPARES", prepares.joinToString(";\n", postfix = ";\n"))
                .replace("\"\\\$([^\"]+)\"".toRegex()) { m -> m.groupValues[1] }

        project.buildDir.resolve("karma.conf.js").writeText(configText)

        return extension.port
    }

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

private inline fun File.ifCanRead(block: (File) -> Unit): Unit {
    if (exists() && canRead()) {
        block(this)
    }
}
