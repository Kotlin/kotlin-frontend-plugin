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
    private var tasksDefined = false

    override fun require(dependencies: List<Dependency>) {
        requiredDependencies.addAll(dependencies)

        defineTasks()
    }

    override fun install(project: Project) {
        listOf(UnpackGradleDependenciesTask::class.java,
                GeneratePackagesJsonTask::class.java,
                NpmInstallTask::class.java,
                NpmIndexTask::class.java,
                NpmDependenciesTask::class.java
        ).flatMap { project.tasks.withType(it) }
                .filterNot { it.state.executed || it.state.skipped || it.state.upToDate }
                .forEach { t ->
                    t.logger.lifecycle(":${t.name} (configure)")
                    t.execute()
                }
    }

    override fun apply(containerTask: Task) {
        project.extensions.create("npm", NpmExtension::class.java)

        project.afterEvaluate {
            defineTasks()
        }
    }

    private fun defineTasks() {
        if (!tasksDefined) {
            val npm = project.extensions.getByType(NpmExtension::class.java)!!

            if (npm.dependencies.isNotEmpty() || npm.developmentDependencies.isNotEmpty() || project.projectDir.resolve("package.json.d").exists() || requiredDependencies.isNotEmpty()) {
                project.configurations.getByName("compile").dependencies.add(DefaultSelfResolvingDependency(object : AbstractFileCollection() {
                    override fun getFiles(): MutableSet<File> {
                        return project.tasks.filterIsInstance<NpmDependenciesTask>().flatMap { it.results }.toMutableSet()
                    }

                    override fun getDisplayName(): String {
                        return "npm-dependencies"
                    }
                }))

                val unpack = project.tasks.create("npm-preunpack", UnpackGradleDependenciesTask::class.java)
                val configure = project.tasks.create("npm-configure", GeneratePackagesJsonTask::class.java) { task ->
                    task.description = "Generate package.json and prepare for npm"
                    task.group = NpmGroup

                    task.dependenciesProvider = { requiredDependencies }
                    task.packageJsonFile = packageJsonFile
                }
                val install = project.tasks.create("npm-install", NpmInstallTask::class.java) { task ->
                    task.description = "Install npm packages"
                    task.group = NpmGroup

                    task.packageJsonFile = packageJsonFile
                }
                val index = project.tasks.create("npm-index", NpmIndexTask::class.java)
                val setup = project.tasks.create("npm-deps", NpmDependenciesTask::class.java)
                val npmAll = project.tasks.create("npm") { task ->
                    task.description = "Configure npm and install packages"
                    task.group = NpmGroup
                }

                configure.dependsOn(unpack)
                install.dependsOn(configure)
                index.dependsOn(install)
                setup.dependsOn(index)
                npmAll.dependsOn(setup)

                project.tasks.getByName("packages").dependsOn(npmAll)
                tasksDefined = true
            }
        }
    }

    companion object {
        val NpmGroup = "NPM"
    }
}