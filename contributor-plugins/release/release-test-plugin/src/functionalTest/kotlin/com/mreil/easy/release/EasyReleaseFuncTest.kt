package com.mreil.easy.release

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyReleaseFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `release plugin applies without error`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                easy {
                    release { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("help")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
        }
    }
}
