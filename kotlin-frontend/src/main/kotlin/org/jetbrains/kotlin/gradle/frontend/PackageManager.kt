package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*

/**
* @author Sergey Mashkov
*/
interface PackageManager {
    fun apply(containerTask: Task)
    fun require(dependencies: List<Dependency>)
    fun install(project: Project)

    fun require(name: String, versionOrUri: String = "*", scope: String = DevelopmentScope) {
        require(listOf(Dependency(name, versionOrUri, scope)))
    }

    companion object {
        val DevelopmentScope = "development"
        val RuntimeScope = "runtime"
    }
}
