package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*
import java.util.*

/**
 * @author Sergey Mashkov
 */
open class GeneratePackagesJsonTask : DefaultTask() {
    @Internal
    lateinit var dependenciesProvider: () -> List<Dependency>

    @get:Internal
    val toolsDependencies: List<Dependency> by lazy { dependenciesProvider() }

    @get:Input
    @Suppress("unused")
    val toolsDependenciesInput: String
        get() = toolsDependencies.joinToString()

    @get:InputFiles
    val unpackResults: List<File>
        get() = project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { it.resultFile }

    @Input
    val configPartsDir = project.projectDir.resolve("package.json.d")

    @Internal
    private val npm = project.extensions.getByType(NpmExtension::class.java)!!

    @get:Input
    @Suppress("unused")
    val dependenciesInput: String
        get() = npm.dependencies.joinToString()

    @get:Input
    @Suppress("unused")
    val devDependenciesInput: String
        get() = npm.developmentDependencies.joinToString()

    @Suppress("unused")
    @get:Input
    val versionReplacementsInput: String
        get() = npm.versionReplacements.joinToString()

    @OutputFile
    lateinit var packageJsonFile: File

    init {
        if (configPartsDir.exists()) {
            (inputs as TaskInputs).dir(configPartsDir)
        }

        onlyIf {
            npm.dependencies.isNotEmpty() || npm.developmentDependencies.isNotEmpty() || toolsDependencies.isNotEmpty()
        }
    }

    @TaskAction
    fun generate() {
        logger.info("Configuring npm")

        val dependencies = npm.dependencies + (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { task ->
            task.resultNames?.map { Dependency(it.first, it.second, Dependency.RuntimeScope) } ?: task.resultFile.readLinesOrEmpty()
                    .map { it.split("=").map(String::trim) }
                    .filter { it.size == 2 }
                    .map { Dependency(it[0], it[1], Dependency.RuntimeScope) }
        }).flatten() + toolsDependencies.filter { it.scope == Dependency.RuntimeScope }

        val devDependencies = npm.developmentDependencies + toolsDependencies.filter { it.scope == Dependency.DevelopmentScope }

        if (logger.isDebugEnabled) {
            logger.debug(dependencies.joinToString(prefix = "Dependencies:\n", separator = "\n") { "${it.name}: ${it.versionOrUri}" })
        }

        val packagesJson: Map<*, *> = mapOf(
                "name" to (project.name ?: "noname"),
                "description" to "simple description",
                "dependencies" to dependencies.associateBy({ it.name }, { it.versionOrUri }),
                "devDependencies" to devDependencies.associateBy({ it.name }, { it.versionOrUri })
        )

        val number = "\\d+$".toRegex()
        val allIncluded = configPartsDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.canRead() }
                .sortedBy { number.find(it.nameWithoutExtension)?.value?.toInt() ?: 0 }
                .map { LinkedHashMap(JsonSlurper().parse(it) as Map<*, *>) }

        val resultJson = allIncluded.fold(packagesJson, ::mergeMaps)
        packageJsonFile.writeText(JsonBuilder(resultJson).toPrettyString())
    }
}