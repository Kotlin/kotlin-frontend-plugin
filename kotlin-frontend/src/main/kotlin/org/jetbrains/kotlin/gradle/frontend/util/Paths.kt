package org.jetbrains.kotlin.gradle.frontend.util

import org.apache.tools.ant.taskdefs.condition.*
import org.gradle.api.*
import java.io.*

fun splitEnvironmentPath() = System.getenv("PATH").split(File.pathSeparator).filter(String::isNotBlank)
fun whereIs(command: String, extraPaths: List<String> = emptyList()): List<File> = (extraPaths + splitEnvironmentPath()).flatMap {
    val bin = it + File.separator + command

    Suffixes.map { File(bin + it) }
}.filter { it.exists() && it.canExecute() }.distinct()

fun nodePath(project: Project, command: String = "node"): List<File> {
    val extraNodeDir = project.findProperty("org.kotlin.frontend.node.dir")?.let { listOf(it.toString()) } ?: emptyList()
    val paths = whereIs(command, extraNodeDir)

    if (paths.isEmpty()) {
        project.logger.debug("No executable $command found in ${extraNodeDir + splitEnvironmentPath()}")
        throw GradleException("No executable $command found")
    }

    return paths
}

private val Suffixes = if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    listOf(".exe", ".bat", ".cmd")
} else {
    listOf("", ".sh", ".bin", ".app")
}