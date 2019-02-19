package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*

/**
* @author Sergey Mashkov
*/
interface PackageManager {
    fun onIdeaSync(project: Project)

    fun apply(containerTask: Task)
    fun require(dependencies: List<Dependency>)

    fun require(name: String, versionOrUri: String = "*", scope: String = Dependency.DevelopmentScope) {
        require(listOf(Dependency(name, versionOrUri, scope)))
    }
}
