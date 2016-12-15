package org.jetbrains.kotlin.gradle.frontend

import org.gradle.testkit.runner.*
import org.gradle.testkit.runner.internal.*
import org.jetbrains.kotlin.preprocessor.*
import org.junit.*
import org.junit.rules.*
import java.io.*

abstract class AbstractFrontendTest(val gradleVersion: String, val kotlinVersion: String) {
    protected val port = 8098
    protected val builder = BuildScriptBuilder()

    protected lateinit var buildGradleFile: File
    protected lateinit var srcDir: File
    protected lateinit var runner: GradleRunner

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

        builder.scriptClassPath.addAll(cp)
        builder.scriptClassPath += "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"

        builder.compileDependencies += "org.jetbrains.kotlin:kotlin-js-library:$kotlinVersion"
    }
}