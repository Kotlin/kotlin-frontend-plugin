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

    val npmDirFile =  project.tasks
            .filterIsInstance<NodeJsDownloadTask>()
            .mapNotNull { it.nodePathTextFile }
            .firstOrNull()

    @OutputDirectory
    val nodeModulesDir: File = project.buildDir.resolve("node_modules")

    init {
        if (npmDirFile != null) inputs.file(npmDirFile)
    }

    @TaskAction
    fun processInstallation() {
        logger.info("Running npm install")

        ProcessBuilder(nodePath(project, "npm").first().absolutePath, "install")
                .directory(project.buildDir)
                .redirectErrorStream(true)
                .startWithRedirectOnFail(project, "npm install")
    }
}
