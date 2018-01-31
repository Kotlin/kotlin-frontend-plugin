package org.jetbrains.kotlin.gradle.frontend.webpack

import groovy.json.*
import groovy.lang.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.gradle.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class GenerateWebPackConfigTask : DefaultTask() {
    @get:Internal
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

    @Input
    val exts = project.frontendExtension.defined

    @get:Input
    private val isDceEnabled: Boolean by lazy {
        !project.tasks
                .withType(KotlinJsDce::class.java)
                .filter { it.isEnabled }.isEmpty()
    }

    init {
        (inputs as TaskInputs).dir(configsDir).optional()

        onlyIf {
            bundles.size == 1 && bundles.single().webpackConfigFile == null
        }
    }

    @TaskAction
    fun generateConfig() {
        val bundle = bundles.singleOrNull() ?: throw GradleException("Only single webpack bundle supported")

        val resolveRoots = mutableListOf<String>()

        val dceOutputFiles = project.tasks
                .withType(KotlinJsDce::class.java)
                .filter { it.isEnabled && !it.name.contains("test", ignoreCase = true) }
                .flatMap { it.outputs.files }

        val entryDir = if (dceOutputFiles.isEmpty()) {
            project.configurations.findByName("compile")?.allDependencies
                    ?.filterIsInstance<ProjectDependency>().orEmpty()
                    .mapNotNull { it.dependencyProject }
                    .flatMap { it.tasks.filterIsInstance<Kotlin2JsCompile>() }
                    .filter { !it.name.contains("test", ignoreCase = true) }
                    .map { project.file(it.outputFileBridge()) }
                    .map { resolveRoots.add(it.parentFile.toRelativeString(project.buildDir)) }

            resolveRoots.add(0, File(contextDir).toRelativeString(project.buildDir))
            contextDir
        } else {
            resolveRoots.addAll(dceOutputFiles.map { it.toRelativeString(project.buildDir) })
            dceOutputFiles.first().absolutePath
        }

        val sourceSets: SourceSetContainer? = project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets
        val mainSourceSet: SourceSet? = sourceSets?.findByName("main")
        val resources = mainSourceSet?.output?.resourcesDir

        if (resources != null) {
            resolveRoots.add(resources.toRelativeString(project.buildDir))
        }

        // node modules
        resolveRoots.add(project.buildDir.resolve("node_modules").toRelativeString(project.buildDir))
        resolveRoots.add(project.buildDir.resolve("node_modules").absolutePath)

        val json = linkedMapOf(
                "context" to entryDir,
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


            if (exts.isNotEmpty()) {
                out.append("var defined = ")
                out.append(JsonBuilder(exts).toPrettyString())
                out.appendln(";")

                out.appendln("config.plugins.push(new webpack.DefinePlugin(defined));")
            }

            out.appendln()
            out.appendln("module.exports = config;")
            out.appendln()

            val p = "^\\d+".toRegex()
            configsDir.listFiles()?.sortedBy { p.find(it.nameWithoutExtension)?.value?.toInt() ?: 0 }?.forEach {
                out.appendln("// from file ${it.path}")
                it.reader().use {
                    it.copyTo(out)
                }
                out.appendln()
            }
        }
    }

    private fun Kotlin2JsCompile.outputFileBridge(): File {
        kotlinOptions.outputFile?.let { return project.file(it) }

        outputFile.javaClass.getMethod("getOutputFile")?.let { return project.file(it.invoke(outputFile)) }

        throw IllegalStateException("Unable to locate kotlin2js output file")
    }

    companion object {
        fun handleFile(project: Project, dir: Any): File {
            return when (dir) {
                is String -> File(dir).let { if (it.isAbsolute) it else project.buildDir.resolve(it) }
                is File -> dir
                is Function0<*> -> handleFile(project, dir() ?: throw IllegalArgumentException("function for webPackConfig.bundleDirectory shouldn't return null"))
                is Closure<*> -> handleFile(project, dir.call() ?: throw IllegalArgumentException("closure for webPackConfig.bundleDirectory shouldn't return null"))
                else -> project.file(dir)
            }
        }

    }
}
