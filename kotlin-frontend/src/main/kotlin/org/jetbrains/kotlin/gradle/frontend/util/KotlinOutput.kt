package org.jetbrains.kotlin.gradle.frontend.util

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import java.io.*

fun kotlinOutput(project: Project) = kotlinOutput(project, "compileKotlin2Js") { !it.name.contains("test", ignoreCase = true) }
fun kotlinTestOutput(project: Project) = kotlinOutput(project, "compileTestKotlin2Js") { it.name.contains("test", ignoreCase = true) }

private inline fun kotlinOutput(project: Project, nameForMessage: String, predicate: (t: KotlinJsCompile) -> Boolean): File {
    val tasks = project.tasks.filterIsInstance<KotlinJsCompile>()
            .filter(predicate)

    val outputs = tasks.mapNotNull { it.kotlinOptions.outputFile }
            .map { project.file(it) }
            .distinct()

    when (outputs.size) {
        0 -> throw GradleException("It should be at least one $nameForMessage configured properly (should have output specified)")
        1 -> return outputs[0].ensureParentDir()
        else -> throw GradleException("Only one configured $nameForMessage is supported")
    }
}

private fun File.ensureParentDir(): File = apply { parentFile.ensureDir() }

private fun File.ensureDir(): File = apply {
    if (mkdirs() && !exists()) {
        throw IOException("Failed to create directory $this")
    }
    if (!isDirectory) {
        throw IOException("Path is not a directory: $this")
    }
}