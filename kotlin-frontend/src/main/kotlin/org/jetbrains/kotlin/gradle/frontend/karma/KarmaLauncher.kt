package org.jetbrains.kotlin.gradle.frontend.karma

import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.frontend.KotlinFrontendExtension
import org.jetbrains.kotlin.gradle.frontend.Launcher
import org.jetbrains.kotlin.gradle.frontend.dependencies.PackageManager
import org.jetbrains.kotlin.gradle.frontend.util.withTask
import org.jetbrains.kotlin.gradle.frontend.webpack.GenerateWebPackConfigTask
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import java.io.File

object KarmaLauncher : Launcher {
    override fun apply(packageManager: PackageManager, project: Project,
                       packagesTask: Task, startTask: Task, stopTask: Task) {
        val karma = project.extensions.create("karma", KarmaExtension::class.java)
        project.afterEvaluate {
            val compileTestKotlin = project.tasks.findByPath("compileTestKotlin2Js")

            if (compileTestKotlin != null && (compileTestKotlin as? Kotlin2JsCompile)?.kotlinOptions?.outputFile != null) {
                val karmaConfigTask = project.tasks.create("karma-config", KarmaConfigTask::class.java) {
                    it.onlyIf {
                        checkTestsExist(project)
                    }
                }

                val karmaStart = project.tasks.create("karma-start", KarmaStartStopTask::class.java) {
                    it.start = true

                    it.onlyIf {
                        checkTestsExist(project)
                    }
                }
                val karmaRunSingle = project.tasks.create("karma-run-single", RunKarmaSingleTask::class.java) {
                    it.description = "Runs single karma test run"
                    it.onlyIf {
                        checkTestsExist(project)
                    }
                }

                val karmaStop = project.tasks.create("karma-stop", KarmaStartStopTask::class.java) { it.start = false }

                project.tasks.getByName("test").dependsOn(karmaRunSingle)

                karmaStart.dependsOn(karmaConfigTask)
                karmaRunSingle.dependsOn(karmaConfigTask)

                karmaStart.dependsOn(compileTestKotlin)
                karmaRunSingle.dependsOn(compileTestKotlin)

                startTask.dependsOn(karmaStart)
                stopTask.dependsOn(karmaStop)

                packageManager.apply {
                    require("karma")

                    if (karma.frameworks.contains("qunit")) {
                        require("qunitjs", "1.23.1")
                        require("karma-qunit", "1.2.1")
                    }

                    if (karma.frameworks.contains("jasmine")) {
                        require("karma-jasmine")
                        require("jasmine-core")
                    }

                    if (karma.frameworks.contains("mocha")) {
                        require("karma-mocha")
                        require("mocha")
                    }

                    require("karma-junit-reporter")
                    require("karma-sourcemap-loader")

                    require("karma-phantomjs-launcher")
                    require("phantomjs-prebuilt")

                    var webPackRequireAdded = false
                    project.withTask(GenerateWebPackConfigTask::class) { task ->
                        if (!webPackRequireAdded) {
                            require("karma-webpack")
                            webPackRequireAdded = true
                        }

                        karma.enableWebPack = true
                        karmaConfigTask.dependsOn(task)
                    }

                    if (project.extensions.getByType(KotlinFrontendExtension::class.java).sourceMaps) {
                        require("karma-sourcemap-loader")
                    }
                }
            }
        }

    }

    private fun checkTestsExist(project: Project): Boolean {
        return project.tasks.filterIsInstance<KotlinJsCompile>()
                .filter { it.name.contains("test", ignoreCase = true) && it.kotlinOptions.outputFile != null }
                .any { File(it.kotlinOptions.outputFile).exists() }
    }
}