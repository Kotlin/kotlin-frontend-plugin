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
import java.net.*
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
${cp.joinToString("\n") { "            classpath(project.files(" + JsonOutput.toJson(it.absolutePath) + "))" }}
            }
        }

        apply plugin: 'kotlin2js'

        repositories {
            jcenter()
        }

        dependencies {
            compile "org.jetbrains.kotlin:kotlin-js-library:$kotlinVersion"
        }
        """.trimIndent() + "\n\n")
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

        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-preunpack")?.outcome)
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

        val runner = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments("npm")
                .withGradleVersion(gradleVersion)


        val result = runner.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-configure")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-install")?.outcome)
        assertNull(result.task(":webpack-bundle"))

        assertTrue { projectDir.root.resolve("build/node_modules/style-loader").isDirectory }

        val rerunResult = runner.build()

        assertEquals(TaskOutcome.UP_TO_DATE, rerunResult.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, rerunResult.task(":npm-configure")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, rerunResult.task(":npm-install")?.outcome)

        buildGradleFile.writeText(buildGradleFile.readText().replace("dependency", "devDependency"))

        val rerunResult2 = runner.build()

        assertEquals(TaskOutcome.UP_TO_DATE, rerunResult2.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, rerunResult2.task(":npm-configure")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, rerunResult2.task(":npm-install")?.outcome)
    }

    @Test
    fun testBundleWithParts() {
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

        val runner = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments("bundle")
                .withGradleVersion(gradleVersion)

        val result = runner.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-config")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-bundle")?.outcome)

        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
        assertTrue { projectDir.root.resolve("build/bundle/main.bundle.js").isFile }

        val noChangesRerunResult = runner.build()
        assertEquals(TaskOutcome.UP_TO_DATE, noChangesRerunResult.task(":webpack-config")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, noChangesRerunResult.task(":webpack-bundle")?.outcome)

        projectDir.root.resolve("webpack.config.d").mkdirsOrFail()
        projectDir.root.resolve("webpack.config.d/part.js").writeText("""
        // this is a part
        """.trimIndent())

        val rerunResult = runner.build()

        assertEquals(TaskOutcome.SUCCESS, rerunResult.task(":webpack-config")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, rerunResult.task(":webpack-bundle")?.outcome)

        assertTrue { "this is a part" in projectDir.root.resolve("build/webpack.config.js").readText() }
    }

    @Test
    fun testWebPackRunAndStop() {
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
            println("my script content")
        }
        """)

        val runner = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withArguments("run")
                .withGradleVersion(gradleVersion)

        val result = runner.build()

        try {
            assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-config")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-run")?.outcome)
            assertNull(result.task(":karma-start"))
            assertNull(result.task(":ktor-start"))

            assertFalse { projectDir.root.resolve("build/bundle/main.bundle.js").exists() }
            val bundleContent = URL("http://localhost:8088/main.bundle.js").openStream().reader().use { it.readText() }
            assertTrue { "webpackBootstrap" in bundleContent }
            assertTrue { "my script content" in bundleContent }
        } finally {
            val stopResult = runner.withArguments("stop").build()
            assertEquals(TaskOutcome.SUCCESS, stopResult.task(":webpack-stop")?.outcome)

            assertFails {
                URL("http://localhost:8088/main.bundle.js").openStream().readBytes()
            }
        }
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