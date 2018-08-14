package org.jetbrains.kotlin.gradle.frontend

import org.gradle.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.initialization.*
import org.gradle.api.invocation.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.karma.*
import org.jetbrains.kotlin.gradle.frontend.ktor.*
import org.jetbrains.kotlin.gradle.frontend.npm.*
import org.jetbrains.kotlin.gradle.frontend.rollup.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.*
import org.jetbrains.kotlin.gradle.plugin.*
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

        fun tryCallBlock(@Suppress("UNUSED_PARAMETER") appliedPlugin: AppliedPlugin) {
            if (!fired) {
                fired = true
                callBlock()
            }
        }

        project.pluginManager.withPlugin("kotlin2js", ::tryCallBlock)
        project.pluginManager.withPlugin("kotlin-platform-js", ::tryCallBlock)
    }

    override fun apply(project: Project) {
        project.plugins.apply("java")
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

        withKotlinPlugin(project) { kotlin2js, _ ->
            project.afterEvaluate { project ->
                // TODO this need to be done in kotlin plugin itself
                (kotlin2js as KotlinJsCompile).kotlinOptions.outputFile?.let { output ->
                    project.convention.findPlugin(JavaPluginConvention::class.java)
                            ?.sourceSets
                            ?.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                            ?.output
                            ?.dir(File(output).parentFile)
                }

                if (frontend.sourceMaps) {
                    val kotlinVersion = project.plugins.findPlugin(Kotlin2JsPluginWrapper::class.java)?.kotlinPluginVersion

                    if (kotlinVersion != null && compareVersions(kotlinVersion, "1.1.4") < 0) {
                        project.tasks.withType(KotlinJsCompile::class.java).toList().mapNotNull { compileTask ->
                            val task = project.tasks.create(compileTask.name + "RelativizeSMAP", RelativizeSourceMapTask::class.java) { task ->
                                task.compileTask = compileTask
                            }

                            task.dependsOn(compileTask)
                        }
                    } else {
                        project.tasks.withType(KotlinJsCompile::class.java).forEach { task ->
                            if (!task.kotlinOptions.sourceMap) {
                                project.logger.warn("Source map generation is not enabled for kotlin task ${task.name}")
                            }
                        }
                    }
                }

                for ((id, bundles) in frontend.bundles().groupBy { it.bundlerId }) {
                    val bundler = frontend.bundlers[id] ?: throw GradleException("Bundler $id is not supported (or not plugged-in), required for bundles: ${bundles.map { it.bundleName }}")

                    bundler.apply(project, packageManager, packages, bundle, run, stop)
                }

                if (frontend.downloadNodeJsVersion.isNotBlank()) {
                    val downloadTask = project.tasks.create("nodejs-download", NodeJsDownloadTask::class.java) { task ->
                        task.version = frontend.downloadNodeJsVersion
                        if (frontend.nodeJsMirror.isNotBlank()) {
                            task.mirror = frontend.nodeJsMirror
                        }
                    }

                    packages.dependsOn(downloadTask)
                }
            }
        }

        for (runner in listOf(WebPackLauncher, KtorLauncher, KarmaLauncher)) {
            runner.apply(packageManager, project, packages, run, stop)
        }

        withKotlinPlugin(project) { kotlin2js, testKotlin2js ->
            kotlin2js.dependsOn(packages)
            testKotlin2js.dependsOn(packages)

            project.afterEvaluate {
                val compileTask = project.tasks.findByName(kotlin2js.name + "RelativizeSMAP") ?: kotlin2js

                bundle.dependsOn(compileTask)
                run.dependsOn(packages, compileTask)
            }
        }

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

    private fun compareVersions(a: String, b: String): Int {
        return compareVersions(versionToList(a), versionToList(b))
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        return (0 until maxOf(a.size, b.size)).
                map { idx -> a.getOrElse(idx) { 0 }.compareTo(b.getOrElse(idx) { 0 }) }
                .firstOrNull { it != 0 } ?: 0
    }

    private fun versionToList(v: String) = v.split("[._\\-\\s]+".toRegex()).mapNotNull { it.toIntOrNull() }
}

