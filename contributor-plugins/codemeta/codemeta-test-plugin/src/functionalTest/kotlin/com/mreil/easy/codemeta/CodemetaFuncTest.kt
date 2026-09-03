package com.mreil.easy.codemeta

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.assertSoftly
import com.mreil.easy.test.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
class CodemetaFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `generateCodemeta creates file and fails build when missing`() {
        project.configure {
            // no codemeta.json - should be auto-created and build fails
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.codemeta")
                }
                easy {
                    codemeta {}
                }
                tasks.register("verifyCodemeta") {
                    doLast {
                        println("SHOULD_NOT_RUN")
                    }
                }
                """.trimIndent(),
            )
        }

        val firstResult =
            project.buildAndFail("verifyCodemeta")

        val generated = project.file("codemeta.json")
        assertSoftly { softly ->
            softly.assertThat(firstResult.output).contains("codemeta.json was not found")
            softly.assertThat(firstResult.output).contains("created initial file")
            softly.assertThat(generated).exists()
            softly.assertThat(generated.readText()).contains("TODO: Add description")
            softly.assertThat(generated.readText()).contains("\"@context\"")
        }

        // second run should succeed now that file exists
        val secondResult =
            project.build("verifyCodemeta")

        assertSoftly { softly ->
            softly.assertThat(secondResult.output).contains("SHOULD_NOT_RUN").`as`("verifyCodemeta should run after file created")
        }
    }

    @Test
    fun `codemeta extension is available when enabled`() {
        val codemetaProbe =
            probeTask("verifyCodemeta") {
                prelude(
                    "val easy = project.extensions.findByType(com.mreil.easy.EasyExtension::class.java) " +
                        "as? org.gradle.api.plugins.ExtensionAware",
                )
                extensionExists("HAS_EASY", "easy")
                expect(
                    "HAS_CODEMETA",
                    "easy?.extensions?.findByName(\"codemeta\") != null",
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
            buildGradle(
                """
                    plugins {
                        `java-library`
                        id("com.mreil.easy.test.codemeta")
                    }
                easy {
                    codemeta {}
                }
                ${codemetaProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyCodemeta")

        assertSoftly { softly ->
            codemetaProbe.assertOutput(softly, result.output)
        }
    }
}
