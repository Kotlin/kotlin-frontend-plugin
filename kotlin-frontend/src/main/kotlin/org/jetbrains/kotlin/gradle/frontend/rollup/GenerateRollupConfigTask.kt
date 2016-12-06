package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*

/**
 * Author: Sergey Mashkov
 */
open class GenerateRollupConfigTask : DefaultTask() {
    @get:OutputFile
    val configFile by lazy { project.buildDir.resolve(RollupConfigFileName) }

    @get:Input
    val moduleName by lazy { project.extensions.getByType(KotlinFrontendExtension::class.java).moduleName }

    @get:Nested
    val config by lazy { project.extensions.getByType(RollupExtension::class.java)!! }

    @get:Input
    val bundleFrom by lazy { kotlinOutput(project).absolutePath!! }

    @get:Input
    val destination by lazy { project.buildDir.resolve("bundle").resolve("$moduleName.bundle.js").absolutePath!! }

    @TaskAction
    fun generate() {
        configFile.bufferedWriter().use { out ->
            out.appendln("""
            import resolve from 'rollup-plugin-node-resolve';
            import commonjs from 'rollup-plugin-commonjs';

            export default {
                entry: '$bundleFrom',
                dest: '$destination',
                format: 'iife',
                moduleName: '$moduleName',
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