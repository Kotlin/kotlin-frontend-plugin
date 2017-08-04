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
            .map { it.nodePathTextFile }
            .firstOrNull()

    @OutputDirectory
    val nodeModulesDir: File = project.buildDir.resolve("node_modules")

    init {
        if (npmDirFile != null) inputs.file(npmDirFile)
    }

    @TaskAction
    fun processInstallation() {
        logger.info("Running npm install")

        val npm = nodePath(project, "npm").first()
        val npmPath = npm.absolutePath

        ProcessBuilder(npmPath, "install")
                .directory(project.buildDir)
                .apply { ensurePath(environment(), npm.parentFile.absolutePath) }
                .redirectErrorStream(true)
                .startWithRedirectOnFail(project, "npm install")
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
