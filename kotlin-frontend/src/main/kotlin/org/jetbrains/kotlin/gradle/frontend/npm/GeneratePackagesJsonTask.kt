package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.utils.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class GeneratePackagesJsonTask : DefaultTask() {
    @get:InputFiles
    val unpackResults: List<File>
        get() = project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { it.resultFile }

    @InputDirectory
    val configPartsDir = project.projectDir.resolve("package.json.d")

    @Nested
    val npm: NpmExtension = project.extensions.findByType(NpmExtension::class.java)

    @OutputFile
    lateinit var packageJsonFile: File

    @TaskAction
    fun generate() {
        logger.info("Configuring npm")

        val dependencies = npm.dependencies + (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>().map { task ->
            task.resultNames?.toList() ?: task.resultFile.readLines()
                    .map { it.split("=").map(String::trim) }
                    .filter { it.size == 2 }
                    .map { it[0] to it[1] }
        }).flatten()

        if (logger.isDebugEnabled) {
            logger.debug(dependencies.joinToString(prefix = "Dependencies:\n", separator = "\n") { "${it.first}: ${it.second}" })
        }

        val packagesJson = mapOf(
                "name" to (project.name ?: "noname"),
                "description" to "simple description",
                "dependencies" to dependencies.associateBy({ it.first }, { it.second }),
                "devDependencies" to npm.developmentDependencies.associateBy({ it.first }, { it.second })
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