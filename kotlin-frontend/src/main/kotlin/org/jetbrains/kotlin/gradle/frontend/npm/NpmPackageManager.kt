package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*
import java.io.*

class NpmPackageManager(val project: Project) : PackageManager {
    private val packageJsonFile: File
        get() = project.buildDir.resolve("package.json")

    override fun apply(containerTask: Task) {
        project.extensions.create("npm", NpmExtension::class.java)

        val configure = project.tasks.create("npm-configure", GeneratePackagesJsonTask::class.java) { task ->
            task.packageJsonFile = packageJsonFile
        }
        val install = project.tasks.create("npm-install", NpmInstallTask::class.java) { task ->
            task.packageJsonFile = packageJsonFile
        }
        val index = project.tasks.create("npm-index", NpmIndexTask::class.java)
        val setup = project.tasks.create("npm-deps", NpmDependenciesTask::class.java)

        install.dependsOn(configure)
        index.dependsOn(install)
        setup.dependsOn(index)

        containerTask.dependsOn(setup)
    }
}