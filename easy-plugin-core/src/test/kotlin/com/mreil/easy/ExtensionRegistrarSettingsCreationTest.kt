package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.reflect.KClass

class ExtensionRegistrarSettingsCreationTest {
    abstract class SettingsChildExtension : EasyPluginExtension {
        companion object : Named {
            override val name: String = "settingsChild"
        }
    }

    abstract class ProjectChildExtension : EasyPluginExtension {
        companion object : Named {
            override val name: String = "projectChild"
        }
    }

    private fun registry(
        settingsExtensions: Set<KClass<out EasyPluginExtension>> = emptySet(),
        projectExtensions: Set<KClass<out EasyPluginExtension>> = emptySet(),
    ): PluginRegistry =
        mock(PluginRegistry::class.java).apply {
            `when`(getSettingsExtensions()).thenReturn(settingsExtensions)
            `when`(getRegisteredExtensions()).thenReturn(projectExtensions)
        }

    @Test
    fun `creates EasySettingsExtension on ExtensionAware target`() {
        val project = ProjectBuilder.builder().build()

        val extension = ExtensionRegistrar(project, project.providers).createSettingsExtension(registry())

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(extension).isInstanceOf(EasySettingsExtension::class.java)
            softly.assertThat(project.extensions.findByName(EasySettingsExtension.name)).isSameAs(extension)
        }
    }

    @Test
    fun `attaches settings extensions and ignores project extensions`() {
        val project = ProjectBuilder.builder().build()
        val registry =
            registry(
                settingsExtensions = setOf(SettingsChildExtension::class),
                projectExtensions = setOf(ProjectChildExtension::class),
            )

        val extension = ExtensionRegistrar(project, project.providers).createSettingsExtension(registry)

        assertSoftly { softly ->
            softly
                .assertThat(extension.extensions.findByName(SettingsChildExtension.name))
                .isInstanceOf(SettingsChildExtension::class.java)
            softly.assertThat(extension.extensions.findByName(ProjectChildExtension.name)).isNull()
        }
    }

    @Test
    fun `unstubbed registry contributes no settings children`() {
        val project = ProjectBuilder.builder().build()
        val registry = mock(PluginRegistry::class.java)

        val extension = ExtensionRegistrar(project, project.providers).createSettingsExtension(registry)

        assertSoftly { softly ->
            // Mockito's default answer returns an empty set for the unstubbed collection getter
            softly.assertThat(registry.getSettingsExtensions()).isEmpty()
            softly.assertThat(extension.extensions.findByName(SettingsChildExtension.name)).isNull()
        }
    }
}
