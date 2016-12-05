package org.jetbrains.kotlin.gradle.frontend.util

import org.gradle.api.*
import kotlin.reflect.*

fun <T : Task> Project.withTask(type: KClass<T>, block: (Task) -> Unit) {
    val javaType = type.java
    val existing = project.tasks.withType(javaType)
    existing?.forEach { task ->
        block(task)
    }

    project.tasks.whenTaskAdded { task ->
        if (javaType.isInstance(task)) {
            block(task)
        }
    }
}
