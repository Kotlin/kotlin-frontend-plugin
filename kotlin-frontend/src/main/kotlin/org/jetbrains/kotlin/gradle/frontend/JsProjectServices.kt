package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.frontend.npm.NpmPackageManager

class JsProjectServices(val project: Project) {
    val packageManagers: List<PackageManager> = listOf(NpmPackageManager(project))
    val packageManager = packageManagers.first()

    internal val targets = mutableListOf<JsTarget>()
}