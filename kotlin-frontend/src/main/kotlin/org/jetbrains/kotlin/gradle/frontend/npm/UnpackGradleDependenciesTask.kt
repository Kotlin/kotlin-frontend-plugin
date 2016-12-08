package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.preprocessor.*
import org.jetbrains.kotlin.utils.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class UnpackGradleDependenciesTask : DefaultTask() {
    @get:Input
    val compileConfiguration: Configuration
        get() = project.configurations.getByName("compile")!!

    @OutputFile
    val resultFile = unpackFile(project)

    @Internal
    var resultNames: MutableList<Pair<String, String>>? = null

    @Nested
    val npm: NpmExtension = project.extensions.findByType(NpmExtension::class.java)

    @TaskAction
    fun unpackLibraries() {
        resultNames = mutableListOf()
        val out = project.buildDir.resolve("node_modules")

        out.mkdirsOrFail()

        compileConfiguration.resolvedConfiguration.resolvedArtifacts
                .filter { it.file.exists() && LibraryUtils.isKotlinJavascriptLibrary(it.file) }
                .forEach { artifact ->
                    @Suppress("UNCHECKED_CAST")
                    val existingPackageJson = project.zipTree(artifact.file).firstOrNull { it.name == "package.json" }?.let { JsonSlurper().parse(it) as Map<String, Any> }

                    val name = existingPackageJson?.get("name")?.toString()
                            ?: getJsModuleName(artifact.file)
                            ?: LibraryUtils.getKotlinJsModuleName(artifact.file)
                            ?: artifact.name
                            ?: artifact.id.displayName
                            ?: artifact.file.nameWithoutExtension

                    if (existingPackageJson == null) {
                        val outDir = out.resolve(name)
                        outDir.mkdirsOrFail()

                        project.tasks.create("npm-unpack-$name", Copy::class.java).from(project.zipTree(artifact.file)).into(outDir).execute()
                        val version = npm.versionReplacements.singleOrNull { it.name == artifact.name }?.versionOrUri
                            ?: fixVersion(artifact.moduleVersion.id.version)

                        val packageJson = mapOf(
                                "name" to name,
                                "version" to version,
                                "main" to "$name.js",
                                "_source" to "gradle"
                        )

                        outDir.resolve("package.json").bufferedWriter().use { out ->
                            out.appendln(JsonBuilder(packageJson).toPrettyString())
                        }

                        resultNames?.add(name to outDir.toLocalURI())
                    } else {
                        resultNames?.add(name to artifact.file.toLocalURI())
                    }
                }

        resultFile.bufferedWriter().use { out -> resultNames?.joinTo(out, separator = "\n", postfix = "\n") { "${it.first} = ${it.second}" } }
    }

    private val moduleNamePattern = """\s*//\s*Kotlin\.kotlin_module_metadata\(\s*\d+\s*,\s*("[^"]+")""".toRegex()
    private fun getJsModuleName(file: File): String? {
        return project.zipTree(file)
                .filter { it.name.endsWith(".meta.js") && it.canRead() }
                .mapNotNull { moduleNamePattern.find(it.readText())?.groupValues?.get(1) }
                .mapNotNull { JsonSlurper().parseText(it)?.toString() }
                .singleOrNull()
    }

    private fun fixVersion(version: String?) = buildString {
        val parts = version?.split("[._\\-+]+".toRegex()).orEmpty()
        val numericParts = parts.takeWhile { it.all(Char::isDigit) }.take(3)
        val majorMinorPatch = numericParts.padEnd(3, "0")

        majorMinorPatch.joinTo(this, ".")

        val remaining = parts.drop(numericParts.size)
        if (remaining.isNotEmpty()) {
            remaining.joinTo(this, ".", prefix = "-") { it.replace("[^0-9A-Za-z-]+".toRegex(), "-") }
        }
    }

    private fun <T> List<T>.padEnd(size: Int, value: T): List<T> {
        if (this.size >= size) {
            return this
        }

        val result = toMutableList()
        while (result.size < size) {
            result.add(value)
        }

        return result
    }

    private fun File.toLocalURI() = toURI().toASCIIString().replaceFirst("file:/", "file://")

    companion object {
        fun unpackFile(project: Project) = project.buildDir.resolve(".unpack.txt")
    }
}