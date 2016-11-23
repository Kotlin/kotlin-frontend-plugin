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
        val webpack = project.extensions.create("webpack", WebPackExtension::class.java)

        project.afterEvaluate {
            if (hasWebPack(webpack)) {
                packageManager.require(
                        listOf("webpack", "webpack-dev-server")
                                .map { Dependency(it, "*", Dependency.DevelopmentScope) }
                )

                val config = project.tasks.create("webpack-config", GenerateWebPackConfigTask::class.java)
                val bundle = project.tasks.create("webpack-bundle", WebPackBundleTask::class.java) { t ->
                    t.description = "Bundles all scripts and resources with webpack"
                    t.group = WebPackGroup
                }

                bundle.dependsOn(config)

                bundleTask.dependsOn(bundle)
            }
        }
    }

    companion object {
        val WebPackGroup = "webpack"
        fun hasWebPack(webPackExtension: WebPackExtension): Boolean {
            return webPackExtension.entry != null
        }

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