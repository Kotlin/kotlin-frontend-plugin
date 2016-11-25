package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.tasks.*

open class KotlinFrontendExtension {
    @Input
    var sourceMaps: Boolean = false

    @Input
    var moduleName: String = ""
}