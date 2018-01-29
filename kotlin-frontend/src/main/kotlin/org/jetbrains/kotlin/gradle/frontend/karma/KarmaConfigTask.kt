package org.jetbrains.kotlin.gradle.frontend.karma

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*
import java.io.*

open class KarmaConfigTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    val configsDir: File
        get() = project.projectDir.resolve("karma.config.d")

    @get:Input
    val sourceMaps: Boolean
        get() = project.frontendExtension.sourceMaps

    @get:Nested
    val extension: KarmaExtension by lazy { project.extensions.getByType(KarmaExtension::class.java) }

    @OutputFile
    var karmaConfigFile: File = project.buildDir.resolve("karma.conf.js")

    @TaskAction
    fun main() {
        val prepares = mutableListOf<String>()
        val plugins = mutableSetOf("karma-phantomjs-launcher").apply { this += extension.plugins }
        val preprocessors = extension.preprocessors.toMutableSet()
        val clientConfig = mutableMapOf<String, Any>()

        if (extension.customConfigFile.isNotBlank()) {
            val file = project.projectDir.resolve(extension.customConfigFile)
            file.copyTo(project.buildDir.resolve("karma.conf.js"), true)
        } else {
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
                "browsers" to extension.browsers,
                "captureTimeout" to extension.captureTimeout,
                "singleRun" to false,
                "preprocessors" to mapOf(
                    kotlinTestOutput(project).absolutePath to preprocessors
                ),
                "plugins" to plugins,
                "client" to clientConfig
            )

            if ("junit" in extension.reporters) {
                config["junitReporter"] = mapOf(
                    "outputFile" to project.buildDir.resolve("reports/karma.xml").absolutePath,
                    "suite" to "karma"
                )
                plugins.add("karma-junit-reporter")
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
                preprocessors += "sourcemap"
                plugins += "karma-sourcemap-loader"
            }
            if (extension.enableWebPack) {
                project.tasks.withType(GenerateWebPackConfigTask::class.java).single().let { webpackTask ->
                    webpackTask.webPackConfigFile.ifCanRead { file ->
                        prepares += "var webpackConfig = require(${JsonOutput.toJson(file.absolutePath)})"
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

            karmaConfigFile.bufferedWriter().use { out ->
                out.append(configText)
                out.appendln()

                val p = "^\\d+".toRegex()
                configsDir.listFiles()?.sortedBy { p.find(it.nameWithoutExtension)?.value?.toInt() ?: 0 }?.forEach {
                    out.append("// from file ${it.path}")
                    out.appendln()

                    it.reader().use {
                        it.copyTo(out)
                    }
                    out.appendln()
                }
            }
        }
    }

    private inline fun File.ifCanRead(block: (File) -> Unit) {
        if (exists() && canRead()) {
            block(this)
        }
    }
}
