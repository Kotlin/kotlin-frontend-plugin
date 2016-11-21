package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*

/**
 * Author: Sergey Mashkov
 */
class RollupBundler(val project: Project) : Bundler {
    override fun apply(packageManager: PackageManager, bundleTask: Task, runTask: Task, stopTask: Task) {
        packageManager.require(
                listOf("rollup", "rollup-plugin-node-resolve", "rollup-plugin-commonjs")
                        .map { Dependency(it, "*", Dependency.DevelopmentScope) })

        project.extensions.create("rollup", RollupExtension::class.java)

        if (project.extensions.findByType(RollupExtension::class.java) != null) {
            val config = project.tasks.create("rollup-config", GenerateRollupConfigTask::class.java)
            val bundle = project.tasks.create("rollup-bundle", RollupBundleTask::class.java)

            bundle.dependsOn(config)

            bundleTask.dependsOn(bundle)
        }
    }
}