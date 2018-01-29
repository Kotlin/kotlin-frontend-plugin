package org.jetbrains.kotlin.gradle.frontend.karma

import org.gradle.api.tasks.*

open class KarmaExtension {
    @Input
    var customConfigFile: String = ""

    @Input
    var port: Int = 9876

    @Input
    var runnerPort: Int = 9100

    @Input
    var reporters: MutableList<String> = mutableListOf("progress", "junit")

    @Input
    var frameworks: MutableList<String> = mutableListOf("qunit")

    @Input
    var browsers = mutableListOf("PhantomJS")

    @Input
    var plugins: MutableList<String> = mutableListOf()

    @Input
    var preprocessors: MutableList<String> = mutableListOf()

    @Input
    var extraConfig: Map<String, Any?> = mapOf()

    @Input
    var enableWebPack = false

    @Input
    var captureTimeout: Int = 60000
}
