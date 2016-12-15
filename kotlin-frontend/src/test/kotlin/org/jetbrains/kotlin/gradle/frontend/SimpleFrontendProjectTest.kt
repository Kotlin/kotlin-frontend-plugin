package org.jetbrains.kotlin.gradle.frontend

import org.gradle.testkit.runner.*
import org.jetbrains.kotlin.preprocessor.*
import org.junit.*
import org.junit.runner.*
import org.junit.runners.*
import org.junit.runners.Parameterized.*
import java.net.*
import kotlin.test.*

@RunWith(Parameterized::class)
class SimpleFrontendProjectTest(gradleVersion: String, kotlinVersion: String) : AbstractFrontendTest(gradleVersion, kotlinVersion) {
    @Test
    fun testEmptyProject() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build())

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
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            block("compileKotlin2Js") {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
        }
        """)

        val result = runner.withArguments("bundle").build()

        assertNull(result.task(":webpack-bundle"))
        assertNull(result.task(":npm-install"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin2Js")?.outcome)
        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
    }

    @Test
    fun testSimpleProjectWebPackBundle() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("webpackBundle") {
                    line("port = $port")
                    line("bundleName = \"main\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
        }
        """)

        val result = runner.withArguments("bundle").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-install")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-config")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-bundle")?.outcome)

        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
        assertTrue { projectDir.root.resolve("build/bundle/main.bundle.js").isFile }
    }

    @Test
    fun testSimpleProjectWebPackBundleFail() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("webpackBundle") {
                    line("port = $port")
                    line("bundleName = \"main\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
        }
        """)

        projectDir.root.resolve("webpack.config.d").mkdirsOrFail()
        projectDir.root.resolve("webpack.config.d/failure.js").writeText("""
        letsFailHere()
        """.trimIndent())

        val result = runner.withArguments("bundle").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":webpack-bundle")?.outcome)

        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
        assertFalse { projectDir.root.resolve("build/bundle/main.bundle.js").isFile }
    }

    @Test
    fun testNpmOnly() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("npm") {
                    line("dependency \"style-loader\"")
                }
            }
        })

        val runner = runner.withArguments("npm")
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
    fun testNpmFail() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("npm") {
                    line("dependency \"non-existing-package-here\"")
                }
            }
        })

        val result = runner.withArguments("npm").buildAndFail()

        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-configure")?.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":npm-install")?.outcome)
    }

    @Test
    fun testBundleWithParts() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("webpackBundle") {
                    line("port = $port")
                    line("bundleName = \"main\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
        }
        """)

        val runner = runner.withArguments("bundle")
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
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("webpackBundle") {
                    line("port = $port")
                    line("bundleName = \"main\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
            println("my script content")
        }
        """)

        val runner = runner.withArguments("run")
        val result = runner.build()

        try {
            assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-config")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-run")?.outcome)
            assertNull(result.task(":karma-start"))
            assertNull(result.task(":ktor-start"))

            assertFalse { projectDir.root.resolve("build/bundle/main.bundle.js").exists() }
            val bundleContent = URL("http://localhost:$port/main.bundle.js").openStream().reader().use { it.readText() }
            assertTrue { "webpackBootstrap" in bundleContent }
            assertTrue { "my script content" in bundleContent }
        } finally {
            val stopResult = runner.withArguments("stop").build()
            assertEquals(TaskOutcome.SUCCESS, stopResult.task(":webpack-stop")?.outcome)

            assertFails {
                fail(URL("http://localhost:$port/main.bundle.js").openStream().reader().use { it.readText() })
            }
        }
    }

    @Test
    fun testWebPackRunAmendConfigAndReRun() {
        builder.applyFrontendPlugin()
        buildGradleFile.writeText(builder.build {
            kotlinFrontend {
                block("webpackBundle") {
                    line("port = $port")
                    line("bundleName = \"main\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        srcDir.mkdirsOrFail()
        srcDir.resolve("main.kt").writeText("""
        fun main(args: Array<String>) {
            println("my script content")
        }
        """)

        val runner = runner.withArguments("run")
        val result = runner.build()

        try {
            assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-config")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-run")?.outcome)

            URL("http://localhost:$port/main.bundle.js").openStream().reader().use { it.readText() }

            buildGradleFile.writeText(buildGradleFile.readText().replace("port = $port", "port = $port + 1"))

            val rerunResult = runner.build() // should detect changes and rerun
            assertEquals(TaskOutcome.UP_TO_DATE, rerunResult.task(":webpack-config")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, rerunResult.task(":webpack-run")?.outcome)

            URL("http://localhost:${port + 1}/main.bundle.js").openStream().reader().use { it.readText() }
        } finally {
            val stopResult = runner.withArguments("stop").build()
            assertEquals(TaskOutcome.SUCCESS, stopResult.task(":webpack-stop")?.outcome)

            assertFails {
                fail(URL("http://localhost:$port/main.bundle.js").openStream().reader().use { it.readText() })
            }

            assertFails {
                fail(URL("http://localhost:${port + 1}/main.bundle.js").openStream().reader().use { it.readText() })
            }
        }
    }

    @Test
    fun testMultiModule() {
        buildGradleFile.writeText(builder.build())
        projectDir.root.resolve("settings.gradle").writeText("""
        include 'module1'
        include 'module2'
        """.trimIndent())

        val module1 = projectDir.root.resolve("module1")
        val module2 = projectDir.root.resolve("module2")

        module1.mkdirsOrFail()
        module2.mkdirsOrFail()

        val src1 = module1.resolve("src/main/kotlin")
        val src2 = module2.resolve("src/main/kotlin")

        src1.mkdirsOrFail()
        src2.mkdirsOrFail()

        val builder1 = BuildScriptBuilder()
        val builder2 = BuildScriptBuilder()

        builder1.applyKotlin2JsPlugin()
        builder1.applyFrontendPlugin()
        builder2.applyKotlin2JsPlugin()
        builder2.applyFrontendPlugin()

        builder1.compileDependencies += "org.jetbrains.kotlin:kotlin-js-library:$kotlinVersion"
        builder2.compileDependencies += ":module1"

        module1.resolve("build.gradle").writeText(builder1.build {
            kotlinFrontend {
                block("npm") {
                    line("dependency \"fs\"")
                    line("devDependency \"path\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        module2.resolve("build.gradle").writeText(builder2.build {
            compileKotlin2Js {
                kotlinFrontend {
                    block("npm") {
                        line("dependency \"style-loader\"")
                    }

                    block("webpackBundle") {
                        line("bundleName = \"main\"")
                    }
                }

                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/script.js\"")
            }
        })

        src1.resolve("lib.kt").writeText("""
        package my.test.lib

        fun libFunction() = 1
        """.trimIndent())

        src2.resolve("main.kt").writeText("""
        package my.test.ui
        import my.test.lib.*

        fun main(args: Array<String>) {
            println(libFunction())
        }
        """.trimIndent())

        val result = runner.withArguments("compileKotlin2Js").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":module1:compileKotlin2Js")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":module2:compileKotlin2Js")?.outcome)
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