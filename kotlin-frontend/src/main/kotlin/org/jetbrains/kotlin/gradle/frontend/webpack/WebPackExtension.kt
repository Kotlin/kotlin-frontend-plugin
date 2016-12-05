package org.jetbrains.kotlin.gradle.frontend.webpack

import org.gradle.api.tasks.*
import java.io.*

/**
 * @author Sergey Mashkov
 */
open class WebPackExtension {
    @Input
    var entry: String? = null // TODO need to be structural

    @Internal
    var contentPath: File? = null

    @Input
    var publicPath: String = "/"

    @Input
    var port: Int = 8088

    @Input
    var proxyUrl: String = ""

    /**
     * a file or a path
     */
    @Input
    var bundleDirectory: Any = "bundle"
}