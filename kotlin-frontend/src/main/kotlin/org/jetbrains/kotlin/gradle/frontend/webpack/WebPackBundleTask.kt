package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.util.*

/**
 * @author Sergey Mashkov
 */
open class WebPackBundleTask : DefaultTask() {
    @get:Nested
    private val config by lazy { project.frontendExtension.bundles().filterIsInstance<WebPackExtension>().singleOrNull() ?: throw GradleException("Only one webpack bundle is supported") }

    @InputFile
    val webPackConfigFile = config.webpackConfigFile?.let { project.file(it) } ?: project.buildDir.resolve("webpack.config.js")

    @get:OutputDirectory
    val bundleDir by lazy { GenerateWebPackConfigTask.handleFile(project, project.frontendExtension.bundlesDirectory) }

    @get:InputFile
    val sourceFile by lazy { kotlinOutput(project) }

    @TaskAction
    fun buildBundle() {
        ProcessBuilder(
                nodePath(project, "node").first().absolutePath,
                project.buildDir.resolve("node_modules/webpack/bin/webpack.js").absolutePath,
                "--config", webPackConfigFile.absolutePath
        )
                .directory(project.buildDir)
                .startWithRedirectOnFail(project, "node webpack.js")
    }
}