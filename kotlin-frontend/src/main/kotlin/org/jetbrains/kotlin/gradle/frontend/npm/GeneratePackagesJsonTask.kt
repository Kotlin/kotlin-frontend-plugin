package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class GeneratePackagesJsonTask : DefaultTask() {
    @Nested
    val npm: NpmExtension = project.extensions.findByType(NpmExtension::class.java)

    @OutputFile
    lateinit var packageJsonFile: File

    @TaskAction
    fun generate() {
        logger.info("Configuring npm")

        if (logger.isDebugEnabled) {
            logger.debug(npm.dependencies.joinToString(prefix = "Dependencies:\n", separator = "\n") { "${it.first}: ${it.second}" })
        }

        val packagesJson = mapOf(
                "name" to (project.name ?: "noname"),
                "description" to "simple description",
                "dependencies" to npm.dependencies.associateBy({ it.first }, { it.second }),
                "devDependencies" to npm.developmentDependencies.associateBy({ it.first }, { it.second })
        )

        packageJsonFile.writeText(JsonBuilder(packagesJson).toPrettyString())
    }
}