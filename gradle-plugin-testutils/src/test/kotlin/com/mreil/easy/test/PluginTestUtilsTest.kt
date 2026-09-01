package com.mreil.easy.test

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PluginTestUtilsTest {
    @Test
    fun `loads plugin settings property successfully`() {
        val settingsId = loadGradleProperty("plugin.settings")
        assertThat(settingsId).isNotEmpty()
        assertThat(settingsId).isEqualTo(loadSettingsPluginId())
    }
}
