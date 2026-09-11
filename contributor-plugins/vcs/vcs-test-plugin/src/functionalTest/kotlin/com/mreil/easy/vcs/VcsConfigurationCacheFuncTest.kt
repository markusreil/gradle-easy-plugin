package com.mreil.easy.vcs

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Configuration-cache compatibility test for the `vcs` contributor.
 *
 * Runs the build with `--configuration-cache` twice and asserts the entry is stored on the
 * first run and reused on the second, with no configuration-cache problems. `codemeta` is
 * enabled so the `generateCodemeta` task input realizes the lazy git-backed `codeRepository`
 * provider at configuration (cache store) time; any external-process-at-configuration regression
 * (e.g. raw `ProcessBuilder` git calls) fails this test.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class VcsConfigurationCacheFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `configuration cache stores and reuses without external process problems`() {
        gitInit(project)

        project.configure {
            // Pre-create codemeta.json so generateCodemeta is skipped, keeping the build green
            // while still realizing its `codeRepository` input at configuration-cache store time.
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "test",
                  "description": "test",
                  "version": "1.0.0"
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.vcs")
                }
                easy {
                    vcs { enabled.set(true) }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val first = project.build("--configuration-cache", "vcsStatus")
        val second = project.build("--configuration-cache", "vcsStatus")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(first.output).doesNotContain("external process")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
            softly.assertThat(second.output).doesNotContain("external process")
        }
    }

    private fun gitInit(project: GradleTestProject) {
        val process =
            ProcessBuilder("git", "init")
                .directory(project.projectDir)
                .redirectErrorStream(true)
                .start()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "git init failed with exit code $exitCode" }
    }
}
