package org.jetbrains.kotlin.gradle.frontend.util

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.frontend.*
import kotlin.reflect.*

inline fun <reified T : Task> Project.withTask(noinline block: (T) -> Unit) = withTask(T::class, block)

fun <T : Task> Project.withTask(type: KClass<T>, block: (T) -> Unit) {
    val javaType = type.java
    val existing = project.tasks.withType(javaType)
    existing?.forEach { task ->
        block(task)
    }

    project.tasks.whenTaskAdded { task ->
        if (javaType.isInstance(task)) {
            @Suppress("UNCHECKED_CAST")
            block(task as T)
        }
    }
}

val Project.frontendExtension: KotlinFrontendExtension
    get() = project.extensions.getByType(KotlinFrontendExtension::class.java)
