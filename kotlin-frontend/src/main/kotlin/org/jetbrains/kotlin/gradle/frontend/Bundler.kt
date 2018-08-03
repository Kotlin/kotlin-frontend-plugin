package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.frontend.config.BundleConfig
import org.jetbrains.kotlin.gradle.frontend.dependencies.PackageManager

/**
 * @author Sergey Mashkov
 */
interface Bundler<C : BundleConfig> {
    val bundlerId: String
    fun createConfig(project: Project): C

    fun apply(project: Project,
              packageManagers: List<PackageManager>,
              packagesTask: Task,
              bundleTask: Task,
              runTask: Task,
              stopTask: Task)

    fun outputFiles(project: Project): FileCollection
}