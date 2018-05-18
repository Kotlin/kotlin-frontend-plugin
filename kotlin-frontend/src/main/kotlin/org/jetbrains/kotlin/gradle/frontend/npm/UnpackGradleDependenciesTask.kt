package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.Dependency
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.jetbrains.kotlin.preprocessor.*
import org.jetbrains.kotlin.utils.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class UnpackGradleDependenciesTask : DefaultTask() {
    @Internal
    lateinit var dependenciesProvider: () -> List<Dependency>

    @get:Input
    val compileConfiguration: Configuration
        get() = project.configurations.getByName("compile")

    @OutputFile
    val resultFile = unpackFile(project)

    @Internal
    var resultNames: MutableList<NameVersionsUri>? = null

    @Internal
    private val npm: NpmExtension = project.extensions.findByType(NpmExtension::class.java)!!

    @get:Input
    val replacementsInput: String
        get() = npm.versionReplacements.joinToString()

    init {
        onlyIf {
            npm.dependencies.isNotEmpty() || npm.developmentDependencies.isNotEmpty() || dependenciesProvider().isNotEmpty()
        }
    }

    @TaskAction
    fun unpackLibraries() {
        resultNames = mutableListOf()
        val out = project.buildDir.resolve("node_modules_imported")

        out.mkdirsOrFail()

        val projectArtifacts = compileConfiguration.allDependencies
                .filterIsInstance<ProjectDependency>()
                .flatMap { it.dependencyProject.configurations }
                .flatMap { it.allArtifacts }
                .map { it.file.canonicalFile.absolutePath }
                .toSet()

        compileConfiguration.resolvedConfiguration.resolvedArtifacts
//                .filter { it.file.canonicalFile.absolutePath !in projectArtifacts }
                .filter { it.file.exists() && LibraryUtils.isKotlinJavascriptLibrary(it.file) }
                .forEach { artifact ->
                    @Suppress("UNCHECKED_CAST")
                    val existingPackageJson = project.zipTree(artifact.file).firstOrNull { it.name == "package.json" }?.let { JsonSlurper().parse(it) as Map<String, Any> }

                    val name = existingPackageJson?.get("name")?.toString()
                            ?: getJsModuleName(artifact.file)
                            ?: artifact.name
                            ?: artifact.id.displayName
                            ?: artifact.file.nameWithoutExtension

                    val outDir = out.resolve(name)
                    outDir.mkdirsOrFail()

                    logger.debug("Unpack to node_modules from ${artifact.file} to $outDir")
                    project.tasks.create("npm-unpack-$name", Copy::class.java).from(project.zipTree(artifact.file)).into(outDir).execute()

                    if (existingPackageJson == null) {
                        val version = npm.versionReplacements.singleOrNull { it.name == artifact.name }?.versionOrUri
                                ?: toSemver(artifact.moduleVersion.id.version)

                        val packageJson = mapOf(
                                "name" to name,
                                "version" to version,
                                "main" to "$name.js",
                                "_source" to "gradle"
                        )

                        outDir.resolve("package.json").bufferedWriter().use { out ->
                            out.appendln(JsonBuilder(packageJson).toPrettyString())
                        }

                        resultNames?.add(NameVersionsUri(name, artifact.moduleVersion.id.version, version, outDir.toLocalURI()))
                    } else {
                        val existingVersion = existingPackageJson["version"]?.toString() ?: toSemver(null)

                        resultNames?.add(NameVersionsUri(name, artifact.moduleVersion.id.version, existingVersion, artifact.file.toLocalURI()))
                    }
                }

        resultFile.bufferedWriter().use { writer -> resultNames?.joinTo(writer, separator = "\n", postfix = "\n") { "${it.name}/${it.version}/${it.semver}/${it.uri}" } }
    }

    data class NameVersionsUri(val name: String, val version: String, val semver: String, val uri: String)

    private val moduleNamePattern = """\s*//\s*Kotlin\.kotlin_module_metadata\(\s*\d+\s*,\s*("[^"]+")""".toRegex()
    private fun getJsModuleName(file: File): String? {
        return project.zipTree(file)
                .filter { it.name.endsWith(".meta.js") && it.canRead() }
                .mapNotNull { moduleNamePattern.find(it.readText())?.groupValues?.get(1) }
                .mapNotNull { JsonSlurper().parseText(it)?.toString() }
                .singleOrNull()
    }

    companion object {
        fun unpackFile(project: Project) = project.buildDir.resolve(".unpack.txt")
    }
}