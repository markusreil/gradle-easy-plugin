package com.mreil.utils

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ProjectExtensionsTest {
    @Test
    fun `root project is root`() {
        val root = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(root.isRoot()).isTrue()
        }
    }

    @Test
    fun `child project is not root`() {
        val root = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().withParent(root).build()

        assertSoftly { softly ->
            softly.assertThat(child.isRoot()).isFalse()
            softly.assertThat(root.isRoot()).isTrue()
        }
    }
}
