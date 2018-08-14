package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class NpmIndexTask : DefaultTask() {
    @Input
    val nodeModulesDir: File = project.buildDir.resolve("node_modules")

    @OutputFile
    val modulesWithDtsList: File = project.buildDir.resolve(".modules.with.types.txt")

    @OutputFile
    val kotlinModulesList: File = project.buildDir.resolve(".modules.with.kotlin.txt")

    @TaskAction
    fun findTypeScripts() {
        modulesWithDtsList.bufferedWriter().use { out ->
            project.fileTree(nodeModulesDir)
                    .filter { it.name == "typings.json" || (it.name == "package.json" && packageJsonContainsTypes(it)) }
                    .map { it.parentFile!! }
                    .distinct()
                    .joinToLines(out) { it.path }
        }
    }

    @TaskAction
    fun findKotlinModules() {
        kotlinModulesList.bufferedWriter().use { out ->
            project.fileTree(nodeModulesDir)
                    .filter { it.extension.let { it == "jar" || it == "kotlin_module" } || it.name.endsWith(".meta.js") }
                    .mapNotNull { file ->
                        when (file.extension) {
                            "jar" -> file
                            "kotlin_module" -> {
                                when {
                                    file.parentFile.name == "META-INF" -> file.parentFile.parentFile
                                    else -> null
                                }
                            }
                            "js" -> {
                                if (file.bufferedReader(bufferSize = 512).use { it.readAndCompare("// Kotlin.kotlin_module_metadata") }) {
                                    file.parentFile
                                } else {
                                    null
                                }
                            }
                            else -> null
                        }
                    }
                    .distinct()
                    .filter { it.resolve("package.json").let { packageJson ->
                        !packageJson.exists() || (JsonSlurper().parse(packageJson) as Map<*, *>)["_source"] != "gradle"
                        true
                    } }
                    .joinToLines(out) { it.path }
        }
    }

    private fun packageJsonContainsTypes(file: File): Boolean {
        val parsedFile = JsonSlurper().parse(file)
        return (parsedFile is Map<*, *> && parsedFile["typings"] != null)
    }

    private fun <T> Iterable<T>.joinToLines(o: Appendable, transform: (T) -> CharSequence) {
        joinTo(o, separator = "\n", postfix = "\n", transform = transform)
    }

    private fun Reader.readAndCompare(prefix: CharSequence): Boolean {
        for (idx in 0..prefix.length - 1) {
            val rc = read()
            if (rc == -1 || rc.toChar() != prefix[idx]) {
                return false
            }
        }

        return true
    }
}