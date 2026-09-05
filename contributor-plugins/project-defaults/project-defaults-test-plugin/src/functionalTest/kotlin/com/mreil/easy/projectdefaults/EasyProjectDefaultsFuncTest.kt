package com.mreil.easy.projectdefaults

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Functional tests for project convention checks and the `base` plugin.
 *
 * Verifies fail-fast `group`/`version` validation and lifecycle task availability
 * when `projectDefaults` is enabled. Wiring details are covered by unit tests
 * in `project-defaults-plugin`.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyProjectDefaultsFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `fails when group is missing`() {
        project.configure {
            // no group staged - defaults to unspecified
            group = null
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.projectdefaults")
                }
                easy {
                    projectDefaults { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.buildAndFail("help", "--info")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Project group must be set")
        }
    }

    @Test
    fun `fails when version is missing`() {
        project.configure {
            // no version staged - defaults to unspecified
            version = null
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.projectdefaults")
                }
                easy {
                    projectDefaults { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.buildAndFail("help", "--info")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Project version must be set")
            softly.assertThat(result.output).contains("searched: ")
            softly.assertThat(result.output).contains("gradle.properties")
        }
    }

    @Test
    fun `base lifecycle tasks are available when enabled`() {
        val baseProbe =
            probeTask("verifyBase") {
                taskExists("HAS_CLEAN", "clean")
                taskExists("HAS_BUILD", "build")
                taskExists("HAS_CHECK", "check")
            }
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.projectdefaults")
                }
                easy {
                    projectDefaults { enabled.set(true) }
                }
                ${baseProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyBase")

        assertSoftly { softly ->
            baseProbe.assertOutput(softly, result.output)
        }
    }
}
