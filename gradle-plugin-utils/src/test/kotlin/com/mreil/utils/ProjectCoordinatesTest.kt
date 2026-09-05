package com.mreil.utils

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProjectCoordinatesTest {
    @ParameterizedTest(name = "value={0} expected={1}")
    @CsvSource(
        "'', false",
        "'unspecified', false",
        "'com.example', true",
        "'1.0.0', true",
    )
    fun `isSpecified detects unset values`(
        value: String?,
        expected: Boolean,
    ) {
        assertSoftly { softly ->
            softly.assertThat(value.isSpecified()).isEqualTo(expected)
        }
    }

    @Test
    fun `isSpecified is false for null`() {
        assertSoftly { softly ->
            softly.assertThat(null.isSpecified()).isFalse()
        }
    }

    @Test
    fun `hasGroup reflects project group`() {
        val project = ProjectBuilder.builder().build()
        project.group = ""

        assertSoftly { softly ->
            softly.assertThat(project.hasGroup()).isFalse()
        }

        project.group = "com.example"

        assertSoftly { softly ->
            softly.assertThat(project.hasGroup()).isTrue()
        }
    }

    @Test
    fun `hasVersion reflects project version`() {
        val project = ProjectBuilder.builder().build()
        project.version = "unspecified"

        assertSoftly { softly ->
            softly.assertThat(project.hasVersion()).isFalse()
        }

        project.version = "1.0.0"

        assertSoftly { softly ->
            softly.assertThat(project.hasVersion()).isTrue()
        }
    }
}
