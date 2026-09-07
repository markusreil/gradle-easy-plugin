package com.mreil.easy

import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun `getEasyExtension returns registered easy extension`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project)

        assertSoftly { softly ->
            softly.assertThat(project.getEasyExtension()).isNotNull()
        }
    }

    @Test
    fun `getEasyExtension fails fast when easy is absent`() {
        val project = ProjectBuilder.builder().build()

        assertThatThrownBy { project.getEasyExtension() }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `findEasyChild returns registered child extension`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project, TestEnabledExtension::class)

        assertSoftly { softly ->
            softly.assertThat(project.findEasyChild<TestEnabledExtension, TestEnabledExtension>()).isNotNull()
        }
    }

    @Test
    fun `findEasyChild returns null when child is absent`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project)

        assertSoftly { softly ->
            softly.assertThat(project.findEasyChild<OtherExtension, OtherExtension>()).isNull()
        }
    }

    @Test
    fun `isEasyChildEnabled is true when child is enabled`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project, TestEnabledExtension::class)

        assertSoftly { softly ->
            softly.assertThat(project.isEasyChildEnabled<TestEnabledExtension>()).isTrue()
        }
    }

    @Test
    fun `isEasyChildEnabled is false when child is disabled`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project, TestEnabledExtension::class)
        project.findEasyChild<TestEnabledExtension, TestEnabledExtension>()?.enabled?.set(false)

        assertSoftly { softly ->
            softly.assertThat(project.isEasyChildEnabled<TestEnabledExtension>()).isFalse()
        }
    }

    @Test
    fun `isEasyChildEnabled is false when child is absent`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project)

        assertSoftly { softly ->
            softly.assertThat(project.isEasyChildEnabled<TestEnabledExtension>()).isFalse()
        }
    }
}
