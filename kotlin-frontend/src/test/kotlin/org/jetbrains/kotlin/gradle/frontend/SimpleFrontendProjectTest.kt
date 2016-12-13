package org.jetbrains.kotlin.gradle.frontend

import groovy.json.*
import org.gradle.testkit.runner.*
import org.gradle.testkit.runner.internal.*
import org.jetbrains.kotlin.preprocessor.*
import org.junit.*
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.*
import org.junit.runners.Parameterized.*
import java.io.*
import kotlin.test.*

@RunWith(Parameterized::class)
class SimpleFrontendProjectTest(val gradleVersion: String, val kotlinVersion: String) {
    @get:Rule
    val projectDir = TemporaryFolder()

    private val buildGradleFile: File by lazy { projectDir.root.resolve("build.gradle") }
    private val srcDir by lazy { projectDir.root.resolve("src/main/kotlin") }

    @Before
    fun setup() {
        projectDir.create()
        buildGradleFile.parentFile.mkdirsOrFail()
        projectDir.root.resolve("build/kotlin-build/caches").mkdirsOrFail()

        val cp = PluginUnderTestMetadataReading.readImplementationClasspath()
        buildGradleFile.writeText("""
        buildscript {
            ext.kotlin_version = ${JsonOutput.toJson(kotlinVersion)}

            repositories {
                jcenter()
            }

            dependencies {
            classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
            ${cp.joinToString("\n") { "classpath(project.files(" + JsonOutput.toJson(it.absolutePath) + "))" }}
            }
        }

        apply plugin: 'kotlin'

        repositories {
            jcenter()
        }

        dependencies {
            compile "org.jetbrains.kotlin:kotlin-js-library:$kotlinVersion"
        }
        """)
    }

    @Test
    fun testEmptyProject() {
        buildGradleFile.appendText("""
        apply plugin: 'org.jetbrains.kotlin.frontend'

        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir.root)
            .withArguments("bundle")
            .withGradleVersion(gradleVersion)
            .withDebug(true)
            .build()

        assertNull(result.task(":webpack-bundle"))
        assertNull(result.task(":npm-install"))
    }

    @Test
    fun testSimpleProjectNoBundles() {
        val BAX = '$'
        buildGradleFile.appendText("""
        apply plugin: 'org.jetbrains.kotlin.frontend'

        compileKotlin2Js {
            kotlinOptions.outputFile = "$BAX{project.buildDir.path}/js/script.js"
        }
        """.trimIndent())

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
        }
        """)

        val result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments("bundle")
                .withGradleVersion(gradleVersion)
                .build()

        assertNull(result.task(":webpack-bundle"))
        assertNull(result.task(":npm-install"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin2Js")?.outcome)
        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
    }

    @Test
    fun testSimpleProjectWebPackBundle() {
        val BAX = '$'
        buildGradleFile.appendText("""
        apply plugin: 'org.jetbrains.kotlin.frontend'

        kotlinFrontend {
            webpackBundle {
                bundleName = "main"
            }
        }

        compileKotlin2Js {
            kotlinOptions.outputFile = "$BAX{project.buildDir.path}/js/script.js"
        }
        """.trimIndent())

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
        }
        """)

        val result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments("bundle")
                .withGradleVersion(gradleVersion)
                .build()

        assertEquals(TaskOutcome.SKIPPED, result.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-install")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-bundle")?.outcome)

        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
        assertTrue { projectDir.root.resolve("build/bundle/main.bundle.js").isFile }
    }

    @Test
    fun testNpmOnly() {
        val BAX = '$'
        buildGradleFile.appendText("""
        apply plugin: 'org.jetbrains.kotlin.frontend'

        kotlinFrontend {
            npm {
                dependency "style-loader"
            }
        }
        """.trimIndent())

        val result = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments("npm")
                .withGradleVersion(gradleVersion)
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-install")?.outcome)
        assertNull(result.task(":webpack-bundle"))

        assertTrue { projectDir.root.resolve("build/node_modules/style-loader").isDirectory }
    }

    companion object {
        @JvmStatic
        @Parameters
        fun versions() = listOf(
                arrayOf("3.1", "1.0.5-2"),
                arrayOf("3.2.1", "1.0.5-2")
        )
    }
}