package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
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

        ProcessBuilder("npm", "install")
                .directory(project.buildDir)
                .redirectErrorStream(true)
                .startWithRedirectOnFail(project)
    }
}