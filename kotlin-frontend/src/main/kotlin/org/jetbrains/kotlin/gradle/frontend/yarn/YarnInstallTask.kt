package org.jetbrains.kotlin.gradle.frontend.yarn

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.gradle.frontend.Dependency
import org.jetbrains.kotlin.gradle.frontend.dependencies.UnpackGradleDependenciesTask
import org.jetbrains.kotlin.gradle.frontend.util.NodeJsDownloadTask
import org.jetbrains.kotlin.gradle.frontend.util.nodePath
import org.jetbrains.kotlin.gradle.frontend.util.readLinesOrEmpty
import org.jetbrains.kotlin.gradle.frontend.util.startWithRedirectOnFail
import java.io.File
import java.net.URI
import java.nio.file.*

open class YarnInstallTask : DefaultTask() {
    @InputFile
    lateinit var packageJsonFile: File

    @Internal
    private val yarnDirFile = project.tasks
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

        val unpacked = (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { task ->
            task.resultNames?.map { Dependency(it.name, it.uri, Dependency.RuntimeScope) }
                    ?: task.resultFile.readLinesOrEmpty()
                        .map { it.split("/", limit = 4).map(String::trim) }
                        .filter { it.size == 4 }
                        .map { Dependency(it[0], it[3], Dependency.RuntimeScope) }
        }).flatten()

        unpacked.forEach { dep ->
            val linkPath = nodeModulesDir.resolve(dep.name).toPath().toAbsolutePath()
            val target = Paths.get(URI(dep.versionOrUri)).toAbsolutePath()

            ensureSymbolicLink(linkPath, target)
        }

        ProcessBuilder(yarnPath, "install")
            .directory(project.buildDir)
            .apply { ensurePath(environment(), yarn.parentFile.absolutePath) }
            .redirectErrorStream(true)
            .startWithRedirectOnFail(project, "yarn install")
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
            // always create junction on Windows as it does yarn
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
                env[envName] = path + sep +
                        if (envValue.endsWith(path)) envValue.removeSuffix(path) else envValue.replace(
                            "$sep$path$sep",
                            sep
                        )
            }
        }
    }
}
