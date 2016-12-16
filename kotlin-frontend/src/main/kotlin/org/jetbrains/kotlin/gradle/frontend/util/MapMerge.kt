package org.jetbrains.kotlin.gradle.frontend.util

import java.util.*

fun mergeMaps(source: Map<*, *>, override: Map<*, *>): Map<Any?, Any?> {
    val result = LinkedHashMap(source)

    for (key in override.keys) {
        if (key !in result) {
            result[key] = override[key]
        } else {
            val a = result[key]
            val b = override[key]

            when {
                a == b -> Unit

                a is Map<*, *> && b is Map<*, *> -> result[key] = mergeMaps(a, b)
                a is Map<*, *> || b is Map<*, *> -> throw IllegalArgumentException("Couldn't merge two maps for key $key")

                a is Collection<*> && b is Collection<*> -> result[key] = a.toList() + b
                a is Collection<*> -> result[key] = a.toList() + b
                b is Collection<*> -> result[key] = listOf(a) + b

                else -> result[key] = b
            }
        }
    }

    return result
}
