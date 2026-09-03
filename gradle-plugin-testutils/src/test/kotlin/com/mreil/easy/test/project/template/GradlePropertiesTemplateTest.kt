package com.mreil.easy.test.project.template

import com.mreil.easy.test.project.GradleTestProject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GradlePropertiesTemplateTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `renders group then version`() {
        assertThat(GradlePropertiesTemplate().render()).isEqualTo("group=com.example\nversion=1.0.0")
    }

    @Test
    fun `omits null values`() {
        assertThat(GradlePropertiesTemplate(group = null, version = null).render()).isEmpty()
        assertThat(GradlePropertiesTemplate(version = null).render()).isEqualTo("group=com.example")
        assertThat(GradlePropertiesTemplate(group = null).render()).isEqualTo("version=1.0.0")
    }

    @Test
    fun `raw template renders content unchanged`() {
        assertThat(RawStringTemplate("a=b").render()).isEqualTo("a=b")
    }

    @Test
    fun `group and version delegate to staged template`() {
        val project = GradleTestProject(File(tempDir, "root"))

        assertThat(project.group).isEqualTo("com.example")
        assertThat(project.version).isEqualTo("1.0.0")

        project.group = "org.acme"
        project.version = null
        project.flushPendingFiles()

        assertThat(File(project.projectDir, "gradle.properties").readText()).isEqualTo("group=org.acme")
        project.cleanup()
    }

    @Test
    fun `group access fails fast on raw staged properties`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.file("gradle.properties", "custom=true")

        assertThatThrownBy { project.group = "org.acme" }.isInstanceOf(IllegalStateException::class.java)

        project.cleanup()
    }
}
