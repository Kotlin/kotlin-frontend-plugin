package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class GeneratePackagesJsonTask : DefaultTask() {
    @Internal
    lateinit var dependenciesProvider: () -> List<Dependency>

    @get:Nested
    val toolsDependencies: List<Dependency> by lazy { dependenciesProvider() }

    @get:InputFiles
    val unpackResults: List<File>
        get() = project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { it.resultFile }

//    @InputDirectory
    val configPartsDir = project.projectDir.resolve("package.json.d")

    @Nested
    val npm: NpmExtension = project.extensions.findByType(NpmExtension::class.java)

    @OutputFile
    lateinit var packageJsonFile: File

    init {
        if (configPartsDir.exists()) {
            inputs.dir(configPartsDir)
        }
    }

    @TaskAction
    fun generate() {
        logger.info("Configuring npm")

        val dependencies = npm.dependencies + (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { task ->
            task.resultNames?.map { Dependency(it.first, it.second, Dependency.RuntimeScope) } ?: task.resultFile.readLines()
                    .map { it.split("=").map(String::trim) }
                    .filter { it.size == 2 }
                    .map { Dependency(it[0], it[1], Dependency.RuntimeScope) }
        }).flatten() + toolsDependencies.filter { it.scope == Dependency.RuntimeScope }

        val devDependencies = npm.developmentDependencies + toolsDependencies.filter { it.scope == Dependency.DevelopmentScope }

        if (logger.isDebugEnabled) {
            logger.debug(dependencies.joinToString(prefix = "Dependencies:\n", separator = "\n") { "${it.name}: ${it.versionOrUri}" })
        }

        val packagesJson = mapOf(
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
                .map { JsonSlurper().parse(it) }

        packageJsonFile.writeText(JsonBuilder(packagesJson).toPrettyString())
    }
}