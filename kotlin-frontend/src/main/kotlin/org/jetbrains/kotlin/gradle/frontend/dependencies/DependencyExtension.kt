package org.jetbrains.kotlin.gradle.frontend.dependencies

import org.jetbrains.kotlin.gradle.frontend.Dependency
import java.util.ArrayList

open class DependencyExtension {
    val dependencies: MutableList<Dependency> = ArrayList()

    val versionReplacements: MutableList<Dependency> = ArrayList()

    val developmentDependencies: MutableList<Dependency> = ArrayList()

    @JvmOverloads
    fun dependency(name: String, version: String = "*") {
        dependencies.add(
            Dependency(
                name,
                version,
                Dependency.RuntimeScope
            )
        )
    }

    fun replaceVersion(name: String, version: String) {
        versionReplacements.add(
            Dependency(
                name,
                version,
                Dependency.RuntimeScope
            )
        )
    }

    @JvmOverloads
    fun devDependency(name: String, version: String = "*") {
        developmentDependencies.add(
            Dependency(
                name,
                version,
                Dependency.DevelopmentScope
            )
        )
    }
}