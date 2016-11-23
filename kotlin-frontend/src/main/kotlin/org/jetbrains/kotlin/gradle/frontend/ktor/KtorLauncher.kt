package org.jetbrains.kotlin.gradle.frontend.ktor

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*

/**
 * @author Sergey Mashkov
 */
object KtorLauncher : Launcher {
    override fun apply(project: Project, startTask: Task, stopTask: Task) {
        val ktorRun = project.tasks.create("ktor-run", KtorStartStopTask::class.java)
        val ktorStop = project.tasks.create("ktor-stop", KtorStartStopTask::class.java) { t -> t.start = false }

        ktorRun.dependsOn(project.tasks.getByName("build"))

        startTask.dependsOn(ktorRun)
        stopTask.dependsOn(ktorStop)
    }
}