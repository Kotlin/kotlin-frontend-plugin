package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.config.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import java.io.*

open class WebPackExtension(project: Project) : BundleConfig {
    @get:Input
    override val bundlerId: String
        get() = "webpack"

    @Input
    override var bundleName = project.name!!

    @Input
    override var sourceMapEnabled: Boolean = project.frontendExtension.sourceMaps

    @Input
    @Optional
    var contentPath: File? = null

    @Input
    var publicPath: String = "/"

    @Input
    var host: String = "localhost"

    @Input
    var port: Int = 8088

    @Input
    var proxyUrl: String = ""

    @Input
    var stats: String = "errors-only"

    @Input
    @Optional
    var webpackConfigFile: Any? = null

    @Input
    var mode: String = "development"
}
