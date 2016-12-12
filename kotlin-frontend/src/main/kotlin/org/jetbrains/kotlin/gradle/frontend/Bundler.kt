package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.config.*

/**
 * @author Sergey Mashkov
 */
interface Bundler<C : BundleConfig> {
    val bundlerId: String
    fun createConfig(project: Project): C
    fun apply(project: Project, packageManager: PackageManager, bundleTask: Task, runTask: Task, stopTask: Task)
}