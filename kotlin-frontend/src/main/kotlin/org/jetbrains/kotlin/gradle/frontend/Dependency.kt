package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.tasks.*
import java.io.*

data class Dependency(@Input val name: String, @Input val versionOrUri: String, @Input val scope: String) : Serializable {
    companion object {
        val DevelopmentScope = "development"
        val RuntimeScope = "runtime"
    }
}
