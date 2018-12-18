package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.tasks.Input
import org.jetbrains.kotlin.gradle.frontend.Dependency
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class NpmExtension {
    val dependencies: MutableList<Dependency> = ArrayList()

    val versionReplacements: MutableList<Dependency> = ArrayList()

    val developmentDependencies: MutableList<Dependency> = ArrayList()

    /**
     * When [Boolean.false] npm install will avoid symlinks on binaries.
     */
    @Input
    var binLinks: Boolean = true

    @JvmOverloads
    fun dependency(name: String, version: String = "*") {
        dependencies.add(Dependency(name, version, Dependency.RuntimeScope))
    }

    fun replaceVersion(name: String, version: String) {
        versionReplacements.add(Dependency(name, version, Dependency.RuntimeScope))
    }

    @JvmOverloads
    fun devDependency(name: String, version: String = "*") {
        developmentDependencies.add(Dependency(name, version, Dependency.DevelopmentScope))
    }
}