package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*

/**
 * Author: Sergey Mashkov
 */
open class RollupBundleTask : DefaultTask() {
    @get:InputFile
    val configFile by lazy { project.buildDir.resolve(GenerateRollupConfigTask.RollupConfigFileName) }

    @get:Nested
    val bundle by lazy { project.frontendExtension.bundles().filterIsInstance<RollupExtension>().singleOrNull() ?: throw GradleException("Only one rollup bundle is supported") }

    @get:InputFile
    val kotlinJs by lazy { kotlinOutput(project) }

    @get:OutputFile
    val destination by lazy { GenerateWebPackConfigTask.handleFile(project, project.frontendExtension.bundlesDirectory).resolve("${bundle.bundleName}.bundle.js") }

    @TaskAction
    fun runRollupCompile() {
        ProcessBuilder(
                nodePath(project, "node").first().absolutePath,
                project.buildDir.resolve("node_modules/rollup/bin/rollup").absolutePath,
                "-c")
                .directory(project.buildDir)
                .startWithRedirectOnFail(project, "node rollup")
    }
}