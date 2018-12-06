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
import java.util.logging.Logger
import kotlin.reflect.full.declaredMemberProperties

private val logger = Logger.getLogger("kotlin-frontend-plugin")

class FrontendPluginNewRunner : FrontendPluginRunner() {
    val KotlinSingleJavaTargetExtension.internalTarget: KotlinTarget
        get() = KotlinSingleJavaTargetExtension::class.declaredMemberProperties.find { it.name == "target" }!!.get(this) as KotlinTarget

    override fun createJsTargets(project: Project, services: JsProjectServices, settings: KotlinFrontendExtension) {
        val kotlinExtension = project.multiplatformExtension ?: run {
            project.pluginManager.apply(Kotlin2JsPluginWrapper::class.java)
            project.kotlinExtension as KotlinSingleJavaTargetExtension
        }

        fun forEachJsTarget(action: (KotlinTarget) -> Unit) {
            when (kotlinExtension) {
                is KotlinSingleJavaTargetExtension -> action(kotlinExtension.internalTarget)
                is KotlinMultiplatformExtension ->
                    kotlinExtension.targets
                            .matching { it.platformType == KotlinPlatformType.js }
                            .all { action(it) }
            }
        }

        val targets = mutableListOf<JsTargetCompilations>()

        forEachJsTarget { target ->
            val jsTarget = JsTargetCompilations(target)

            target.compilations.all { compilation ->
                when (compilation.name) {
                    KotlinCompilation.MAIN_COMPILATION_NAME -> jsTarget.main = compilation
                    KotlinCompilation.TEST_COMPILATION_NAME -> jsTarget.test = compilation
                    else -> logger.warning("Unsupported compilation ${target.name}.${compilation.name}. " +
                            "Only \"main\" and \"test\" compilations are supported by kotlin-frontend-plugin")
                }
            }

            targets.add(jsTarget)
        }

        targets.take(1).forEach {
            createJsTarget(
                    project,
                    services,
                    settings,
                    project.tasks.findByName(it.main!!.compileKotlinTaskName)!!,
                    project.tasks.findByName(it.test!!.compileKotlinTaskName)!!,
                    it.target.targetName // todo: null for default
            )
        }
    }

    class JsTargetCompilations(val target: KotlinTarget) {
        var main: KotlinCompilation? = null
        var test: KotlinCompilation? = null
    }
}