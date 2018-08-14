package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.gradle.api.file.*
import org.jetbrains.kotlin.gradle.frontend.*

object RollupBundler : Bundler<RollupExtension> {
    override val bundlerId = "rollup"

    override fun createConfig(project: Project) = RollupExtension(project)

    override fun apply(project: Project, packageManager: PackageManager,
                       packagesTask: Task, bundleTask: Task, runTask: Task, stopTask: Task) {
        packageManager.require(
                listOf("rollup", "rollup-plugin-node-resolve", "rollup-plugin-commonjs")
                        .map { Dependency(it, "*", Dependency.DevelopmentScope) })

        val config = project.tasks.create("rollup-config", GenerateRollupConfigTask::class.java) { task ->
            task.description = "Generate rollup config"
            task.group = RollupGroup
        }

        val bundle = project.tasks.create("rollup-bundle", RollupBundleTask::class.java) { task ->
            task.description = "Bundle all scripts and resources with rollup"
            task.group = RollupGroup
        }

        bundle.dependsOn(config, packagesTask)

        bundleTask.dependsOn(bundle)
    }

    override fun outputFiles(project: Project): FileCollection {
        return listOf(
                project.tasks.withType(GenerateRollupConfigTask::class.java).map { it.outputs.files },
                project.tasks.withType(RollupBundleTask::class.java).map { it.outputs.files }
        ).flatten()
                .filterNotNull()
                .takeIf { it.isNotEmpty() }
                ?.reduce { a, b -> a + b } ?: project.files()
    }

    const val RollupGroup = "Rollup"
}