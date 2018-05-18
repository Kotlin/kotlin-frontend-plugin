package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*

/**
 * @author Sergey Mashkov
 */
interface Launcher {
    fun apply(packageManager: PackageManager, project: Project, packagesTask: Task, startTask: Task, stopTask: Task)
}