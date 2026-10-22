package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPluginEntryPoint
import com.mreil.easy.publish.central.GenerateJreleaserConfigTask
import com.mreil.gradletest.project.evaluate
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PromotionGateTest {
    private fun withSystemProperty(
        key: String,
        value: String,
        block: () -> Unit,
    ) {
        val old = System.getProperty(key)
        try {
            System.setProperty(key, value)
            block()
        } finally {
            if (old != null) System.setProperty(key, old) else System.clearProperty(key)
        }
    }

    private fun singleProject(version: String = "1.0.0") =
        ProjectBuilder
            .builder()
            .build()
            .also {
                it.group = "com.example"
                it.version = version
                it.pluginManager.apply("java-library")
                it.pluginManager.apply(ProjectPluginEntryPoint::class.java)
            }

    private fun publishOf(project: Project): DefaultEasyPublishExtension =
        (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
            .extensions
            .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension

    @Test
    fun `promoteToCentral without flag wires nothing`() {
        val project = singleProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.promoteToCentral()

        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNull()
        }
    }

    @Test
    fun `promoteToCentral with flag wires central tasks`() {
        val project = singleProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.promoteToCentral()

        withSystemProperty("promote", "true") {
            project.evaluate()
        }

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isInstanceOf(
                GenerateJreleaserConfigTask::class.java,
            )
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNotNull()
        }
    }

    @Test
    fun `deployToCentral and promoteToCentral together fail fast`() {
        val project = singleProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.deployToCentral()
        publish.promoteToCentral()

        val ex = assertThrows<GradleException> { project.evaluate() }
        assertSoftly { softly ->
            softly.assertThat(causesOf(ex)).contains("tier 2 and tier 3")
        }
    }

    @Test
    fun `deployToPortal and promoteToPortal together fail fast`() {
        val project = singleProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.deployToPortal()
        publish.promoteToPortal()

        val ex = assertThrows<GradleException> { project.evaluate() }
        assertSoftly { softly ->
            softly.assertThat(causesOf(ex)).contains("tier 2 and tier 3")
        }
    }

    @Test
    fun `promoteToCentral with flag on snapshot fails fast`() {
        val project = singleProject(version = "1.0.0-SNAPSHOT")
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.promoteToCentral()

        val ex =
            assertThrows<GradleException> {
                withSystemProperty("promote", "true") {
                    project.evaluate()
                }
            }
        assertSoftly { softly ->
            softly.assertThat(causesOf(ex)).contains("requires a release version")
        }
    }

    @Test
    fun `promoteToCentral without flag on snapshot is a silent no-op`() {
        val project = singleProject(version = "1.0.0-SNAPSHOT")
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.promoteToCentral()

        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNull()
        }
    }

    private fun causesOf(ex: Throwable): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = ex
        while (current != null) {
            messages += current.message.orEmpty()
            current = current.cause
        }
        return messages.joinToString("\n")
    }

    @Test
    fun `promoteToPortal with flag wires publishPlugins dependency`() {
        val project = singleProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.promoteToPortal()
        project.tasks.register("publishPlugins")

        val keys = listOf("gradle.publish.key", "gradle.publish.secret", "promote")
        val old = keys.associateWith { System.getProperty(it) }
        try {
            System.setProperty("gradle.publish.key", "test-key")
            System.setProperty("gradle.publish.secret", "test-secret")
            System.setProperty("promote", "true")
            project.evaluate()
            val publishTask = project.tasks.named("publish").get()
            assertSoftly { softly ->
                softly.assertThat(publishTask.dependsOn.map { it.toString() }).anyMatch { it.contains("publishPlugins") }
            }
        } finally {
            old.forEach { (key, value) ->
                if (value != null) System.setProperty(key, value) else System.clearProperty(key)
            }
        }
    }

    @Test
    fun `promoteToPortal without flag wires nothing`() {
        val project = singleProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.promoteToPortal()
        project.tasks.register("publishPlugins")

        project.evaluate()

        val publishTask = project.tasks.getByName("publish")
        assertSoftly { softly ->
            softly.assertThat(publishTask.dependsOn.map { it.toString() }).noneMatch { it.contains("publishPlugins") }
        }
    }
}
