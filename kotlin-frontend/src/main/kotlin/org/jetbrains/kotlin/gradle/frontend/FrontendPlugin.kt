package org.jetbrains.kotlin.gradle.frontend

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.initialization.*
import org.gradle.api.invocation.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.karma.*
import org.jetbrains.kotlin.gradle.frontend.ktor.*
import org.jetbrains.kotlin.gradle.frontend.npm.*
import org.jetbrains.kotlin.gradle.frontend.rollup.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*

class FrontendPlugin : Plugin<Project> {
    val bundlers = mapOf("webpack" to WebPackBundler, "rollup" to RollupBundler)

    override fun apply(project: Project) {
        project.pluginManager.apply("kotlin2js")
        project.pluginManager.withPlugin("kotlin2js") { kotlinPlugin ->
            val kotlin2js = project.tasks.getByPath("compileKotlin2Js")
            val testKotlin2js = project.tasks.getByPath("compileTestKotlin2Js")
            testKotlin2js.dependsOn(kotlin2js)

            val frontend = project.extensions.create("kotlinFrontend", KotlinFrontendExtension::class.java, project)

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

            project.afterEvaluate {
                val sourceMapTasks = if (frontend.sourceMaps) {
                    project.tasks.withType(KotlinJsCompile::class.java).toList().mapNotNull { compileTask ->
                        if (compileTask.kotlinOptions.outputFile != null) {
                            val task = project.tasks.create(compileTask.name + "RelativizeSMAP", RelativizeSourceMapTask::class.java) {
                                it.compileTask = compileTask
                            }

                            task.dependsOn(compileTask)
                        } else null
                    }
                } else {
                    emptyList()
                }

                for ((id, bundles) in frontend.bundles().groupBy { it.bundlerId }) {
                    val bundler = frontend.bundlers[id] ?: throw GradleException("Bundler $id is not supported (or not plugged-in), required for bundles: ${bundles.map { it.bundleName }}")

                    bundler.apply(project, packageManager, bundle, run, stop)
                }
            }

            for (runner in listOf(WebPackLauncher, KtorLauncher, KarmaLauncher)) {
                runner.apply(packageManager, project, run, stop)
            }


            kotlin2js.dependsOn(packages)
            testKotlin2js.dependsOn(packages)

            bundle.dependsOn(packages)
            bundle.dependsOn(kotlin2js)

            run.dependsOn(packages, kotlin2js)

            project.tasks.getByPath("assemble").dependsOn(bundle)
            project.tasks.getByName("clean").dependsOn(stop)

            var resolutionTriggered = false
            project.gradle.addListener(object : DependencyResolutionListener {
                override fun beforeResolve(dependencies: ResolvableDependencies?) {
                    resolutionTriggered = true
                }

                override fun afterResolve(dependencies: ResolvableDependencies?) {
                }
            })

            project.gradle.addBuildListener(object : BuildListener {
                override fun settingsEvaluated(p0: Settings?) {
                }

                override fun buildFinished(result: BuildResult) {
                    if (resolutionTriggered && result.failure == null && project.gradle.taskGraph == null) {
                        managers.forEach { m ->
                            m.install(project)
                        }
                    }
                }

                override fun projectsLoaded(p0: Gradle?) {
                }

                override fun buildStarted(p0: Gradle?) {
                }

                override fun projectsEvaluated(p0: Gradle?) {
                }
            })
        }
    }
}

