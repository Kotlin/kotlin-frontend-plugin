package org.jetbrains.kotlin.gradle.frontend

import org.gradle.testkit.runner.*
import org.gradle.testkit.runner.internal.*
import org.jetbrains.kotlin.gradle.frontend.util.mkdirsOrFail
import org.junit.*
import org.junit.rules.*
import org.junit.runner.*
import java.io.*

abstract class AbstractFrontendTest(val gradleVersion: String, val kotlinVersion: String) {
    protected val port = 8098
    protected val builder = BuildScriptBuilder()

    protected lateinit var buildGradleFile: File
    protected lateinit var srcDir: File
    protected lateinit var runner: GradleRunner

    @get:Rule
    val testName = TestName()

    @get:Rule
    val failedRule = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description?) {
            val dst = File("build/tests/${testName.methodName.replace("[", "-").replace("]", "")}").apply { mkdirsOrFail() }
            projectDir.root.copyRecursively(dst, true) { file, copyError ->
                System.err.println("Failed to copy $file due to ${copyError.message}")
                OnErrorAction.SKIP
            }
            println("Copied project to ${dst.absolutePath}")
        }

        /*
        // useful for debugging
        override fun succeeded(description: Description?) {
            failed(null, description)
        }
        // */
    }

    @get:Rule
    val projectDir = TemporaryFolder()

    @Before
    fun setUp() {
        projectDir.create()
        projectDir.root.resolve("build/kotlin-build/caches").mkdirsOrFail()

        buildGradleFile = projectDir.root.resolve("build.gradle")
        srcDir = projectDir.root.resolve("src/main/kotlin")

        buildGradleFile.parentFile.mkdirsOrFail()

        runner = GradleRunner.create()
                .withProjectDir(projectDir.root)
                .withGradleVersion(gradleVersion)

        val cp = PluginUnderTestMetadataReading.readImplementationClasspath()
        builder.applyKotlin2JsPlugin()
        builder.kotlinVersion = kotlinVersion

        builder.scriptClassPath += "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
        builder.scriptClassPath.addAll(cp.filter { "org.jetbrains.kotlin" !in it.path.replace("\\", "/") || kotlinVersion in it.name })

        builder.addJsDependency()
    }
}