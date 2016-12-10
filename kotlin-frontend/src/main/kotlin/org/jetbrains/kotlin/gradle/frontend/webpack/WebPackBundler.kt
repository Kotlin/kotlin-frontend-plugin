package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*

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
                if (project.extensions.getByType(KotlinFrontendExtension::class.java).sourceMaps) {
                    packageManager.require("source-map-loader")
                }

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
    }
}
