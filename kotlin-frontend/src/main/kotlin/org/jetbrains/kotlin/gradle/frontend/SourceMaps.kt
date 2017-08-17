package org.jetbrains.kotlin.gradle.frontend

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.*
import java.io.*
import java.net.*

open class RelativizeSourceMapTask : DefaultTask() {
    @Internal
    lateinit var compileTask: KotlinJsCompile

    @get:InputFile
    val input: File? by lazy { compileTask.kotlinOptions.outputFile?.let { project.file(it) }?.let { it.resolveSibling(it.name + ".map") } }

    @get:OutputFile
    val output: File? by lazy { input }

    init {
        onlyIf {
            compileTask.kotlinOptions.outputFile != null && File(compileTask.kotlinOptions.outputFile).exists() && compileTask.kotlinOptions.sourceMap
        }
    }

    @TaskAction
    fun relativize() {
        input?.let { from ->
            output?.let { to ->
                relativizeSourceMap(from, to)
            }
        }
    }
}

fun relativizeSourceMap(input: File, output: File = input.parentFile.resolve(input.name + ".rel"), baseDir: File = input.parentFile) {
    if (!input.exists()) {
        return
    }

    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(input) as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    val sources = json["sources"] as? MutableList<Any?>

    if (sources != null) {
        for (i in sources.indices) {
            val source = sources[i]

            if (source is String) {
                val file = if (":/" in source) {
                    File(URI(source.replace("^file:[/]+".toRegex()) { "file:///" }))
                } else {
                    File(source)
                }

                sources[i] = file.relativeToOrSelf(baseDir).path
            }
        }
    }

    output.bufferedWriter().use {
        JsonBuilder(json).writeTo(it)
    }
}
