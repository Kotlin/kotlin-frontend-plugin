package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*

/**
 * @author Sergey Mashkov
 */
open class WebPackBundleTask : DefaultTask() {
    @get:InputDirectory
    val inputDir by lazy { kotlinOutput(project).parentFile }

    @get:Internal
    val webPackConfig by lazy { project.extensions.getByType(WebPackExtension::class.java)!! }

    @InputFile
    val webPackConfigFile = project.buildDir.resolve("webpack.config.js")

    @get:OutputDirectory
    val bundleDir by lazy { GenerateWebPackConfigTask.handleFile(project, webPackConfig.bundleDirectory) }

    @TaskAction
    fun buildBundle() {
                ProcessBuilder("node", project.buildDir.resolve("node_modules/webpack/bin/webpack.js").absolutePath, "--config", webPackConfigFile.absolutePath)
                        .directory(project.buildDir)
                        .startWithRedirectOnFail(project)
    }
}