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
import java.util.ArrayDeque

/**
 * @author Sergey Mashkov
 */
open class GenerateWebPackConfigTask : DefaultTask() {
    @get:Internal
    private val configsDir: File
        get() = project.projectDir.resolve("webpack.config.d")

    @Input
    val projectDirectory: String = project.projectDir.absolutePath

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
    val defined = project.frontendExtension.defined

    @get:Input
    private val isDceEnabled: Boolean by lazy {
        !project.tasks
                .withType(KotlinJsDce::class.java)
                .none { it.isEnabled }
    }

    init {
        (inputs as TaskInputs).dir(configsDir).optional()

        onlyIf {
            bundles.size == 1 && bundles.single().webpackConfigFile == null
        }
    }

    fun getModuleResolveRoots(testMode: Boolean): List<String> {
        val resolveRoots = mutableListOf<String>()

        val dceOutputFiles = project.tasks
                .withType(KotlinJsDce::class.java)
                .filter { it.isEnabled && !it.name.contains("test", ignoreCase = true) }
                .flatMap { it.outputs.files }

        if (dceOutputFiles.isEmpty() || testMode) {
            resolveRoots.add(getContextDir(testMode).toRelativeString(project.buildDir))

            // Recursively walk the dependency graph and build a set of transitive local projects.
            val allProjects = mutableSetOf<Project>()
            val queue = ArrayDeque<Project>().apply { add(project) }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val dependencies = current.configurations.findByName("compile")?.allDependencies
                    ?.filterIsInstance<ProjectDependency>().orEmpty()
                    .mapNotNull { it.dependencyProject }

                allProjects.addAll(dependencies)
                queue.addAll(dependencies)
            }

            allProjects.flatMap { it.tasks.filterIsInstance<Kotlin2JsCompile>() }
                    .filter { !it.name.contains("test", ignoreCase = true) }
                    .map { project.file(it.outputFileBridge()) }
                    .forEach { resolveRoots.add(it.parentFile.toRelativeString(project.buildDir)) }
        } else {
            resolveRoots.addAll(dceOutputFiles.map { it.toRelativeString(project.buildDir) })
        }

        if (testMode) {
            resolveRoots.add(kotlinOutput(project).absolutePath)
        }

        val sourceSets: SourceSetContainer? = project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets
        val mainSourceSet: SourceSet? = sourceSets?.findByName("main")
        val resources = mainSourceSet?.output?.resourcesDir

        if (resources != null) {
            resolveRoots.add(resources.toRelativeString(project.buildDir))
        }

        // node modules
        resolveRoots.add(project.buildDir.resolve("node_modules").absolutePath)
        resolveRoots.add(project.buildDir.resolve("node_modules").toRelativeString(project.buildDir))

        return resolveRoots
    }

    fun getContextDir(testMode: Boolean): File {
        val dceOutputs = project.tasks
                .withType(KotlinJsDce::class.java)
                .filter { it.isEnabled && !it.name.contains("test", ignoreCase = true) }
                .map { it.destinationDir }
                .firstOrNull()

        return if (dceOutputs == null || testMode) kotlinOutput(project).parentFile.absoluteFile!!
        else dceOutputs.absoluteFile
    }

    @TaskAction
    fun generateConfig() {
        val bundle = bundles.singleOrNull() ?: throw GradleException("Only single webpack bundle supported")

        val resolveRoots = getModuleResolveRoots(false)

        val json = linkedMapOf(
                "mode" to bundle.mode,
                "context" to getContextDir(false).absolutePath,
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


            if (defined.isNotEmpty()) {
                out.append("var defined = ")
                out.append(JsonBuilder(defined).toPrettyString())
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
