package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import groovy.lang.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class GenerateWebPackConfigTask : DefaultTask() {
//    @get:InputDirectory
    val configsDir: File
        get() = project.projectDir.resolve("webpack.config.d")

    @get:Input
    val contextDir by lazy { WebPackBundler.kotlinOutput(project).parentFile.path!! }

    @get:Nested
    val webPackConfig by lazy { project.extensions.findByType(WebPackExtension::class.java)!! }

    @OutputFile
    val webPackConfigFile: File = project.buildDir.resolve("webpack.config.js")

    init {
        if (configsDir.exists()) {
            (inputs as TaskInputs).dir(configsDir)
        }
    }

    @TaskAction
    fun generateConfig() {
        val bundleDir = handleFile(project, webPackConfig.bundleDirectory)

        val json = mapOf(
                "context" to contextDir,
                "entry" to (webPackConfig.entry ?: ""),
                "output" to mapOf(
                        "path" to bundleDir.absolutePath,
                        "filename" to "[name].bundle.js",
                        "chunkFilename" to "[id].bundle.js",
                        "publicPath" to webPackConfig.publicPath
                ),
                "module" to mapOf(
                        "loaders" to emptyList<Any>()
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