package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.gradle.api.internal.artifacts.dependencies.*
import org.gradle.api.internal.file.*
import org.jetbrains.kotlin.gradle.frontend.*
import java.io.*

class NpmPackageManager(val project: Project) : PackageManager {
    private val packageJsonFile: File
        get() = project.buildDir.resolve("package.json")

    private val requiredDependencies = mutableListOf<Dependency>()

    override fun require(dependencies: List<Dependency>) {
        requiredDependencies.addAll(dependencies)
    }

    override fun apply(containerTask: Task) {
        project.extensions.create("npm", NpmExtension::class.java)

        project.configurations.getByName("compile").dependencies.add(DefaultSelfResolvingDependency(object: AbstractFileCollection() {
            override fun getFiles(): MutableSet<File> {
                return project.tasks.filterIsInstance<NpmDependenciesTask>().flatMap { it.results }.toMutableSet()
            }

            override fun getDisplayName(): String {
                return "npm-dependencies"
            }
        }))

        val unpack = project.tasks.create("npm-preunpack", UnpackGradleDependenciesTask::class.java)
        val configure = project.tasks.create("npm-configure", GeneratePackagesJsonTask::class.java) { task ->
            task.dependenciesProvider = { requiredDependencies }
            task.packageJsonFile = packageJsonFile
        }
        val install = project.tasks.create("npm-install", NpmInstallTask::class.java) { task ->
            task.packageJsonFile = packageJsonFile
        }
        val index = project.tasks.create("npm-index", NpmIndexTask::class.java)
        val setup = project.tasks.create("npm-deps", NpmDependenciesTask::class.java)

        configure.dependsOn(unpack)
        install.dependsOn(configure)
        index.dependsOn(install)
        setup.dependsOn(index)

        containerTask.dependsOn(setup)
    }
}