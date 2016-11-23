package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.ktor.*
import org.jetbrains.kotlin.gradle.frontend.npm.*
import org.jetbrains.kotlin.gradle.frontend.rollup.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*

class FrontendPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("kotlin2js")
        project.pluginManager.withPlugin("kotlin2js") { kotlinPlugin ->
            val kotlin2js = project.tasks.getByPath("compileKotlin2Js")

            project.extensions.create("kotlinFrontend", KotlinFrontendExtension::class.java).apply {
                moduleName = project.name
            }

            val packages = project.tasks.create("packages").apply {
                group = "build"
                description = "Gather and install all JS dependencies (npm)"
            }

            val managers = listOf<PackageManager>(NpmPackageManager(project))
            val packageManager: PackageManager = managers.first()

            for (manager in managers) {
                manager.apply(packages)
            }

            val bundle = project.task("bundle").apply {
                group = "build"
                description = "Bundles all scripts and resources"
            }

            val run = project.task("run").apply {
                group = "run"
                description = "Runs dev-server in background and bundles all if required (possibly in-memory)"
            }
            val stop = project.task("stop").apply {
                group = "run"
                description = "Stops dev-server running in background if running"
            }

            for (bundler in listOf<Bundler>(WebPackBundler(project), RollupBundler(project))) {
                bundler.apply(packageManager, bundle, run, stop)
            }

            for (runner in listOf(WebPackLauncher, KtorLauncher)) {
                runner.apply(project, run, stop)
            }

            kotlin2js.dependsOn(packages)

            bundle.dependsOn(packages)
            bundle.dependsOn(kotlin2js)

            run.dependsOn(packages, kotlin2js)

            project.tasks.getByPath("assemble").dependsOn(bundle)
            project.tasks.getByName("clean").dependsOn(stop)
        }
    }
}

