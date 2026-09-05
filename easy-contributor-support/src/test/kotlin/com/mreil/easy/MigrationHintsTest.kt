package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetSystemProperty

class MigrationHintsTest {
    @Test
    fun `isEnabled defaults to true`() {
        val project = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(MigrationHints.isEnabled(project)).isTrue()
        }
    }

    @Test
    @SetSystemProperty(key = "easy.migrationHintsEnabled", value = "false")
    fun `isEnabled respects opt-out property`() {
        val project = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(MigrationHints.isEnabled(project)).isFalse()
        }
    }

    @Test
    @SetSystemProperty(key = "easy.migrationHintsEnabled", value = "true")
    fun `isEnabled respects explicit opt-in`() {
        val project = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(MigrationHints.isEnabled(project)).isTrue()
        }
    }

    @Test
    fun `notifyRedundantConfig logs without throwing`() {
        val project = ProjectBuilder.builder().build()

        project.notifyRedundantConfig("sourcesJar", "withSourcesJar()")
    }

    @Test
    @SetSystemProperty(key = "easy.migrationHintsEnabled", value = "false")
    fun `notifyRedundantConfig is silent when disabled`() {
        val project = ProjectBuilder.builder().build()

        project.notifyRedundantConfig("sourcesJar", "withSourcesJar()")
    }
}
