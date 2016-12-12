package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*

/**
 * @author Sergey Mashkov
 */
open class WebPackBundleTask : DefaultTask() {
    @InputFile
    val webPackConfigFile = project.buildDir.resolve("webpack.config.js")

    @get:OutputDirectory
    val bundleDir by lazy { GenerateWebPackConfigTask.handleFile(project, project.frontendExtension.bundlesDirectory) }

    @TaskAction
    fun buildBundle() {
                ProcessBuilder("node", project.buildDir.resolve("node_modules/webpack/bin/webpack.js").absolutePath, "--config", webPackConfigFile.absolutePath)
                        .directory(project.buildDir)
                        .startWithRedirectOnFail(project)
    }
}