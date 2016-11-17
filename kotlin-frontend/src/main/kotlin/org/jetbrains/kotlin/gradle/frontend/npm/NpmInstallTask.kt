package org.jetbrains.kotlin.gradle.frontend.npm

import net.rubygrapefruit.platform.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class NpmInstallTask : DefaultTask() {
    @InputFile
    lateinit var packageJsonFile: File

    @OutputDirectory
    val nodeModulesDir: File = project.buildDir.resolve("node_modules")

    @TaskAction
    fun processInstallation() {
        logger.info("Running npm install")

        val pb = ProcessBuilder("npm", "install")
                .directory(project.buildDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)

        val process = Native.get(ProcessLauncher::class.java).start(pb)
        if (process.waitFor() != 0) {
            throw GradleException("npm install failed")
        }
    }
}