package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.JsonSlurper
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*

/**
 * @author Sergey Mashkov
 */
open class WebPackBundleTask : DefaultTask() {
    @get:Nested
    private val config by lazy {
        project.frontendExtension.bundles().filterIsInstance<WebPackExtension>().singleOrNull()
                ?: throw GradleException("Only one webpack bundle is supported")
    }

    @get:InputFile
    private val webPackConfigFile by lazy {
        config.webpackConfigFile?.let { project.file(it) }
                ?: project.buildDir.resolve("webpack.config.js")
    }

    @get:OutputDirectory
    val bundleDir by lazy { GenerateWebPackConfigTask.handleFile(project, project.frontendExtension.bundlesDirectory) }

    @get:InputFile
    val sourceFile by lazy { kotlinOutput(project) }

    @TaskAction
    fun buildBundle() {
        @Suppress("UNCHECKED_CAST")
        val webpackVersion = project.buildDir.resolve("node_modules/webpack/package.json")
                .let { JsonSlurper().parse(it) as Map<String, Any?> }["version"]
                ?.let { it as String }

        val processBuilderCommands = arrayListOf(
                nodePath(project, "node").first().absolutePath,
                project.buildDir.resolve("node_modules/webpack/bin/webpack.js").absolutePath,
                "--config", webPackConfigFile.absolutePath
        )
        val webpackMajorVersion = webpackVersion
                ?.split('.')
                ?.firstOrNull()
                ?.toInt()
        if (webpackMajorVersion != null && webpackMajorVersion >= 4) {
            processBuilderCommands.addAll(arrayOf(
                    "--mode", config.mode
            ))
        }
        ProcessBuilder(processBuilderCommands)
                .directory(project.buildDir)
                .startWithRedirectOnFail(project, "node webpack.js")
    }
}
