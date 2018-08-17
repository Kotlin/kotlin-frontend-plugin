package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import kotlin.reflect.KProperty

class NodeModuleVersion(project: Project, module: String) {
    @Suppress("UNCHECKED_CAST")
    val version: String = project.buildDir.resolve("node_modules/$module/package.json")
            .let { JsonSlurper().parse(it) as Map<String, Any?> }["version"]
            ?.let { it as String } ?: throw GradleException("Module \"$module\" not found")

    val major: Int by VersionComponent(0)

    val minor: Int by VersionComponent(1)

    val patch: Int by VersionComponent(2)

    private class VersionComponent(val component: Int) {
        operator fun getValue(thisRef: NodeModuleVersion, property: KProperty<*>): Int {
            return thisRef.version.split(".")
                    .getOrNull(component)
                    ?.toInt() ?: 0
        }
    }
}