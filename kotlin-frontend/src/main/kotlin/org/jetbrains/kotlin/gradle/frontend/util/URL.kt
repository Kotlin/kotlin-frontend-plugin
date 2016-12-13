package org.jetbrains.kotlin.gradle.frontend.util

import java.io.*

fun File.toLocalURI() = toURI().toASCIIString().replaceFirst("file:[/]+".toRegex(), "file:///")

fun File.readLinesOrEmpty(): List<String> = try {
    if (canRead()) readLines() else emptyList()
} catch (t: FileNotFoundException) {
    emptyList()
}