package org.jetbrains.kotlin.gradle.frontend.webpack

import net.rubygrapefruit.platform.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class WebPackBundleTask : DefaultTask() {
    @get:InputDirectory
    val inputDir: File
        get() = WebPackBundler.kotlinOutput(project).parentFile

    @get:Internal
    val webPackConfig by lazy { project.extensions.getByType(WebPackExtension::class.java)!! }

    @Input
    val webPackConfigFile = project.buildDir.resolve("webpack.config.js")

    @get:OutputDirectory
    val bundleDir by lazy { GenerateWebPackConfigTask.handleFile(project, webPackConfig.bundleDirectory) }

    @TaskAction
    fun buildBundle() {
        val process = Native.get(ProcessLauncher::class.java).start(
                ProcessBuilder("node", project.buildDir.resolve("node_modules/webpack/bin/webpack.js").absolutePath, "--config", webPackConfigFile.absolutePath)
                        .inheritIO()
                        .directory(project.buildDir)
        )

        if (process.waitFor() != 0) {
            logger.error("webpack failed to bundle project, exit code ${process.exitValue()}")
            throw GradleException("bundle failed")
        }
    }
}