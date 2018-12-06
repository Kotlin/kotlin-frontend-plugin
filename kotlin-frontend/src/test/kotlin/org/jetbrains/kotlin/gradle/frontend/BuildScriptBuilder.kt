package org.jetbrains.kotlin.gradle.frontend

import groovy.json.*
import java.io.*
import java.util.*

class BuildScriptBuilder {
    var kotlinVersion = "1.0.6"

    val scriptClassPath = ArrayList<Any>()
    val compileDependencies = ArrayList<String>()

    val applyPlugins = ArrayList<String>()

    fun applyFrontendPlugin() {
        applyPlugins += "org.jetbrains.kotlin.frontend"
    }

    fun applyKotlin2JsPlugin() {
        applyPlugins += "kotlin2js"
    }

    fun applyDcePlugin() {
        applyPlugins += "kotlin-dce-js"
    }

    fun addJsDependency() {
        if (kotlinVersion.startsWith("1.0."))
            compileDependencies += "org.jetbrains.kotlin:kotlin-js-library:$kotlinVersion"
        else if (kotlinVersion.startsWith("1.1.") || kotlinVersion.startsWith("1.2.") || kotlinVersion.startsWith("1.3."))
            compileDependencies += "org.jetbrains.kotlin:kotlin-stdlib-js:$kotlinVersion"
        else
            throw IllegalArgumentException("Only 1.0, 1.1 and 1.2 kotlin supported")
    }

    fun build(body: Builder.() -> Unit = {}): String {
        val builder = Builder()

        builder.apply {
            block("buildscript") {
                line("ext.kotlin_version = ${JsonOutput.toJson(kotlinVersion)}")

                repositories()

                block("dependencies") {
                    dependencies("classpath", scriptClassPath)
                }
            }

            for (plugin in applyPlugins) {
                line("apply plugin: ${JsonOutput.toJson(plugin)}")
            }

            repositories()

            block("dependencies") {
                dependencies("compile", compileDependencies)
            }

            body()
        }

        return builder.build()
    }

    private fun Builder.dependencies(type: String, list: List<Any>) {
        for (dep in list) {
            if (dep is String) {
                if (dep.startsWith(":")) {
                    line("$type project(${JsonOutput.toJson(dep)})")
                } else {
                    line("$type ${JsonOutput.toJson(dep)}")
                }
            } else if (dep is File) {
                line("$type(files(${JsonOutput.toJson(dep.absolutePath)}))")
            } else {
                throw IllegalArgumentException("Unsupported dependency type $dep")
            }
        }
    }

    private fun Builder.repositories() {
        block("repositories") {
            line("jcenter()")

            if (kotlinVersion.endsWith("-SNAPSHOT")) {
                mavenRepo("https://oss.sonatype.org/content/repositories/snapshots")
            } else if (kotlinVersion.startsWith("1.1.0-dev")) {
                mavenRepo("https://dl.bintray.com/kotlin/kotlin-dev")
            }
        }
    }

    private fun Builder.mavenRepo(url: String) {
        block("maven") {
            line("url ${JsonOutput.toJson(url)}")
        }
    }

    inner class Builder {
        private val sb = StringBuilder(1024)
        var level: Int = 0

        fun line(text: String) {
            ensureLineStart()
            indent()
            appendDirect(text)
        }

        fun appendDirect(s: String) {
            sb.append(s)
        }

        fun ensureLineStart() {
            if (!sb.endsWith("\n")) {
                sb.appendln()
            }
        }

        fun build(): String {
            ensureLineStart()
            return sb.toString()
        }
    }

    private fun Builder.indent() {
        for (i in 1..level) {
            appendDirect("    ")
        }
    }
}

fun BuildScriptBuilder.Builder.block(name: String, block: () -> Unit) {
    line("$name {")
    level++
    block()
    level--
    line("}")
}

fun BuildScriptBuilder.Builder.kotlinFrontend(c: () -> Unit) = block("kotlinFrontend", c)
fun BuildScriptBuilder.Builder.compileKotlin2Js(c: () -> Unit) = block("compileKotlin2Js", c)
