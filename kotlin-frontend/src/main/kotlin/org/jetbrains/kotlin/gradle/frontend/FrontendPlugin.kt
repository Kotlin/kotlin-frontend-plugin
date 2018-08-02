package org.jetbrains.kotlin.gradle.frontend

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.frontend.karma.KarmaLauncher
import org.jetbrains.kotlin.gradle.frontend.ktor.KtorLauncher
import org.jetbrains.kotlin.gradle.frontend.npm.NpmPackageManager
import org.jetbrains.kotlin.gradle.frontend.rollup.RollupBundler
import org.jetbrains.kotlin.gradle.frontend.util.NodeJsDownloadTask
import org.jetbrains.kotlin.gradle.frontend.webpack.WebPackBundler
import org.jetbrains.kotlin.gradle.frontend.webpack.WebPackLauncher
import org.jetbrains.kotlin.gradle.frontend.yarn.YarnPackageManager
import org.jetbrains.kotlin.gradle.plugin.Kotlin2JsPluginWrapper
import java.io.File

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

        val managers = listOf(NpmPackageManager(project), YarnPackageManager(project))
        for (manager in managers) {
            manager.apply()
        }

        val packageManager = managers.find { it.hasDependencies() } ?: managers.first()

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
            project.afterEvaluate {
                // TODO this need to be done in kotlin plugin itself
                (kotlin2js as KotlinJsCompile).kotlinOptions.outputFile?.let { output ->
                    project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets?.let { sourceSets ->
                        sourceSets.getByName("main").output.dir(File(output).parentFile)
                    }
                }

                if (frontend.sourceMaps) {
                    val kotlinVersion = project.plugins.findPlugin(Kotlin2JsPluginWrapper::class.java)?.kotlinPluginVersion

                    if (kotlinVersion != null && compareVersions(kotlinVersion, "1.1.4") < 0) {
                        project.tasks.withType(KotlinJsCompile::class.java).toList().mapNotNull { compileTask ->
                            val task = project.tasks.create(compileTask.name + "RelativizeSMAP", RelativizeSourceMapTask::class.java) {
                                it.compileTask = compileTask
                            }

                            task.dependsOn(compileTask)
                        }
                    } else {
                        project.tasks.withType(KotlinJsCompile::class.java).forEach {
                            if (!it.kotlinOptions.sourceMap) {
                                project.logger.warn("Source map generation is not enabled for kotlin task ${it.name}")
                            }
                        }
                    }
                }

                for ((id, bundles) in frontend.bundles().groupBy { it.bundlerId }) {
                    val bundler = frontend.bundlers[id]
                            ?: throw GradleException("Bundler $id is not supported (or not plugged-in), required for bundles: ${bundles.map { it.bundleName }}")

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

