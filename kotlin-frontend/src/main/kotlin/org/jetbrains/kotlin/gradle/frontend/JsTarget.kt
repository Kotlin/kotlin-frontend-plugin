package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project
import org.gradle.api.Task

class JsTarget(
        val project: Project,
        val services: JsProjectServices,
        val config: KotlinFrontendExtension,
        val mainCompileTask: Task,
        val testCompileTask: Task,
        val packagesTask: Task,
        val bundleTask: Task,
        val runTask: Task,
        val stopTask: Task
) {

}