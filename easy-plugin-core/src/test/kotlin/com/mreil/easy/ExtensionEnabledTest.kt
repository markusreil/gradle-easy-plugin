package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class ExtensionEnabledTest {
    abstract class TestSubExtension :
        EasyPluginExtension,
        CanBeEnabled {
        init {
            enabled.convention(true)
        }

        companion object : Named {
            override val name: String = "sub"
        }
    }

    @Test
    fun `reports child extension enabled when settings root hosts it`() {
        val project = ProjectBuilder.builder().build()
        val registry = mock(PluginRegistry::class.java)
        val easy = ExtensionRegistrar(project, project.providers).createSettingsExtension(registry)
        easy.extensions.create(TestSubExtension.name, TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(project.isExtensionEnabled(TestSubExtension::class)).isTrue()
        }
    }

    @Test
    fun `reports extension disabled when settings root has no matching child`() {
        val project = ProjectBuilder.builder().build()
        val registry = mock(PluginRegistry::class.java)
        ExtensionRegistrar(project, project.providers).createSettingsExtension(registry)

        assertSoftly { softly ->
            softly.assertThat(project.isExtensionEnabled(TestSubExtension::class)).isFalse()
        }
    }
}
