package org.jetbrains.kotlin.gradle.frontend.util

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal val Project.kotlinExtension: KotlinProjectExtension
    get() = extensions.getByName("kotlin") as KotlinProjectExtension

internal val Project.multiplatformExtension: KotlinMultiplatformExtension?
    get() = kotlinExtension as? KotlinMultiplatformExtension