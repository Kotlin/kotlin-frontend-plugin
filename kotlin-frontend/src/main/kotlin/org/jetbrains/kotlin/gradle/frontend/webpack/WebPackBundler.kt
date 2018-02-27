package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.language.jvm.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.tasks.*

object WebPackBundler : Bundler<WebPackExtension> {

    override val bundlerId = "webpack"

    override fun createConfig(project: Project) = WebPackExtension(project)

    override fun apply(project: Project, packageManager: PackageManager, bundleTask: Task, runTask: Task, stopTask: Task) {
        packageManager.require(
                listOf("webpack", "webpack-cli", "webpack-dev-server")
                        .map { Dependency(it, "*", Dependency.DevelopmentScope) }
        )
        if (project.frontendExtension.sourceMaps) {
            packageManager.require("source-map-loader")
        }

        val config = project.tasks.create("webpack-config", GenerateWebPackConfigTask::class.java)
        val helper = project.tasks.create("webpack-helper", GenerateWebpackHelperTask::class.java) { t ->
            t.dependsOn(config)
        }

        val bundle = project.tasks.create("webpack-bundle", WebPackBundleTask::class.java) { t ->
            t.description = "Bundles all scripts and resources with webpack"
            t.group = WebPackGroup
        }

        bundle.dependsOn(config, helper)
        bundle.dependsOn(*project.tasks.withType(RelativizeSourceMapTask::class.java).toTypedArray())

        bundleTask.dependsOn(bundle)

        project.withTask<KotlinJsDce> { task ->
            bundle.dependsOn(task)
        }
        project.withTask<ProcessResources> { task ->
            bundle.dependsOn(task)
        }
    }

    val WebPackGroup = "webpack"
}
