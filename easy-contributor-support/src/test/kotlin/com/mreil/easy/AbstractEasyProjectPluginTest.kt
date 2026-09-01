package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Tests for [AbstractEasyProjectPlugin] lifecycle `init` vs `afterEnabled`.
 *
 * Uses `Proxy`-based `Project` fakes capturing `afterEvaluate` to assert eager `init`
 * and deferred `afterEnabled` gated by `TestEnabledExtension` (`CanBeEnabled`) via
 * `EnabledBy`, including default-enabled and non-`CanBeEnabled` error cases.
 */
class AbstractEasyProjectPluginTest {
    @Test
    fun `without EnabledBy calls afterEnabled eagerly`() {
        val project = ProjectBuilder.builder().build()
        val plugin = NoAnnotationProjectPlugin()

        plugin.apply(project)

        assertSoftly { softly ->
            softly.assertThat(plugin.initCalled).isTrue()
            softly.assertThat(plugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `with EnabledBy defers afterEnabled until afterEvaluate when enabled`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)
        ext.enabled.set(true)

        val captured = mutableListOf<Action<Project>>()
        val projectProxy = newProjectProxy(holder, captured)

        val plugin = EnabledProjectPlugin()
        plugin.apply(projectProxy)

        assertSoftly { softly ->
            softly.assertThat(plugin.initCalled).isTrue()
            softly.assertThat(plugin.afterEnabledCalled).isFalse()
        }

        captured.single().execute(projectProxy)

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

        val captured = mutableListOf<Action<Project>>()
        val projectProxy = newProjectProxy(holder, captured)

        val plugin = EnabledProjectPlugin()
        plugin.apply(projectProxy)

        captured.single().execute(projectProxy)

        assertSoftly { softly ->
            softly.assertThat(plugin.initCalled).isTrue()
            softly.assertThat(plugin.afterEnabledCalled).isFalse()
        }
    }

    @Test
    fun `with EnabledBy defaults to enabled when not set`() {
        val holder = ProjectBuilder.builder().build()
        createEasy(holder, TestEnabledExtension::class)

        val captured = mutableListOf<Action<Project>>()
        val projectProxy = newProjectProxy(holder, captured)

        val plugin = EnabledProjectPlugin()
        plugin.apply(projectProxy)
        captured.single().execute(projectProxy)

        assertSoftly { softly ->
            softly.assertThat(plugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `with non-CanBeEnabled extension throws`() {
        val holder = ProjectBuilder.builder().build()
        createEasy(holder, OtherExtension::class)

        val captured = mutableListOf<Action<Project>>()
        val projectProxy = newProjectProxy(holder, captured)

        val plugin = BadProjectPlugin()
        plugin.apply(projectProxy)

        val action = captured.single()
        var thrown: Throwable? = null
        try {
            action.execute(projectProxy)
        } catch (e: Throwable) {
            thrown = e
        }

        assertSoftly { softly ->
            softly.assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
            softly.assertThat(thrown?.message).contains("must implement CanBeEnabled")
        }
    }
}
