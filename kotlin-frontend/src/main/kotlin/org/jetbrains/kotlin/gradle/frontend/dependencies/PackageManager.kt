package org.jetbrains.kotlin.gradle.frontend.dependencies

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.frontend.Dependency

/**
* @author Sergey Mashkov
*/
interface PackageManager {
    fun apply()
    fun require(dependencies: List<Dependency>)
    fun install(project: Project)
    fun hasDependencies(): Boolean

    fun require(name: String, versionOrUri: String = "*", scope: String = DevelopmentScope) {
        require(listOf(Dependency(name, versionOrUri, scope)))
    }

    companion object {
        val DevelopmentScope = "development"
        val RuntimeScope = "runtime"
    }
}
