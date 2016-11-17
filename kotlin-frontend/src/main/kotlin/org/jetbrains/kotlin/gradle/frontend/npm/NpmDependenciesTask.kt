package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.gradle.api.internal.artifacts.dependencies.*
import org.gradle.api.internal.file.*
import org.gradle.api.tasks.*
import java.io.*
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class NpmDependenciesTask : DefaultTask() {
    @TaskAction
    fun action() {
        val handled = HashSet<File>()
        val dependencies = project.configurations.getByName("compile").dependencies

        project.tasks.withType(NpmIndexTask::class.java) { task ->
            if (handled.add(task.kotlinModulesList) && task.kotlinModulesList.canRead()) {
                val dirs = task.kotlinModulesList.readLines()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .map(::File)
                        .filter(File::exists)

                dependencies.add(DefaultSelfResolvingDependency(object: AbstractFileCollection() {
                    override fun getFiles(): MutableSet<File> {
                        return dirs.toMutableSet()
                    }

                    override fun getDisplayName(): String {
                        return "npm-dependencies"
                    }
                }))
            }
        }
    }
}