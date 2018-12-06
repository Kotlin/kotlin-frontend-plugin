package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.frontend.rollup.RollupBundler
import org.jetbrains.kotlin.gradle.frontend.webpack.WebPackBundler

class FrontendPlugin : Plugin<Project> {
    val bundlers = mapOf("webpack" to WebPackBundler, "rollup" to RollupBundler)

    override fun apply(project: Project) {
        lateinit var runner: FrontendPluginRunner

        try {
            Class.forName("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")
            runner = FrontendPluginNewRunner()
        } catch (e: ClassNotFoundException) {
            runner = FrontendPluginLegacyRunner()
        }

        runner.apply(project)
    }
}

