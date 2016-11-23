package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*

/**
 * Author: Sergey Mashkov
 */
class RollupBundler(val project: Project) : Bundler {
    override fun apply(packageManager: PackageManager, bundleTask: Task, runTask: Task, stopTask: Task) {
        val rollup = project.extensions.create("rollup", RollupExtension::class.java)

        project.afterEvaluate {
            if (rollup.enabled) {
                packageManager.require(
                        listOf("rollup", "rollup-plugin-node-resolve", "rollup-plugin-commonjs")
                                .map { Dependency(it, "*", Dependency.DevelopmentScope) })

                if (project.extensions.findByType(RollupExtension::class.java) != null) {
                    val config = project.tasks.create("rollup-config", GenerateRollupConfigTask::class.java) { task ->
                        task.description = "Generate rollup config"
                        task.group = RollupGroup
                    }

                    val bundle = project.tasks.create("rollup-bundle", RollupBundleTask::class.java) { task ->
                        task.description = "Bundle all scripts and resources with rollup"
                        task.group = RollupGroup
                    }

                    bundle.dependsOn(config)

                    bundleTask.dependsOn(bundle)
                }
            }
        }
    }

    companion object {
        val RollupGroup = "Rollup"
    }
}