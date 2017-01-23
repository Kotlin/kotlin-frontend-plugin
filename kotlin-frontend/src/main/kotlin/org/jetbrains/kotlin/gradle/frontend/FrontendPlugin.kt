package org.jetbrains.kotlin.gradle.frontend

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.initialization.*
import org.gradle.api.invocation.*
import org.gradle.api.plugins.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.karma.*
import org.jetbrains.kotlin.gradle.frontend.ktor.*
import org.jetbrains.kotlin.gradle.frontend.npm.*
import org.jetbrains.kotlin.gradle.frontend.rollup.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*
import java.io.*

class FrontendPlugin : Plugin<Project> {
    val bundlers = mapOf("webpack" to WebPackBundler, "rollup" to RollupBundler)

    private fun withKotlinPlugin(project: Project, block: (kotlin2js: Task, testKotlin2js: Task) -> Unit) {
        var fired = false

        fun callBlock() {
            val kotlin2js = project.tasks.getByPath("compileKotlin2Js")
            val testKotlin2js = project.tasks.getByPath("compileTestKotlin2Js")

            block(kotlin2js, testKotlin2js)
        }

        fun tryCallBlock(appliedPlugin: AppliedPlugin) {
            if (!fired) {
                fired = true
                callBlock()
            }
        }

        project.pluginManager.withPlugin("kotlin2js", ::tryCallBlock)
        project.pluginManager.withPlugin("kotlin-platform-js", ::tryCallBlock)
    }

    override fun apply(project: Project) {
        withKotlinPlugin(project) { kotlin2js, testKotlin2js ->
            testKotlin2js.dependsOn(kotlin2js)
        }

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

        withKotlinPlugin(project, { kotlin2js, testKotlin2js ->
            project.afterEvaluate {
                // TODO this need to be done in kotlin plugin itself
                (kotlin2js as KotlinJsCompile).kotlinOptions.outputFile?.let { output ->
                    project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets?.let { sourceSets ->
                        sourceSets.getByName("main").output.dir(File(output).parentFile)
                    }
                }

                val sourceMapTasks = if (frontend.sourceMaps) {
                    project.tasks.withType(KotlinJsCompile::class.java).toList().mapNotNull { compileTask ->
                        val task = project.tasks.create(compileTask.name + "RelativizeSMAP", RelativizeSourceMapTask::class.java) {
                            it.compileTask = compileTask
                        }

                        task.onlyIf { compileTask.kotlinOptions.outputFile != null }
                        task.dependsOn(compileTask)
                    }
                } else {
                    emptyList()
                }

                for ((id, bundles) in frontend.bundles().groupBy { it.bundlerId }) {
                    val bundler = frontend.bundlers[id] ?: throw GradleException("Bundler $id is not supported (or not plugged-in), required for bundles: ${bundles.map { it.bundleName }}")

                    bundler.apply(project, packageManager, bundle, run, stop)
                }
            }
        })

        for (runner in listOf(WebPackLauncher, KtorLauncher, KarmaLauncher)) {
            runner.apply(packageManager, project, run, stop)
        }

        withKotlinPlugin(project, { kotlin2js, testKotlin2js ->
            kotlin2js.dependsOn(packages)
            testKotlin2js.dependsOn(packages)

            project.afterEvaluate {
                val compileTask = project.tasks.findByName(kotlin2js.name + "RelativizeSMAP") ?: kotlin2js

                bundle.dependsOn(compileTask)
                run.dependsOn(packages, compileTask)
            }
        })

        bundle.dependsOn(packages)

        withTask(project, "assemble") { it.dependsOn(bundle) }
        withTask(project, "clean") { it.dependsOn(stop) }

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

    private fun withTask(project: Project, name: String, block: (Task) -> Unit) {
        project.tasks.findByName(name)?.let(block) ?: project.tasks.whenTaskAdded {
            if (it.name == name) {
                block(it)
            }
        }
    }
}

