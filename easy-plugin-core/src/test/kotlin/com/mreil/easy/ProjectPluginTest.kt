package com.mreil.easy

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ProjectPluginTest {
    @Test
    fun `fails when applied to subproject without root setup`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()

        assertThatThrownBy {
            sub.plugins.apply(ProjectPlugin::class.java)
        }.hasRootCauseInstanceOf(IllegalStateException::class.java)
            .hasStackTraceContaining("non-root project ':sub'")
            .hasStackTraceContaining("root project")
    }

    @Test
    fun `creates extension when applied to root`() {
        val root = ProjectBuilder.builder().withName("root").build()

        root.plugins.apply(ProjectPlugin::class.java)

        assertSoftly { softly ->
            softly.assertThat(root.extensions.findByName(EasyExtension.name)).isNotNull()
        }
    }
}
