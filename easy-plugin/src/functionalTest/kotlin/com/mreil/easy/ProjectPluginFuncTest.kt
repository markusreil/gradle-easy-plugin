package com.mreil.easy

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class ProjectPluginFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `project plugin creates easy extension on project`() {
        val extensionProbe =
            probeTask("verifyExtension") {
                extensionExists("HAS_EXTENSION", "easy")
                expect("IS_EASY_EXTENSION", "project.extensions.findByName(\"easy\") is EasyExtension", "true")
            }
        project.configure {
            buildGradle(
                """
                import com.mreil.easy.EasyExtension

                plugins {
                    id("com.mreil.easy.project")
                }

                easy {
                    publish { enabled.set(true) }
                }

                ${extensionProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyExtension")

        assertSoftly { softly ->
            extensionProbe.assertOutput(softly, result.output)
        }
    }
}
