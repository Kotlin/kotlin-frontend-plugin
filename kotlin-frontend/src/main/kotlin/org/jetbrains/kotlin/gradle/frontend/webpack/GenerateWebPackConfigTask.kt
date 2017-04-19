package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import groovy.lang.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class GenerateWebPackConfigTask : DefaultTask() {
    private val configsDir: File
        get() = project.projectDir.resolve("webpack.config.d")

    @Input
    val projectDirectory = project.projectDir.absolutePath

    @get:Input
    val contextDir by lazy { kotlinOutput(project).parentFile.absolutePath!! }

    @get:Internal
    val bundles by lazy { project.frontendExtension.bundles().filterIsInstance<WebPackExtension>() }

    @get:Input
    private val bundleNameInput: Any by lazy { bundles.singleOrNull()?.bundleName ?: "" }

    @get:Input
    private val publicPathInput: Any by lazy { bundles.singleOrNull()?.publicPath ?: "" }

    @get:Input
    private val outputFileName by lazy { kotlinOutput(project).name }

    @get:Input
    val bundleDirectory by lazy { handleFile(project, project.frontendExtension.bundlesDirectory) }

    @OutputFile
    val webPackConfigFile: File = project.buildDir.resolve("webpack.config.js")

    init {
        (inputs as TaskInputs).dir(configsDir).optional()

        onlyIf {
            bundles.size == 1 && bundles.single().webpackConfigFile == null
        }
    }

    @TaskAction
    fun generateConfig() {
        val bundle = bundles.singleOrNull() ?: throw GradleException("Only single webpack bundle supported")

        val resolveRoots = mutableListOf(
                File(contextDir).toRelativeString(project.buildDir),
                project.buildDir.resolve("node_modules").toRelativeString(project.buildDir)
        )

        val json = linkedMapOf(
                "context" to contextDir,
                "entry" to mapOf(
                        bundle.bundleName to kotlinOutput(project).nameWithoutExtension.let { "./$it" }
                ),
                "output" to mapOf(
                        "path" to bundleDirectory.absolutePath,
                        "filename" to "[name].bundle.js",
                        "chunkFilename" to "[id].bundle.js",
                        "publicPath" to bundle.publicPath
                ),
                "module" to mapOf(
                        "rules" to emptyList<Any>()
                ),
                "resolve" to mapOf(
                        "modules" to resolveRoots
                ),
                "plugins" to listOf<Any>()
        )

        webPackConfigFile.bufferedWriter().use { out ->
            out.appendln("'use strict';")
            out.appendln()

            out.appendln("var webpack = require('webpack');")

            out.appendln()
            out.append("var config = ")
            out.append(JsonBuilder(json).toPrettyString())
            out.appendln(";")

            out.appendln()
            out.appendln("module.exports = config;")
            out.appendln()

            val p = "\\d+$".toRegex()
            configsDir.listFiles()?.sortedBy { p.find(it.nameWithoutExtension)?.value?.toInt() ?: 0 }?.forEach {
                out.appendln("// from file ${it.path}")
                it.reader().use {
                    it.copyTo(out)
                }
                out.appendln()
            }
        }
    }

    companion object {
        fun handleFile(project: Project, dir: Any): File {
            return when (dir) {
                is String -> File(dir).let { if (it.isAbsolute) it else project.buildDir.resolve(it) }
                is File -> dir
                is Function0<*> -> handleFile(project, dir() ?: throw IllegalArgumentException("function for webPackConfig.bundleDirectory shoudln't return null"))
                is Closure<*> -> handleFile(project, dir.call() ?: throw IllegalArgumentException("closure for webPackConfig.bundleDirectory shoudln't return null"))
                else -> project.file(dir)
            }
        }

    }
}