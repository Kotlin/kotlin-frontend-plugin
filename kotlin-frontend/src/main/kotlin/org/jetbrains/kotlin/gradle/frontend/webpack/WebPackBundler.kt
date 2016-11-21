package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
class WebPackBundler(val project: Project) : Bundler {

    override fun apply(packageManager: PackageManager, bundleTask: Task, runTask: Task, stopTask: Task) {
        packageManager.require(
                listOf("webpack", "webpack-dev-server")
                        .map { Dependency(it, "*", Dependency.DevelopmentScope) }
        )

        project.extensions.create("webpack", WebPackExtension::class.java)

        val config = project.tasks.create("webpack-config", GenerateWebPackConfigTask::class.java)
        val bundle = project.tasks.create("webpack-bundle", WebPackBundleTask::class.java)
        val run = project.tasks.create("webpack-run", WebPackRunTask::class.java)
        val stop = project.tasks.create("webpack-stop", WebPackStopTask::class.java)

        bundle.dependsOn(config)
        run.dependsOn(config)

        bundleTask.dependsOn(bundle)
        runTask.dependsOn(run)
        stopTask.dependsOn(stop)

        project.tasks.getByName("clean").dependsOn(stop)
    }

    companion object {
        @Deprecated("Move to common utils")
        fun kotlinOutput(project: Project): File {
            return project.tasks.filterIsInstance<KotlinJsCompile>()
                    .filter { !it.name.contains("test", ignoreCase = true) }
                    .mapNotNull { it.kotlinOptions.outputFile }
                    .map { project.file(it) }
                    .distinct()
                    .singleOrNull()
                    ?.ensureParentDir()
                    ?: throw GradleException("Only one kotlin output directory supported by frontend plugin.")
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
    }
}