package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*

/**
 * Author: Sergey Mashkov
 */
open class RollupBundleTask : DefaultTask() {
    @get:InputFile
    val configFile by lazy { project.buildDir.resolve(GenerateRollupConfigTask.RollupConfigFileName) }

    @get:Input
    val moduleName by lazy { project.extensions.getByType(KotlinFrontendExtension::class.java).moduleName }

    @get:Nested
    val config by lazy { project.extensions.getByType(RollupExtension::class.java) }

    @get:InputFile
    val kotlinJs by lazy { WebPackBundler.kotlinOutput(project) }

    @get:OutputFile
    val destination by lazy { project.buildDir.resolve("bundle").resolve("$moduleName.bundle.js") }

    @TaskAction
    fun runRollupCompile() {
        ProcessBuilder("node", project.buildDir.resolve("node_modules/rollup/bin/rollup").absolutePath, "-c")
                .directory(project.buildDir)
                .startWithRedirectOnFail(project)
    }
}