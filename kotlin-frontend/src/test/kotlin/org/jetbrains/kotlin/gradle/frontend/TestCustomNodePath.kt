package org.jetbrains.kotlin.gradle.frontend

import org.apache.tools.ant.taskdefs.condition.*
import org.jetbrains.kotlin.gradle.frontend.util.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import kotlin.test.*

class TestCustomNodePath {
    @get:Rule
    val folder = TemporaryFolder()

    private val dir by lazy { folder.root }
    private val bin by lazy { dir.resolve("bin") }

    @Before
    fun setUp() {
        folder.create()
        bin.mkdirsOrFail()
    }

    @Test
    fun main() {
        if (!Os.isFamily(Os.FAMILY_UNIX)) {
            return
        }

        bin.resolve("my-vm").writeText("#!/bin/sh\necho $1\n")
        bin.resolve("my-vm").setExecutable(true)

        bin.resolve("my-script.my-vm").writeText("#!/usr/bin/env my-vm\nhere goes my super script\n")
        bin.resolve("my-script.my-vm").setExecutable(true)

        val f = whereIs("my-script.my-vm", listOf(bin.absolutePath)).first()

        assertEquals(bin.resolve("my-script.my-vm"), f)

        val out = dir.resolve("log.txt")

        val process = ProcessBuilder(f.absolutePath).redirectErrorStream(true).redirectOutput(out).addCommandPathToSystemPath().start()

        assertEquals(0, process.waitFor())
        assertEquals(f.absolutePath, out.readText().trim())
    }
}