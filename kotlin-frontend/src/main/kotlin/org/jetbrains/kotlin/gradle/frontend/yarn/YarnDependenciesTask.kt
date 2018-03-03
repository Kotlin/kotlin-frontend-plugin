package org.jetbrains.kotlin.gradle.frontend.yarn

import org.jetbrains.kotlin.gradle.frontend.dependencies.DependenciesTask

open class YarnDependenciesTask : DependenciesTask<YarnIndexTask>() {
    override val indexTaskClass: Class<YarnIndexTask> = YarnIndexTask::class.java
}