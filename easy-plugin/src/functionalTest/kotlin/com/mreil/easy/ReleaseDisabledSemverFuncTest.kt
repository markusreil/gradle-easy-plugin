package com.mreil.easy

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Regression guard for the two-jar-split risk where a disabled `release` contributor still
 * registered its services in `init()` and realized `EasySemver` while the configuration cache was
 * stored. With `semver` enabled and an invalid project version, that realization must not happen
 * when `release` is disabled.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class ReleaseDisabledSemverFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `disabled release does not realize semver under the configuration cache`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.project")
                }

                version = "not-semver"

                easy {
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val first = project.build("--configuration-cache", "help")
        val second = project.build("--configuration-cache", "help")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
        }
    }
}
