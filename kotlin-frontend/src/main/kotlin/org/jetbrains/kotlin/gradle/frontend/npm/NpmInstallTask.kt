package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*
import java.net.*
import java.nio.file.*

/**
 * @author Sergey Mashkov
 */
open class NpmInstallTask : DefaultTask() {
    @InputFile
    lateinit var packageJsonFile: File

    @Internal
    private val npmDirFile =  project.tasks
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

        val unpacked = (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { task ->
            task.resultNames?.map { Dependency(it.name, it.uri, Dependency.RuntimeScope) } ?: task.resultFile.readLinesOrEmpty()
                    .map { it.split("/", limit = 4).map(String::trim) }
                    .filter { it.size == 4 }
                    .map { Dependency(it[0], it[3], Dependency.RuntimeScope) }
        }).flatten()

        unpacked.forEach { dep ->
            val linkPath = nodeModulesDir.resolve(dep.name).toPath()
            val target = Paths.get(URI(dep.versionOrUri))

            if (Files.isSymbolicLink(linkPath) && Files.readSymbolicLink(linkPath) != target) {
                Files.delete(linkPath)
                Files.createSymbolicLink(linkPath, target)
            } else if (!Files.isSymbolicLink(linkPath)) {
                linkPath.toFile().deleteRecursively()
                Files.createSymbolicLink(linkPath, target)
            }
        }

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
