package org.jetbrains.kotlin.gradle.frontend.ktor

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*

object KtorLauncher : Launcher {
    override fun apply(packageManager: PackageManager, project: Project,
                       packagesTask: Task, startTask: Task, stopTask: Task) {
        val ktor = project.extensions.create("ktor", KtorExtension::class.java)

        project.afterEvaluate {
            if (ktor.port != null) {
                val ktorRun = project.tasks.create("ktor-run", KtorStartStopTask::class.java) { t ->
                    t.description = "Run ktor server"
                    t.group = KtorGroup
                }

                val ktorStop = project.tasks.create("ktor-stop", KtorStartStopTask::class.java) { t ->
                    t.start = false
                    t.description = "Stop ktor server"
                    t.group = KtorGroup
                }

                ktorRun.dependsOn(project.tasks.getByName("assemble"))

                startTask.dependsOn(ktorRun)
                stopTask.dependsOn(ktorStop)
            }
        }
    }

    val KtorGroup = "KTor"
}