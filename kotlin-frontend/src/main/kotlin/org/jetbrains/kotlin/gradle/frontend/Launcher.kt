package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*

/**
 * @author Sergey Mashkov
 */
interface Launcher {
    fun apply(project: Project, startTask: Task, stopTask: Task)
}