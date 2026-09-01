package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Tests for [CanBeEnabled.isEnabled] defaulting and value propagation.
 *
 * Verifies the `Property<Boolean>` `enabled` defaults to `true` via `getOrElse(true)`
 * and reflects explicit `set` calls — the predicate used by `AbstractEasy*Plugin` to decide
 * `afterEnabled`.
 */
class CanBeEnabledTest {
    @Test
    fun `isEnabled defaults to true when not set`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(ext.isEnabled()).isTrue()
        }
    }

    @Test
    fun `isEnabled reflects set value`() {
        val holder = ProjectBuilder.builder().build()
        val easy = createEasy(holder, TestEnabledExtension::class)
        val ext = easy.extensions.getByType(TestEnabledExtension::class.java)

        ext.enabled.set(false)
        assertSoftly { softly ->
            softly.assertThat(ext.isEnabled()).isFalse()
        }
        ext.enabled.set(true)
        assertSoftly { softly ->
            softly.assertThat(ext.isEnabled()).isTrue()
        }
    }
}
