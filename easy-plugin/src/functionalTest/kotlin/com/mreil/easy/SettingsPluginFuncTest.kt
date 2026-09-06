package com.mreil.easy

import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
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
                    extensions.configure<com.mreil.easy.codemeta.EasyCodemetaExtension>("codemeta") {
                        enabled.set(false)
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

    @Test
    fun `settings plugin activates project plugins when extension enabled`() {
        val probe =
            probeTask("verifyProjectPlugin") {
                taskExists("HAS_GENERATE_CODEMETA", "generateCodemeta")
                expect(
                    "HAS_CODEMETA_EXT",
                    "(project.extensions.findByName(\"easy\") as? org.gradle.api.plugins.ExtensionAware)?.extensions?.findByName(\"codemeta\") != null",
                    "true",
                )
            }
        project.configure {
            // pre-create codemeta.json so generateCodemeta does not fail the build
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
            settings(
                """
                plugins {
                    id("com.mreil.easy.settings")
                }
                easy {
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    `java-library`
                }
                ${probe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyProjectPlugin")

        assertSoftly { softly ->
            probe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `settings plugin copies easy extension to subprojects and activates project plugins`() {
        val probe =
            probeTask("verifySubproject") {
                prelude(
                    "val child = project.findProject(\":child\")!!",
                    "val ext = child.extensions.findByName(\"easy\") as? EasyExtension",
                    "val dummy = ext?.extensions?.findByName(\"dummy\") as? DummyExtension",
                    "val publish = ext?.extensions?.findByName(\"publish\")",
                )
                expect("CHILD_HAS_ROOT_EXTENSION", "ext != null", "true")
                expect("DUMMY_MESSAGE", "dummy?.message?.get()", "fromSettings")
                expect("CHILD_HAS_PUBLISH_EXT", "publish != null", "true")
                taskExists("CHILD_HAS_PUBLISH_TASK", "publish", inProject = ":child")
            }
        project.configure {
            // pre-create codemeta.json so generateCodemeta (auto-wired into every task) does not fail the build
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
            settings(
                """
                plugins {
                    id("com.mreil.easy.settings")
                }
                easy {
                    dummy {
                        message.set("fromSettings")
                    }
                    publish {
                        enabled.set(true)
                        toMavenLocal()
                    }
                }
                include(":child")
                """.trimIndent(),
            )
            buildGradle(
                """
                import com.mreil.easy.EasyExtension
                import com.mreil.easy.fixtures.DummyExtension

                ${probe.script()}
                """.trimIndent(),
            )
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    """.trimIndent(),
                )
            }
        }

        val result = project.build("verifySubproject")

        assertSoftly { softly ->
            probe.assertOutput(softly, result.output)
        }
    }
}
