package org.jetbrains.kotlin.gradle.frontend.karma

import org.gradle.api.tasks.Input
import java.io.Serializable

/**
 * Custom browner launcher configuration for Karma.
 *
 * http://karma-runner.github.io/2.0/config/browsers.html
 */
open class CustomLauncher : Serializable {
    @Input
    var name: String? = null
    @Input
    var base: String? = null
    @Input
    var flags: List<String> = emptyList()
    @Input
    var displayName: String? = null

    /**
     * @return true when minimalistic configuration is present.
     */
    fun isConfigured() = !name.isNullOrEmpty() && !base.isNullOrBlank()
}