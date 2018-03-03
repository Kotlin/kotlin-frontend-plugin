package org.jetbrains.kotlin.gradle.frontend.yarn

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.frontend.util.NodeJsDownloadTask
import org.jetbrains.kotlin.gradle.frontend.util.nodePath
import org.jetbrains.kotlin.gradle.frontend.util.startWithRedirectOnFail
import java.io.File

open class YarnInstallTask : DefaultTask() {
    @InputFile
    lateinit var packageJsonFile: File

    val yarnDirFile =  project.tasks
        .filterIsInstance<NodeJsDownloadTask>()
        .map { it.nodePathTextFile }
        .firstOrNull()

    @OutputDirectory
    val nodeModulesDir: File = project.buildDir.resolve("node_modules")

    init {
        if (yarnDirFile != null) inputs.file(yarnDirFile)
    }

    @TaskAction
    fun processInstallation() {
        logger.info("Running yarn install")

        val yarn = nodePath(project, "yarn").first()
        val yarnPath = yarn.absolutePath

        ProcessBuilder(yarnPath, "install")
            .directory(project.buildDir)
            .apply { ensurePath(environment(), yarn.parentFile.absolutePath) }
            .redirectErrorStream(true)
            .startWithRedirectOnFail(project, "yarn install")
    }

    private fun ensurePath(env: MutableMap<String, String>, path: String) {
        val sep = File.pathSeparator
        env.keys.filter { it.equals("path", ignoreCase = true) }.forEach { envName ->
            val envValue = env[envName]
            if (envValue != null && !envValue.startsWith(path)) {
                env[envName] = path + sep + if (envValue.endsWith(path)) envValue.removeSuffix(path) else envValue.replace("$sep$path$sep", sep)
            }
        }
    }
}
