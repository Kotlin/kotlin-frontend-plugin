package org.jetbrains.kotlin.gradle.frontend.npm

import org.gradle.api.tasks.*
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class NpmExtension {
    @Input
    val dependencies: MutableList<Pair<String, String>> = ArrayList()

    @Input
    val developmentDependencies: MutableList<Pair<String, String>> = ArrayList()

    @JvmOverloads
    fun dependency(name: String, version: String = "*") {
        dependencies.add(name to version)
    }

    @JvmOverloads
    fun devDependency(name: String, version: String = "*") {
        developmentDependencies.add(name to version)
    }
}