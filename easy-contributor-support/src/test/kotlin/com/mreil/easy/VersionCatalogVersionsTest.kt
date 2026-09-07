package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class VersionCatalogVersionsTest {
    @Test
    fun `missing catalog falls back to default`() {
        val project = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(project.catalogVersionOrDefault("jreleaser", "1.25.0")).isEqualTo("1.25.0")
        }
    }

    @Test
    fun `missing alias falls back to default`() {
        val project = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(project.catalogVersionOrDefault("no-such-alias", "9.9.9")).isEqualTo("9.9.9")
            softly.assertThat(project.catalogVersionOrDefault("no-such-alias", "9.9.9", "no-such-catalog")).isEqualTo("9.9.9")
        }
    }
}
