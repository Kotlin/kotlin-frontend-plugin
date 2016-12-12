package org.jetbrains.kotlin.gradle.frontend.rollup

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.frontend.config.*

open class RollupExtension(project: Project) : BundleConfig {
    @Internal
    override val bundlerId = "rollup"

    @Input
    override var bundleName = project.name!!

    @Input
    override val sourceMapEnabled: SourceMapType = SourceMapType.DISABLED
}