package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import kotlin.reflect.*

/**
 * @author Sergey Mashkov
 */
object WebPackLauncher : Launcher {
    override fun apply(packageManager: PackageManager, project: Project, startTask: Task, stopTask: Task) {
        project.afterEvaluate {
            if (WebPackBundler.hasWebPack(project.extensions.getByType(WebPackExtension::class.java))) {
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

                startTask.dependsOn(run)
                stopTask.dependsOn(stop)
            }
        }
    }
}