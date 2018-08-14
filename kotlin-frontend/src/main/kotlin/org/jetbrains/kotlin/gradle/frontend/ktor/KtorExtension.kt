package org.jetbrains.kotlin.gradle.frontend.ktor

/**
 * @author Sergey Mashkov
 */
open class KtorExtension {
    var port: Int? = null
    var jvmOptions: Array<String> = emptyArray()
    var workDir: Any? = null
}