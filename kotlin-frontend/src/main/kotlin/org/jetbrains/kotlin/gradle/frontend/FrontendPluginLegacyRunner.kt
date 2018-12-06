package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.AppliedPlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.plugin.Kotlin2JsPluginWrapper

class FrontendPluginLegacyRunner : FrontendPluginRunner() {
    override fun apply(project: Project) {
        project.plugins.apply("java")

        super.apply(project)
    }

    override fun createJsTargets(project: Project, services: JsProjectServices, settings: KotlinFrontendExtension) {
        withKotlinPlugin(project) { kotlin2js, testKotlin2js ->
            createJsTarget(project, services, settings, kotlin2js, testKotlin2js)
        }
    }

    private fun withKotlinPlugin(project: Project, block: (kotlin2js: Task, testKotlin2js: Task) -> Unit) {
        var fired = false

        fun callBlock() {
            val kotlin2js = project.tasks.getByPath("compileKotlin2Js")
            val testKotlin2js = project.tasks.getByPath("compileTestKotlin2Js")

            block(kotlin2js, testKotlin2js)
        }

        fun tryCallBlock(@Suppress("UNUSED_PARAMETER") appliedPlugin: AppliedPlugin) {
            if (!fired) {
                fired = true
                callBlock()
            }
        }

        project.pluginManager.withPlugin("kotlin2js", ::tryCallBlock)
        project.pluginManager.withPlugin("kotlin-platform-js", ::tryCallBlock)
    }


    override fun configureJsTarget(target: JsTarget) {
        target.testCompileTask.dependsOn(target.mainCompileTask)
        super.configureJsTarget(target)
    }

    override fun processTargetSourceMaps(target: JsTarget) {
        with(target) {
            val kotlinVersion = project.plugins.findPlugin(Kotlin2JsPluginWrapper::class.java)?.kotlinPluginVersion

            if (kotlinVersion != null && compareVersions(kotlinVersion, "1.1.4") < 0) {
                project.tasks.withType(KotlinJsCompile::class.java).toList().mapNotNull { compileTask ->
                    val task = project.tasks.create(compileTask.name + "RelativizeSMAP", RelativizeSourceMapTask::class.java) { task ->
                        task.compileTask = compileTask
                    }

                    task.dependsOn(compileTask)
                }
            } else super.processTargetSourceMaps(target)
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        return compareVersions(versionToList(a), versionToList(b))
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        return (0 until maxOf(a.size, b.size)).map { idx -> a.getOrElse(idx) { 0 }.compareTo(b.getOrElse(idx) { 0 }) }
                .firstOrNull { it != 0 } ?: 0
    }

    private fun versionToList(v: String) = v.split("[._\\-\\s]+".toRegex()).mapNotNull { it.toIntOrNull() }
}