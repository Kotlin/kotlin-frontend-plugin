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

    @TaskAction
    fun action() {
        val handled = HashSet<File>()
        project.tasks.withType(NpmIndexTask::class.java) { task ->
            if (handled.add(task.kotlinModulesList) && task.kotlinModulesList.canRead()) {
                val dirs = task.kotlinModulesList.readLines()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .map(::File)
                        .filter(File::exists)

                results = dirs
            }
        }
    }
}