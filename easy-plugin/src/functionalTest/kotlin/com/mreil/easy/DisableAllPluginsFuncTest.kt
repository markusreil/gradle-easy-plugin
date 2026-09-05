package com.mreil.easy

import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.SetSystemProperty

@ExtendWith(GradleTestProjectExtension::class)
class DisableAllPluginsFuncTest {
    lateinit var project: GradleTestProject

    @Test
    @SetSystemProperty(key = "easy.disableAllPlugins", value = "true")
    fun `disableAllPlugins disables extensions by default`() {
        val disabledProbe =
            probeTask("verifyDisabled") {
                prelude(
                    "val easy = project.extensions.getByName(\"easy\") as com.mreil.easy.EasyExtension",
                    "val publish = easy.extensions.findByName(\"publish\") as com.mreil.easy.CanBeEnabled",
                )
                expect("PUBLISH_ENABLED", "publish.enabled.get()", "false")
            }
        project.configure {
            systemProperty("easy.disableAllPlugins", "true")
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.project")
                }
                ${disabledProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyDisabled")

        assertSoftly { softly ->
            disabledProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    @SetSystemProperty(key = "easy.disableAllPlugins", value = "true")
    fun `explicit enable overrides disableAllPlugins`() {
        val enabledProbe =
            probeTask("verifyEnabled") {
                prelude(
                    "val easy = project.extensions.getByName(\"easy\") as com.mreil.easy.EasyExtension",
                    "val publish = easy.extensions.findByName(\"publish\") as com.mreil.easy.CanBeEnabled",
                )
                expect("PUBLISH_ENABLED", "publish.enabled.get()", "true")
            }
        project.configure {
            systemProperty("easy.disableAllPlugins", "true")
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.project")
                }
                easy {
                    publish {
                        enabled.set(true)
                    }
                }
                ${enabledProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyEnabled")

        assertSoftly { softly ->
            enabledProbe.assertOutput(softly, result.output)
        }
    }
}
