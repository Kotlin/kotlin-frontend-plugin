package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.frontend.Launcher
import org.jetbrains.kotlin.gradle.frontend.RelativizeSourceMapTask
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.frontend.dependencies.PackageManager
import org.jetbrains.kotlin.gradle.frontend.util.frontendExtension
import org.jetbrains.kotlin.gradle.frontend.util.withTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJsDce

object WebPackLauncher : Launcher {
    override fun apply(packageManager: PackageManager, project: Project,
                       packagesTask: Task, startTask: Task, stopTask: Task) {
        project.afterEvaluate {
            if (project.frontendExtension.bundles().any { it is WebPackExtension }) {
                val run = project.tasks.create("webpack-run", WebPackRunTask::class.java) { t ->
                    t.start = true
                    t.description = "Start webpack dev server (if not yet running)"
                    t.group = WebPackBundler.WebPackGroup
                }
                val stop = project.tasks.create("webpack-stop", WebPackRunTask::class.java) { t ->
                    t.start = false
                    t.description = "Stop webpack dev server (if running)"
                    t.group = WebPackBundler.WebPackGroup
                }

                project.withTask(GenerateWebPackConfigTask::class) { task ->
                    run.dependsOn(task)
                }
                project.withTask(RelativizeSourceMapTask::class) { task ->
                    run.dependsOn(task)
                }

                project.withTask<KotlinJsCompile> { task ->
                    run.dependsOn(task)
                }
                project.withTask<KotlinJsDce> { task ->
                    run.dependsOn(task)
                }
                project.withTask<ProcessResources> { task ->
                    run.dependsOn(task)
                }

                run.dependsOn(packagesTask)

                startTask.dependsOn(run)
                stopTask.dependsOn(stop)
            }
        }
    }
}