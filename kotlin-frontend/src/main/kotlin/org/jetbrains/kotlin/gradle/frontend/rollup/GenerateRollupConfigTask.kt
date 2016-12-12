package org.jetbrains.kotlin.gradle.frontend.rollup

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.frontend.webpack.GenerateWebPackConfigTask.Companion.handleFile

open class GenerateRollupConfigTask : DefaultTask() {
    @Input
    val projectDir = project.projectDir

    @get:OutputFile
    val configFile by lazy { project.buildDir.resolve(RollupConfigFileName) }

    @get:Nested
    val bundle by lazy { project.frontendExtension.bundles().filterIsInstance<RollupExtension>().singleOrNull() ?: throw GradleException("Only one rollup bundle is supported") }

    @get:Input
    val bundleFrom by lazy { kotlinOutput(project).absolutePath!! }

    @get:Input
    val destination by lazy { handleFile(project, project.frontendExtension.bundlesDirectory).resolve("${bundle.bundleName}.bundle.js").absolutePath!! }

    @TaskAction
    fun generate() {
        configFile.bufferedWriter().use { out ->
            out.appendln("""
            import resolve from 'rollup-plugin-node-resolve';
            import commonjs from 'rollup-plugin-commonjs';

            export default {
                entry: ${JsonOutput.toJson(bundleFrom)},
                dest: ${JsonOutput.toJson(destination)},
                format: 'iife',
                moduleName: '${kotlinOutput(project).nameWithoutExtension}',
                //sourceMap: 'inline',
                plugins: [
                    resolve({
                      jsnext: true,
                      main: true,
                      browser: true,
                    }),
                    commonjs(),
                ],
            };
            """.trimIndent())
        }
    }

    companion object {
        val RollupConfigFileName = "rollup.config.js"
    }
}