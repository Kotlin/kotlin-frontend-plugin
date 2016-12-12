package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.config.*
import java.io.*

open class WebPackExtension(project: Project) : BundleConfig {
    override val bundlerId: String
        get() = "webpack"

    override var bundleName = project.name!!

    override var sourceMapEnabled: SourceMapType = SourceMapType.DISABLED

    @Internal
    var contentPath: File? = null

    @Input
    var publicPath: String = "/"

    @Input
    var port: Int = 8088

    @Input
    var proxyUrl: String = ""
}