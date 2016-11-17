package org.jetbrains.kotlin.gradle.frontend

import org.gradle.api.*
import org.gradle.testfixtures.*
import org.junit.*
import kotlin.test.*

class SmokeTest {
    @Test
    fun testProjectConfiguration() {
        val project = ProjectBuilder.builder().withName("test-project").build()
        project.pluginManager.apply("org.jetbrains.kotlin.frontend")

        assertNotNull(project.tasks.getByPath("packages"))

        assertNotNull(project.tasks.getByPath("npm-configure"))
        assertNotNull(project.tasks.getByPath("npm-install"))
        assertNotNull(project.tasks.getByPath("npm-index"))

        assertFailsWith<UnknownTaskException> {
            project.tasks.getByPath("non-existing")
        }

        assertNotNull(project.extensions.getByName("kotlinFrontend"))
        Assert.assertSame(project.extensions.getByName("kotlinFrontend"), project.extensions.getByType(KotlinFrontendExtension::class.java))
    }
}