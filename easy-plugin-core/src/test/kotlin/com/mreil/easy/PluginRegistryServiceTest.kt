package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class PluginRegistryServiceTest {
    private interface DummySettingsExtension : EasyPluginExtension

    private interface OtherDummySettingsExtension : EasyPluginExtension

    @Test
    fun `repeat ServiceLoader loads are no-ops`() {
        val project = ProjectBuilder.builder().build()
        val service = project.getPluginRegistry()

        service.loadFromServiceLoader(javaClass.classLoader)
        val plugins = service.getProjectPlugins()
        val settingsPlugins = service.getSettingsPlugins()
        val extensions = service.getRegisteredExtensions()
        val settingsExtensions = service.getSettingsExtensions()

        service.loadFromServiceLoader(javaClass.classLoader)

        assertSoftly { softly ->
            softly.assertThat(service.getProjectPlugins()).isEqualTo(plugins)
            softly.assertThat(service.getSettingsPlugins()).isEqualTo(settingsPlugins)
            softly.assertThat(service.getRegisteredExtensions()).isEqualTo(extensions)
            softly.assertThat(service.getSettingsExtensions()).isEqualTo(settingsExtensions)
        }
    }

    @Test
    fun `registerSettingsExtension and getSettingsExtensions via shared service`() {
        val project = ProjectBuilder.builder().build()
        val service = project.getPluginRegistry()

        assertSoftly { softly ->
            service.registerSettingsExtension(DummySettingsExtension::class)
            softly.assertThat(service.getSettingsExtensions()).contains(DummySettingsExtension::class)
            // settings-scope registrations stay independent of the project-scope set
            softly.assertThat(service.getRegisteredExtensions()).doesNotContain(DummySettingsExtension::class)
            service.registerSettingsExtension(OtherDummySettingsExtension::class)
            softly
                .assertThat(service.getSettingsExtensions())
                .contains(DummySettingsExtension::class, OtherDummySettingsExtension::class)
        }
    }

    @Test
    fun `registerSettingsExtension deduplicates same KClass`() {
        val project = ProjectBuilder.builder().build()
        val service = project.getPluginRegistry()

        service.registerSettingsExtension(DummySettingsExtension::class)
        service.registerSettingsExtension(DummySettingsExtension::class)

        assertSoftly { softly -> softly.assertThat(service.getSettingsExtensions()).contains(DummySettingsExtension::class) }
    }

    @Test
    fun `getSettingsExtensions returns defensive copy`() {
        val project = ProjectBuilder.builder().build()
        val service = project.getPluginRegistry()
        service.registerSettingsExtension(DummySettingsExtension::class)

        val first = service.getSettingsExtensions()

        assertSoftly { softly ->
            softly.assertThat(first).contains(DummySettingsExtension::class)
            softly.assertThat(service.getSettingsExtensions()).contains(DummySettingsExtension::class)
        }
    }
}
