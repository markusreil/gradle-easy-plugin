package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Action
import org.gradle.api.initialization.Settings
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Tests for [AbstractEasySettingsPlugin] lifecycle `init` vs `afterEnabled`.
 *
 * Mirrors `AbstractEasyProjectPluginTest` for `Settings` scope, capturing
 * `Gradle.settingsEvaluated` via `Proxy` and verifying the throwing-`getGradle`
 * fallback to immediate `isEnabled` checks.
 */
class AbstractEasySettingsPluginTest {
    @Test
    fun `without EnabledBy calls afterEnabled eagerly`() {
        val holder = ProjectBuilder.builder().build()
        val settings = newSettingsProxy(holder, mutableListOf())

        val plugin = NoAnnotationSettingsPlugin()
        plugin.apply(settings)

        assertSoftly { softly ->
            softly.assertThat(plugin.initCalled).isTrue()
            softly.assertThat(plugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `with EnabledBy defers until settingsEvaluated when enabled`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)
        ext.enabled.set(true)

        val captured = mutableListOf<Action<Settings>>()
        val settings = newSettingsProxy(holder, captured)

        val plugin = EnabledSettingsPlugin()
        plugin.apply(settings)

        assertSoftly { softly ->
            softly.assertThat(plugin.initCalled).isTrue()
            softly.assertThat(plugin.afterEnabledCalled).isFalse()
        }

        captured.single().execute(settings)

        assertSoftly { softly ->
            softly.assertThat(plugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `with EnabledBy does not call afterEnabled when disabled`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)
        ext.enabled.set(false)

        val captured = mutableListOf<Action<Settings>>()
        val settings = newSettingsProxy(holder, captured)

        val plugin = EnabledSettingsPlugin()
        plugin.apply(settings)
        captured.single().execute(settings)

        assertSoftly { softly ->
            softly.assertThat(plugin.afterEnabledCalled).isFalse()
        }
    }

    @Test
    fun `falls back to immediate check when settingsEvaluated throws and enabled`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)
        ext.enabled.set(true)

        val settings = newThrowingSettingsProxy(holder)

        val plugin = EnabledSettingsPlugin()
        plugin.apply(settings)

        assertSoftly { softly ->
            softly.assertThat(plugin.initCalled).isTrue()
            softly.assertThat(plugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `fallback does not call afterEnabled when disabled`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)
        ext.enabled.set(false)

        val settings = newThrowingSettingsProxy(holder)

        val plugin = EnabledSettingsPlugin()
        plugin.apply(settings)

        assertSoftly { softly ->
            softly.assertThat(plugin.afterEnabledCalled).isFalse()
        }
    }
}
