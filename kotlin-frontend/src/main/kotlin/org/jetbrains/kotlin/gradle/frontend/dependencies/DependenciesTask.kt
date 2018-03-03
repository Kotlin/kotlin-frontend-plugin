package org.jetbrains.kotlin.gradle.frontend.dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.HashSet

/**
 * @author Sergey Mashkov
 */
abstract class DependenciesTask<T : IndexTask> : DefaultTask() {
    var results: List<File> = emptyList()

    @get:InputFile
    val unpackedFiles: File by lazy { UnpackGradleDependenciesTask.unpackFile(project) }

    abstract val indexTaskClass: Class<T>

    @TaskAction
    fun action() {
        val handled = HashSet<File>()
        project.tasks.withType(indexTaskClass) { task ->
            if (handled.add(task.kotlinModulesList) && task.kotlinModulesList.canRead()) {
                val imported = if (unpackedFiles.canRead()) {
                    unpackedFiles.readLines()
                        .map {
                            it.trim()
                                .removeSuffix("/")
                                .removeSuffix(File.separator)
                                .substringAfterLast('/')
                                .substringAfterLast(File.separatorChar)
                        }
                        .distinct()
                        .toSet()
                } else emptySet()

                val dirs = task.kotlinModulesList.readLines()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map(::File)
                    .filter(File::exists)
                    .filter {
                        val parts = it.absolutePath.split(File.separator).dropWhile { it != "node_modules" }

                        parts.size < 2 || parts[1] !in imported
                    }

                results = dirs
            }
        }
    }
}