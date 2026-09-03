package com.mreil.easy

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.assertSoftly
import com.mreil.easy.test.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
class SettingsPluginFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `settings plugin creates easy extension on settings and copies to root project`() {
        val extensionProbe =
            probeTask("verifyExtension") {
                prelude(
                    "val ext = project.extensions.findByName(\"easy\") as? EasyExtension",
                    "val dummy = ext?.extensions?.findByName(\"dummy\") as? DummyExtension",
                )
                expect("HAS_ROOT_EXTENSION", "ext != null", "true")
                expect("DUMMY_MESSAGE", "dummy?.message?.get()", "fromSettings")
            }
        project.configure {
            settings(
                """
                plugins {
                    id("com.mreil.easy.settings")
                }
                extensions.configure<com.mreil.easy.EasyExtension>("easy") {
                    extensions.configure<com.mreil.easy.fixtures.DummyExtension>("dummy") {
                        message.set("fromSettings")
                    }
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                import com.mreil.easy.EasyExtension
                import com.mreil.easy.fixtures.DummyExtension

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
