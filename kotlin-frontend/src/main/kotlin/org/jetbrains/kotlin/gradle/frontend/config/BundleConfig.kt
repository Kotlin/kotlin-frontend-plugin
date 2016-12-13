package org.jetbrains.kotlin.gradle.frontend.config

import org.gradle.api.tasks.*

interface BundleConfig {
    @get:Input
    val bundlerId: String

    @get:Input
    val bundleName: String

    @get:Input
    val sourceMapEnabled: Boolean
}