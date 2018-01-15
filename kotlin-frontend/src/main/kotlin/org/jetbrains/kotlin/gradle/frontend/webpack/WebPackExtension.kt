package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.config.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*

open class WebPackExtension(project: Project) : BundleConfig {
    override val bundlerId: String
        get() = "webpack"

    override var bundleName = project.name!!

    override var sourceMapEnabled: Boolean = project.frontendExtension.sourceMaps

    var contentPath: File? = null

    var publicPath: String = "/"

    var host: String = "localhost"

    var port: Int = 8088

    var proxyUrl: String = ""

    var stats: String = "errors-only"

    var webpackConfigFile: Any? = null
}