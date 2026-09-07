package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class PluginRegistryServiceTest {
    @Test
    fun `repeat ServiceLoader loads are no-ops`() {
        val project = ProjectBuilder.builder().build()
        val service = project.getPluginRegistry()

        service.loadFromServiceLoader(javaClass.classLoader)
        val plugins = service.getProjectPlugins()
        val settingsPlugins = service.getSettingsPlugins()
        val extensions = service.getRegisteredExtensions()

        service.loadFromServiceLoader(javaClass.classLoader)

        assertSoftly { softly ->
            softly.assertThat(service.getProjectPlugins()).isEqualTo(plugins)
            softly.assertThat(service.getSettingsPlugins()).isEqualTo(settingsPlugins)
            softly.assertThat(service.getRegisteredExtensions()).isEqualTo(extensions)
        }
    }
}
