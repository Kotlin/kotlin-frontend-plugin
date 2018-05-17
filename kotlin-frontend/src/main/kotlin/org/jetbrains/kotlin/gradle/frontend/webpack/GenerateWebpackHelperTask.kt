package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*

open class GenerateWebpackHelperTask : DefaultTask() {
    @get:Nested
    val config by lazy { project.frontendExtension.bundles().filterIsInstance<WebPackExtension>().single() }

    @get:Input
    val webPackConfigFilePath: String by lazy { (config.webpackConfigFile?.let { project.file(it) } ?: project.buildDir.resolve("webpack.config.js")).absolutePath }

    @get:OutputFile
    val result = project.buildDir.resolve("WebPackHelper.js")

    init {
        onlyIf {
            config.webpackConfigFile != null
        }
    }

    @TaskAction
    fun main() {
        val json = JsonBuilder(config(project, config, File(webPackConfigFilePath))).toPrettyString()
        result.writeText("module.exports = $json;\n")
    }


    companion object {
        fun config(project: Project, config: WebPackExtension, webPackConfigFile: File) = mapOf(
                "host" to config.host,
                "port" to config.port,
                "shutDownPath" to WebPackRunTask.ShutDownPath,
                "webPackConfig" to webPackConfigFile.absolutePath,
                "contentPath" to config.contentPath?.absolutePath,
                "proxyUrl" to config.proxyUrl.let { if (it.isBlank()) null else it },
                "publicPath" to config.publicPath,
                "sourceMap" to (project.frontendExtension.sourceMaps && config.sourceMapEnabled),
                "stats" to config.stats,
                "bundlePath" to kotlinOutput(project).absolutePath,
                "moduleName" to kotlinOutput(project).nameWithoutExtension
        )
    }
}