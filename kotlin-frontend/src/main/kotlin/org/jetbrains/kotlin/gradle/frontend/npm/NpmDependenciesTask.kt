package org.jetbrains.kotlin.gradle.frontend.npm

import org.jetbrains.kotlin.gradle.frontend.dependencies.DependenciesTask

/**
 * @author Sergey Mashkov
 */
open class NpmDependenciesTask : DependenciesTask<NpmIndexTask>() {
    override val indexTaskClass: Class<NpmIndexTask> = NpmIndexTask::class.java
}