package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlin.gradle.frontend.dependencies.PackageManager

/**
 * @author Sergey Mashkov
 */
interface Launcher {
    fun apply(packageManagers: List<PackageManager>, project: Project, packagesTask: Task, startTask: Task, stopTask: Task)
}