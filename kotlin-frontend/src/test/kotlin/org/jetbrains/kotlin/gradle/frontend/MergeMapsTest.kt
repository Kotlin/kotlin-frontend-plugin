package org.jetbrains.kotlin.gradle.frontend

import org.jetbrains.kotlin.gradle.frontend.util.*
import org.junit.Test
import kotlin.test.*

class MergeMapsTest {
    @Test
    fun testMergeNonExisting() {
        val a = mapOf("a" to 1)
        val b = mapOf("b" to 2)

        assertMapsEqual(
                mapOf("a" to 1, "b" to 2),
                mergeMaps(a, b)
        )
    }

    @Test
    fun testMergeReplace() {
        val a = mapOf("a" to 1)
        val b = mapOf("a" to 2)

        assertMapsEqual(
                mapOf("a" to 2),
                mergeMaps(a, b)
        )
    }

    @Test
    fun testMergeLists() {
        val a = mapOf("a" to listOf(1, 2))
        val b = mapOf("a" to listOf(3))

        assertMapsEqual(
                mapOf("a" to listOf(1, 2, 3)),
                mergeMaps(a, b)
        )
    }

    @Test
    fun testMergeListAndElement() {
        val a = mapOf("a" to listOf(1, 2))
        val b = mapOf("a" to 3)

        assertMapsEqual(
                mapOf("a" to listOf(1, 2, 3)),
                mergeMaps(a, b)
        )
    }

    @Test
    fun testMergeListAndElement2() {
        val a = mapOf("a" to 1)
        val b = mapOf("a" to listOf(2, 3))

        assertMapsEqual(
                mapOf("a" to listOf(1, 2, 3)),
                mergeMaps(a, b)
        )
    }

    private fun assertMapsEqual(a: Map<*, *>, b: Map<*, *>) {
        assertEquals(a, b)
    }
}