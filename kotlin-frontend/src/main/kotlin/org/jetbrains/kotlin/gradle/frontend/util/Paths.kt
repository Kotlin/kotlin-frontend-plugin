package org.jetbrains.kotlin.gradle.frontend.util

import org.apache.tools.ant.taskdefs.condition.*
import org.gradle.api.*
import java.io.*

val nodeDirProperty = "org.kotlin.frontend.node.dir"

fun splitEnvironmentPath() = System.getenv("PATH").split(File.pathSeparator).filter(String::isNotBlank)
fun whereIs(command: String, extraPaths: List<String> = emptyList()): List<File> = (extraPaths + splitEnvironmentPath()).flatMap {
    val bin = it + File.separator + command

    Suffixes.map { File(bin + it) }
}.filter { it.exists() && it.canExecute() }.distinct()

fun nodePath(project: Project, command: String = "node"): List<File> {
    val userDefinedNodeDir = project.findProperty(nodeDirProperty)?.toString()?.let { listOf(it) } ?: emptyList()
    val downloadedNodeDirs = project.tasks
            .filterIsInstance<NodeJsDownloadTask>()
            .map { it.nodePathTextFile }
            .filter { it.isFile }
            .flatMap { File(it.readText().trim()).let { listOf(it.resolve("bin"), it) } }
            .filter { it.isDirectory }
            .map { it.absolutePath }

    val paths = whereIs(command, userDefinedNodeDir + downloadedNodeDirs)

    if (paths.isEmpty()) {
        project.logger.debug("No executable $command found in ${userDefinedNodeDir + splitEnvironmentPath()}")
        throw GradleException("No executable $command found")
    }

    return paths
}

fun ProcessBuilder.addCommandPathToSystemPath() = apply {
    if (command().isNotEmpty()) {
        val commandFile = File(command()[0])
        if (commandFile.isAbsolute) {
            val env = splitEnvironmentPath()
            val commandDir = commandFile.parent
            if (commandDir !in env) {
                environment()["PATH"] = (env + commandDir).joinToString(File.pathSeparator)
            }
        }
    }
}

fun File.mkdirsOrFail() {
    if (!mkdirs() && !exists()) {
        throw IOException("Failed to create directories at $this")
    }
}

private val Suffixes = if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    listOf(".exe", ".bat", ".cmd")
} else {
    listOf("", ".sh", ".bin", ".app")
}
