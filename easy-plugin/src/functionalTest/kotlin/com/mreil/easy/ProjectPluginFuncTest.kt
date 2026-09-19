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

    private fun GradleTestProject.stageCodemetaJson() {
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
    }

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

    @Test
    fun `project plugin activates project plugins when extension enabled`() {
        val probe =
            probeTask("verifyProjectPlugin") {
                prelude(
                    "val easy = project.extensions.findByName(\"easy\") as? org.gradle.api.plugins.ExtensionAware",
                    "val codemeta = easy?.extensions?.findByName(\"codemeta\")",
                )
                taskExists("HAS_GENERATE_CODEMETA", "generateCodemeta")
                expect("HAS_CODEMETA_EXT", "codemeta != null", "true")
            }
        project.configure {
            stageCodemetaJson()
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.project")
                    `java-library`
                }

                easy {
                    codemeta { enabled.set(true) }
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
    fun `project plugin copies easy extension to subprojects and activates project plugins`() {
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
            settings("include(\":child\")")
            buildGradle(
                """
                import com.mreil.easy.EasyExtension
                import com.mreil.easy.fixtures.DummyExtension

                plugins {
                    id("com.mreil.easy.project")
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
            // ensureDefaultPublication must run exactly once per project: a second run would find
            // the self-created 'maven' publication and log this spurious warning on every project.
            softly.assertThat(result.output).doesNotContain("already exists")
        }
    }
}
