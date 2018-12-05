package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleJavaTargetExtension
import org.jetbrains.kotlin.gradle.frontend.util.kotlinExtension
import org.jetbrains.kotlin.gradle.frontend.util.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.Kotlin2JsPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

class Kotlin1270Utils {
    fun apply2(project: Project) {
        val kotlinExtension = project.multiplatformExtension ?: run {
            project.pluginManager.apply(Kotlin2JsPluginWrapper::class.java)
            project.kotlinExtension as KotlinSingleJavaTargetExtension
        }

        fun forEachJsTarget(action: (KotlinTarget) -> Unit) {
            when (kotlinExtension) {
                // is KotlinSingleJavaTargetExtension -> action(kotlinExtension.target)
                is KotlinMultiplatformExtension ->
                    kotlinExtension.targets
                            .matching { it.platformType == KotlinPlatformType.js }
                            .all { action(it) }
            }
        }

        forEachJsTarget {
            it.compilations.all { processCompilation(project, it) }
        }
    }

    fun processCompilation(project: Project, target: KotlinCompilation) {
        println("COMPILATION ${target.compilationName}: ${target.compileKotlinTaskName}, ${target.platformType}")
    }
}