package org.jetbrains.kotlin.gradle.frontend

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.gradle.frontend.util.toSemver
import org.jetbrains.kotlin.preprocessor.mkdirsOrFail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.net.URL
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
        assertNotFailed(result.task(":npm-install"))
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
        assertNotFailed(result.task(":npm-install"))
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
    fun testSimpleProjectWebPackBundleWithDce() {
        builder.applyDcePlugin()
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
            usedFunction2222()
        }

        private fun usedFunction2222() {
        }

        private fun unusedFunction1111() {
        }
        """)

        val result = runner.withArguments("bundle").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-preunpack")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":npm-install")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-config")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":webpack-bundle")?.outcome)

        assertTrue { projectDir.root.resolve("build/js/script.js").isFile }
        assertTrue { projectDir.root.resolve("build/bundle/main.bundle.js").isFile }

        assertTrue { "unusedFunction1111" in projectDir.root.resolve("build/js/script.js").readText() }
        assertTrue { "unusedFunction1111" !in projectDir.root.resolve("build/bundle/main.bundle.js").readText() }

        assertTrue { "usedFunction2222" in projectDir.root.resolve("build/js/script.js").readText() }
        assertTrue { "usedFunction2222" in projectDir.root.resolve("build/bundle/main.bundle.js").readText() }
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
            line("version '1.0'")

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

        val expectedProjectVersion = toSemver("1.0")
        val expectedKotlinVersion = toSemver(kotlinVersion)

        @Suppress("UNCHECKED_CAST")
        val packageJsonKotlinLocation = projectDir.root.resolve("build/package.json")
                .let { JsonSlurper().parse(it) as Map<String, Any?> }["dependencies"]
                ?.let { it as Map<String, String?> }
                ?.let { it["kotlin"] } ?: fail("No kotlin found in package.json")

        assertTrue { packageJsonKotlinLocation.startsWith("file://") }

        @Suppress("UNCHECKED_CAST")
        assertEquals(expectedKotlinVersion,
                projectDir.root.resolve("build/node_modules/kotlin/package.json")
                        .let { JsonSlurper().parse(it) as Map<String, Any?> }["version"]
        )

        @Suppress("UNCHECKED_CAST")
        assertEquals(expectedProjectVersion,
                projectDir.root.resolve("build/package.json")
                        .let { JsonSlurper().parse(it) as Map<String, Any?> }["version"]
        )

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
            assertNotExecuted(result.task(":karma-start"))
            assertNull(result.task(":ktor-start"))

            assertFalse { projectDir.root.resolve("build/bundle/main.bundle.js").exists() }
            val bundleContent = URL("http://localhost:$port/main.bundle.js").openStream().reader().use { it.readText() }
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

        val builder1 = BuildScriptBuilder().apply {
            this@apply.kotlinVersion = this@SimpleFrontendProjectTest.kotlinVersion
            applyKotlin2JsPlugin()
            applyFrontendPlugin()

            addJsDependency()
        }

        val builder2 = BuildScriptBuilder().apply {
            this@apply.kotlinVersion = this@SimpleFrontendProjectTest.kotlinVersion
            applyKotlin2JsPlugin()
            applyFrontendPlugin()

            compileDependencies += ":module1"
        }

        module1.resolve("build.gradle").writeText(builder1.build {
            kotlinFrontend {
                block("npm") {
                    line("dependency \"tar\"")
                    line("devDependency \"path\"")
                }
            }

            compileKotlin2Js {
                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/test-lib.js\"")
                line("kotlinOptions.moduleKind = \"commonjs\"")
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

                line("kotlinOptions.outputFile = \"\${project.buildDir.path}/js/test-app.js\"")
                line("kotlinOptions.moduleKind = \"commonjs\"")
            }
        })

        src1.resolve("lib.kt").writeText("""
        package my.test.lib

        val const1 = "my-special-const-1"
        fun libFunction() = 1
        """.trimIndent())

        src2.resolve("main.kt").writeText("""
        package my.test.ui
        import my.test.lib.*

        val const1 = "my-special-const-2"

        fun main(args: Array<String>) {
            println(libFunction())
        }
        """.trimIndent())

        val result = runner.withArguments("compileKotlin2Js").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":module1:compileKotlin2Js")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":module2:compileKotlin2Js")?.outcome)

        runner.withArguments("bundle").build()
        assertTrue { module2.resolve("build/bundle/main.bundle.js").exists() }
        val bundleContent = module2.resolve("build/bundle/main.bundle.js").readText()

        assertTrue { "my-special-const-2" in bundleContent }
        assertTrue { "my-special-const-1" in bundleContent }
    }

    private fun assertNotExecuted(task: BuildTask?) {
        if (task != null && task.outcome != TaskOutcome.UP_TO_DATE && task.outcome != TaskOutcome.SKIPPED) {
            fail("${task.path} should be skipped or up-to-date for empty project but it is ${task.outcome}")
        }
    }

    private fun assertNotFailed(task: BuildTask?) {
        assertNotEquals(TaskOutcome.FAILED, task?.outcome, "Task ${task?.path} is failed")
    }

    companion object {
        @JvmStatic
        @Parameters
        fun versions() = listOf(
//                arrayOf("3.1", "1.1.4-3"),
                arrayOf("3.2.1", "1.1.4-3"),
                arrayOf("3.3", "1.1.4-3"),
                arrayOf("3.4.1", "1.1.4-3"),
                arrayOf("3.5", "1.1.4-3"),
                arrayOf("4.1", "1.1.4-3"),
                arrayOf("4.2.1", "1.1.51"),
                arrayOf("4.3.1", "1.1.60"),
                arrayOf("4.3.1", "1.2.0"),
                arrayOf("4.4", "1.2.0"),
                arrayOf("4.4.1", "1.2.21"),
                arrayOf("4.4.1", "1.2.30")
        )
    }
}
