package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.Project.DEFAULT_VERSION
import org.jetbrains.kotlin.gradle.frontend.util.toSemver
import org.junit.Test
import kotlin.test.assertEquals

class SemverTest {
    @Test
    fun testEmpty() {
        assertEquals("0.0.0", toSemver(""))
        assertEquals("0.0.0", toSemver(null))
        assertEquals("0.0.0", toSemver(DEFAULT_VERSION))
    }

    @Test
    fun testMissingVersionComponents() {
        assertEquals("1.0.0", toSemver("1"))
        assertEquals("1.0.0", toSemver("1.0"))

        assertEquals("1.0.0-M01", toSemver("1.0-M01"))
        assertEquals("1.0.0-2", toSemver("1.0-2"))
    }

    @Test
    fun testLeadingZeroes() {
        assertEquals("1.0.0", toSemver("01"))
        assertEquals("1.0.0", toSemver("01.0"))
        assertEquals("1.0.0", toSemver("01.0.0"))

        assertEquals("1.0.0-M01", toSemver("01-M01"))
        assertEquals("1.0.0-1", toSemver("01.0.0-01"))
        assertEquals("1.0.0-01M", toSemver("01-01M"))
    }

    @Test
    fun testKeepOriginalSeparatorsWhenPossible() {
        assertEquals("1.1.0-dev-1234", toSemver("1.1-dev-1234"))
        assertEquals("1.1.0-dev-1234", toSemver("1.1-dev--1234"))
        assertEquals("1.1.0-dev.1234", toSemver("1.1-dev.1234"))
        assertEquals("1.1.0-dev.1234", toSemver("1.1-dev..1234"))

        assertEquals("1.1.0-dev-1234-abcd.0", toSemver("1.1-dev-1234-abcd.0"))

        // it should be at least one hyphen so we shouldn't keep dot
        assertEquals("1.1.0-dev", toSemver("1.1.dev"))
    }
}
