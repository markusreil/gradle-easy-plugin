package com.mreil.easy.vcs

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Functional tests for the `vcs` contributor.
 *
 * Verifies the `vcsStatus` task runs successfully both when the project is not
 * under version control and when it is a git working tree.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class VcsFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `vcsStatus runs on a non-git directory`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.vcs")
                }
                easy {
                    vcs { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("vcsStatus")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("> Task :vcsStatus")
        }
    }

    @Test
    fun `vcsStatus runs on a git-initialised directory`() {
        gitInit(project)

        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.vcs")
                }
                easy {
                    vcs { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("vcsStatus")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("> Task :vcsStatus")
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
