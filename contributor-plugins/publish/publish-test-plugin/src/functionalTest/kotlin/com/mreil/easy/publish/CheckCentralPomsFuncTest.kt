package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Functional tests for the `checkCentralPoms` Maven Central gate.
 *
 * The checker validates every module's generated POM before any upload task runs,
 * and the root `publish` task aggregates all subproject `publish` tasks.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class CheckCentralPomsFuncTest {
    lateinit var project: GradleTestProject

    @Test
    @Suppress("LongMethod")
    fun `publish validates all module poms and aggregates child publish`() {
        val rootName = project.projectDir.name
        project.configure {
            settings("include(\"child\")")
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "license": "https://spdx.org/licenses/MIT",
                  "codeRepository": "https://github.com/example/demo",
                  "author": [{ "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }]
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenCentral()
                        toMavenStaging()
                        // toMavenCentral() turns signing on by default; these tests don't
                        // supply GPG keys, so disable signing explicitly.
                        signingEnabled.set(false)
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
            createChild {
                // Root-only harness (documented production path): maven-publish is applied in the
                // child by the EasyPublishPlugin fan-out and wired by the root JReleaser plugin.
                // Re-applying the harness here would double-register the per-project Central tasks.
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    """.trimIndent(),
                )
                javaSource()
            }
        }

        // `publish` includes the JReleaser deploy via `publishToMavenCentral`, which needs
        // real credentials — excluded here to keep this staging/validation test hermetic.
        val result = project.build("publish", "--info", "-x", "publishToMavenCentral")

        val stagingRepo = project.file("build/stagingRepo")
        val childStagingRepo = project.file("child/build/stagingRepo")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains(":checkCentralPoms")
            softly.assertThat(result.output).contains(":child:publish")
            softly.assertThat(result.output).contains(":child:generatePomFileForMavenPublication")
            softly.assertThat(project).hasArtifact(stagingRepo, MavenCoordinates(name = rootName))
            // Each module stages into its own build dir; JReleaser deploys the collection.
            softly.assertThat(project).hasArtifact(childStagingRepo, MavenCoordinates(name = "child"))
        }
    }

    /**
     * The documented production path applies the harness root-only: `EasyJreleaserPlugin` is not
     * `@ApplyToSubprojects`, so the per-project Central tasks must still reach the child via the
     * root's live `withId("maven-publish")` callback once `EasyPublishPlugin`'s fan-out enables it.
     * Regression for the pre-fix behaviour where a root-only harness left the child without any
     * Central wiring.
     */
    @Test
    fun `central wiring reaches child when harness is applied root only`() {
        project.configure {
            settings("include(\"child\")")
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "license": "https://spdx.org/licenses/MIT",
                  "codeRepository": "https://github.com/example/demo",
                  "author": [{ "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }]
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenCentral()
                        toMavenStaging()
                        // toMavenCentral() turns signing on by default; these tests don't
                        // supply GPG keys, so disable signing explicitly.
                        signingEnabled.set(false)
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
            createChild {
                // Intentionally no harness here: `maven-publish` is applied in the child by the
                // `EasyPublishPlugin` fan-out from the root, then wired by the root JReleaser plugin.
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    """.trimIndent(),
                )
                javaSource()
            }
        }

        val result = project.build("tasks", "--all")
        assertSoftly { softly ->
            // `tasks --all` lists subproject tasks without a leading path colon.
            softly.assertThat(result.output).contains("child:checkCentralPoms")
            softly.assertThat(result.output).contains("child:stripSignatureChecksums")
        }
    }

    @Test
    fun `publish fails before upload when pom metadata is missing`() {
        val rootName = project.projectDir.name
        project.configure {
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "codeRepository": "https://github.com/example/demo"
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenCentral()
                        toMavenStaging()
                        // toMavenCentral() turns signing on by default; these tests don't
                        // supply GPG keys, so disable signing explicitly.
                        signingEnabled.set(false)
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result = project.buildAndFail("publish", "--info", "-x", "publishToMavenCentral")

        val stagingRepo = project.file("build/stagingRepo")
        val stagedPom = project.mavenArtifact(stagingRepo, MavenCoordinates(name = rootName, extension = "pom"))
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("checkCentralPoms")
            softly.assertThat(result.output).contains("no <licenses> entries")
            softly.assertThat(result.output).contains("no <developers> entries")
            softly.assertThat(stagedPom).doesNotExist()
        }
    }

    @Test
    fun `pom url falls back to codeRepository`() {
        val rootName = project.projectDir.name
        project.configure {
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "license": "https://spdx.org/licenses/MIT",
                  "codeRepository": "https://github.com/example/demo",
                  "author": [{ "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }]
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenCentral()
                        toMavenStaging()
                        // toMavenCentral() turns signing on by default; these tests don't
                        // supply GPG keys, so disable signing explicitly.
                        signingEnabled.set(false)
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        // Excluded deploy: see above — this test only asserts POM content after staging.
        project.build("publish", "--info", "-x", "publishToMavenCentral")

        val stagingRepo = project.file("build/stagingRepo")
        assertSoftly { softly ->
            softly
                .assertThat(project)
                .hasPom(stagingRepo, MavenCoordinates(name = rootName))
                .hasText("<url>https://github.com/example/demo</url>")
        }
    }
}
