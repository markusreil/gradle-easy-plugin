package com.mreil.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ProviderExtensionsTest {
    private val project: Project = ProjectBuilder.builder().build()

    @Test
    fun `present property returns its value`() {
        val value = project.objects.property(String::class.java).apply { set("value") }

        assertThat(value.required("missing")).isEqualTo("value")
    }

    @Test
    fun `present derived provider returns its value`() {
        val value = project.providers.provider { "derived" }

        assertThat(value.required("missing")).isEqualTo("derived")
    }

    @Test
    fun `absent provider throws with message`() {
        val absent = project.objects.property(String::class.java)

        assertThatThrownBy { absent.required("value is required") }
            .isInstanceOf(GradleException::class.java)
            .hasMessage("value is required")
    }
}
