package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class NpmDependenciesTask : DefaultTask() {
    var results: List<File> = emptyList()

    @get:InputFile
    val unpackedFiles: File by lazy { UnpackGradleDependenciesTask.unpackFile(project) }

    @TaskAction
    fun action() {
        val handled = HashSet<File>()
        project.tasks.withType(NpmIndexTask::class.java) { task ->
            if (handled.add(task.kotlinModulesList) && task.kotlinModulesList.canRead()) {
                val imported = if (unpackedFiles.canRead()) {
                    unpackedFiles.readLines()
                            .map { it.trim()
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