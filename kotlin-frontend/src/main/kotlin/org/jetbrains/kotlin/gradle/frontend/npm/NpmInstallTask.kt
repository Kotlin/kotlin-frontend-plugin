package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.process.*
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
            val linkPath = nodeModulesDir.resolve(dep.name).toPath().toAbsolutePath()
            val target = Paths.get(URI(dep.versionOrUri)).toAbsolutePath()

            ensureSymbolicLink(linkPath, target)
        }

        ProcessBuilder(npmPath, "install")
                .directory(project.buildDir)
                .apply { ensurePath(environment(), npm.parentFile.absolutePath) }
                .redirectErrorStream(true)
                .startWithRedirectOnFail(project, "npm install")
    }

    private fun ensureSymbolicLink(link: Path, target: Path) {
        if (Files.isSymbolicLink(link)) {
            if (Files.readSymbolicLink(link) != target) {
                Files.delete(link)
                Files.createSymbolicLink(link, target)
            }
            return
        }

        try {
            Files.delete(link)
        } catch (cause: DirectoryNotEmptyException) {
            link.toFile().deleteRecursively()
        } catch (ignore: NoSuchFileException) {
        }

        createSymbolicLink(link, target)
    }

    private fun createSymbolicLink(link: Path, target: Path) {
        if (isWindows()) {
            // always create junction on Windows as it does npm
            // Java doesn't provide any API to create junctions so we call native tool
            project.exec { spec: ExecSpec ->
                spec.apply {
                    workingDir(project.buildDir)
                    commandLine("cmd", "/C", "mklink", "/J", link.toString(), target.toString())
                }
            }.assertNormalExitValue()
        } else {
            Files.createSymbolicLink(link, target)
        }
    }

    private fun isWindows() = System.getProperty("os.name")?.contains("windows", ignoreCase = true) == true

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
