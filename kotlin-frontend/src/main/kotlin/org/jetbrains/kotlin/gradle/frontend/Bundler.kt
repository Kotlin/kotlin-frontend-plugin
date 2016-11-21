package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*

/**
 * @author Sergey Mashkov
 */
interface Bundler {
    fun apply(packageManager: PackageManager, bundleTask: Task, runTask: Task, stopTask: Task)
}