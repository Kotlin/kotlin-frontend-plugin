package org.jetbrains.kotlin.gradle.frontend

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.frontend.karma.KarmaLauncher
import org.jetbrains.kotlin.gradle.frontend.ktor.KtorLauncher
import org.jetbrains.kotlin.gradle.frontend.util.NodeJsDownloadTask
import org.jetbrains.kotlin.gradle.frontend.webpack.WebPackLauncher
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeFirstWord
import java.io.File

abstract class FrontendPluginRunner {
    open fun apply(project: Project) {
        val settings = project.extensions.create("kotlinFrontend", KotlinFrontendExtension::class.java, project)
        val services = JsProjectServices(project)

        createJsTargets(project, services, settings)

        project.afterEvaluate {
            services.targets.forEach {
                configureJsTarget(it)
            }
        }

        addServiceHooks(project, services)
    }

    protected abstract fun createJsTargets(project: Project, services: JsProjectServices, settings: KotlinFrontendExtension)

    private fun addServiceHooks(project: Project, services: JsProjectServices) {
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
                    services.packageManagers.forEach { m ->
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

    private fun Project.task(targetName: String?, name: String): Task {
        val taskName = if (targetName != null) "$targetName${name.capitalizeFirstWord()}" else name
        return project.task(taskName)
    }

    protected fun createJsTarget(
            project: Project,
            services: JsProjectServices,
            settings: KotlinFrontendExtension,
            mainCompileTask: Task,
            testCompileTask: Task,
            name: String? = null
    ) {
        val packagesTask = project.task(name, "packages").apply {
            group = "build"
            description = "Gather and install all JS dependencies (npm)"
        }

        for (manager in services.packageManagers) {
            manager.apply(packagesTask)
        }

        val bundleTask = project.task(name, "bundle").apply {
            group = "build"
            description = "Bundles all scripts and resources"
        }

        val runTask = project.task(name, "run").apply {
            group = "run"
            description = "Runs dev-server in background and bundles all if required (possibly in-memory)"
        }
        val stopTask = project.task(name, "stop").apply {
            group = "run"
            description = "Stops dev-server running in background if running"
        }

        services.targets.add(JsTarget(
                project,
                services,
                settings,
                mainCompileTask,
                testCompileTask,
                packagesTask,
                bundleTask,
                runTask,
                stopTask
        ))

        for (runner in listOf(WebPackLauncher, KtorLauncher, KarmaLauncher)) {
            runner.apply(services.packageManager, project, packagesTask, runTask, stopTask)
        }

        withTask(project, "assemble") { it.dependsOn(bundleTask) }
        withTask(project, "clean") { it.dependsOn(stopTask) }
    }

    open fun configureJsTarget(target: JsTarget) = with(target) {
        mainCompileTask.dependsOn(packagesTask)
        testCompileTask.dependsOn(packagesTask)
        bundleTask.dependsOn(packagesTask)

        // TODO this need to be done in kotlin plugin itself
        (mainCompileTask as KotlinJsCompile).kotlinOptions.outputFile?.let { output ->
            project.convention.findPlugin(JavaPluginConvention::class.java)
                    ?.sourceSets
                    ?.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                    ?.output
                    ?.dir(File(output).parentFile)
        }

        if (config.sourceMaps) {
            processTargetSourceMaps(target)
        }

        val compileTask = project.tasks.findByName(mainCompileTask.name + "RelativizeSMAP") ?: mainCompileTask

        bundleTask.dependsOn(compileTask)
        runTask.dependsOn(packagesTask, compileTask)

        for ((id, bundles) in config.bundles().groupBy { it.bundlerId }) {
            val bundler = config.bundlers[id]
                    ?: throw GradleException("Bundler $id is not supported (or not plugged-in), required for bundles: ${bundles.map { it.bundleName }}")

            bundler.apply(project, services.packageManager, packagesTask, bundleTask, runTask, stopTask)
        }

        if (config.downloadNodeJsVersion.isNotBlank()) {
            val downloadTask = project.tasks.create("nodejs-download", NodeJsDownloadTask::class.java) { task ->
                task.version = config.downloadNodeJsVersion
                if (config.nodeJsMirror.isNotBlank()) {
                    task.mirror = config.nodeJsMirror
                }
            }

            packagesTask.dependsOn(downloadTask)
        }
    }

    protected open fun processTargetSourceMaps(target: JsTarget) = with(target) {
        project.tasks.withType(KotlinJsCompile::class.java).forEach { task ->
            if (!task.kotlinOptions.sourceMap) {
                project.logger.warn("Source map generation is not enabled for kotlin task ${task.name}")
            }
        }
    }

    private fun withTask(project: Project, name: String, block: (Task) -> Unit) {
        project.tasks.findByName(name)?.let(block) ?: project.tasks.whenTaskAdded {
            if (it.name == name) {
                block(it)
            }
        }
    }
}