package org.jetbrains.kotlin.gradle.frontend.npm

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.frontend.Dependency
import org.jetbrains.kotlin.gradle.frontend.util.mergeMaps
import org.jetbrains.kotlin.gradle.frontend.util.readLinesOrEmpty
import org.jetbrains.kotlin.gradle.frontend.util.toSemver
import org.jetbrains.kotlin.preprocessor.mkdirsOrFail
import java.io.File
import java.util.LinkedHashMap

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
    private val npm = project.extensions.getByName("npm") as NpmExtension

    @Internal
    private val yarn = project.extensions.getByName("yarn") as NpmExtension

    @get:Input
    @Suppress("unused")
    val dependenciesInput: String
        get() {
            return when {
                npm.dependencies.isNotEmpty() -> npm.dependencies.joinToString()
                yarn.dependencies.isNotEmpty() -> yarn.dependencies.joinToString()
                else -> ""
            }
        }

    @get:Input
    @Suppress("unused")
    val devDependenciesInput: String
        get() {
            return when {
                npm.developmentDependencies.isNotEmpty() -> npm.developmentDependencies.joinToString()
                yarn.developmentDependencies.isNotEmpty() -> yarn.developmentDependencies.joinToString()
                else -> ""
            }
        }

    @Suppress("unused")
    @get:Input
    val versionReplacementsInput: String
        get() {
            return when {
                npm.versionReplacements.isNotEmpty() -> npm.versionReplacements.joinToString()
                yarn.versionReplacements.isNotEmpty() -> yarn.versionReplacements.joinToString()
                else -> ""
            }
        }

    @get:Input
    val moduleNames: List<String> by lazy {
        project.tasks.withType(KotlinJsCompile::class.java)
            .filter { !it.name.contains("test", ignoreCase = true) }
            .mapNotNull {
                it.kotlinOptions.outputFile?.substringAfterLast('/')?.substringAfterLast('\\')?.removeSuffix(".js")
            }
    }

    @OutputFile
    lateinit var packageJsonFile: File

    @OutputFile
    lateinit var npmrcFile: File

    val buildPackageJsonFile: File?

    init {
        if (configPartsDir.exists()) {
            (inputs as TaskInputs).dir(configPartsDir)
        }
        buildPackageJsonFile =
                project.convention.findPlugin(JavaPluginConvention::class.java)?.sourceSets?.let { sourceSets ->
                    sourceSets.findByName("main")?.output?.resourcesDir?.resolve("package.json")
                }

        if (buildPackageJsonFile != null) {
            outputs.file(buildPackageJsonFile)
        }

        onlyIf {
            npm.dependencies.isNotEmpty() || npm.developmentDependencies.isNotEmpty()
                    || yarn.dependencies.isNotEmpty() || yarn.developmentDependencies.isNotEmpty()
                    || toolsDependencies.isNotEmpty()
        }
    }

    @TaskAction
    fun generate() {
        logger.info("Configuring npm")

        val npmOrYarn = when {
            npm.dependencies.isNotEmpty() -> npm.dependencies
            yarn.dependencies.isNotEmpty() -> yarn.dependencies
            else -> emptyList<Dependency>()
        }

        val dependencies = npmOrYarn + (project.tasks.filterIsInstance<UnpackGradleDependenciesTask>()
            .map { task ->
                task.resultNames?.map { Dependency(it.name, it.uri, Dependency.RuntimeScope) }
                        ?: task.resultFile.readLinesOrEmpty()
                            .map { it.split("/", limit = 4).map(String::trim) }
                            .filter { it.size == 4 }
                            .map { Dependency(it[0], it[3], Dependency.RuntimeScope) }
            }).flatten() + toolsDependencies.filter { it.scope == Dependency.RuntimeScope }

        val devDependencies = if (npm.developmentDependencies.isNotEmpty()) {
            npm.developmentDependencies
        } else if (yarn.developmentDependencies.isNotEmpty()) {
            yarn.developmentDependencies
        } else {
            mutableListOf()
        }

        devDependencies.addAll(toolsDependencies.filter {
            (it.scope == Dependency.DevelopmentScope) && devDependencies.all { dep ->  dep.name != it.name }
        })

        if (logger.isDebugEnabled) {
            logger.debug(dependencies.joinToString(prefix = "Dependencies:\n", separator = "\n") { "${it.name}: ${it.versionOrUri}" })
        }

        val packagesJson: Map<*, *> = mapOf(
                "name" to (moduleNames.singleOrNull() ?: project.name ?: "noname"),
                "version" to (toSemver(project.version.toString())),
                "description" to "simple description",
                "main" to (moduleNames.singleOrNull()),
                "dependencies" to dependencies.associateBy({ it.name }, { it.versionOrUri }),
                "devDependencies" to devDependencies.associateBy({ it.name }, { it.versionOrUri })
        )

        val number = "^\\d+".toRegex()
        val allIncluded = configPartsDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.canRead() }
                .sortedBy { number.find(it.nameWithoutExtension)?.value?.toInt() ?: 0 }
                .map { LinkedHashMap(JsonSlurper().parse(it) as Map<*, *>) }

        val resultJson = allIncluded.fold(packagesJson, ::mergeMaps)
        packageJsonFile.writeText(JsonBuilder(resultJson).toPrettyString())
        npmrcFile.writeText("""
        progress=false
        # cache-min=3600
        """.trimIndent())

        if (buildPackageJsonFile != null) {
            buildPackageJsonFile.parentFile.mkdirsOrFail()
            packageJsonFile.copyTo(buildPackageJsonFile, overwrite = true)
        }
    }
}
