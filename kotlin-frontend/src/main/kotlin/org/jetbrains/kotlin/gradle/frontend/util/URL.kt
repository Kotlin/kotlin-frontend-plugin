package org.jetbrains.kotlin.gradle.frontend.util

import java.io.*

fun File.toLocalURI() = toURI().toASCIIString().replaceFirst("file:/", "file://")
