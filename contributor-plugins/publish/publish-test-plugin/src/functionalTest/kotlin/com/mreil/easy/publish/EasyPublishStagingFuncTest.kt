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
 * Smoke test for the local Maven staging repository (`mavenStaging`).
 *
 * Verifies that `toMavenStaging()` actually publishes to `build/stagingRepo`.
 * Root-only registration, URL resolution, and immutability are covered by fast unit tests
 * in `publish-plugin`.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyPublishStagingFuncTest {
    lateinit var project: GradleTestProject

    /** Smoke: `toMavenStaging()` publishes to the default `build/stagingRepo` directory. */
    @Test
    fun `toMavenStaging publishes to build stagingRepo`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result = project.build("publish", "--info")

        val stagingRepo = project.file("build/stagingRepo")
        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("mavenStaging")
            softly.assertThat(stagingRepo).exists()
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates)
        }
    }

    /**
     * JReleaser deploys every file found under the staging dir, so stale artifacts from a previous
     * version must be wiped before re-staging. `cleanStagingRepo` runs before the upload tasks and
     * deletes the whole dir, keeping the staged tree equal to the current version only.
     */
    @Test
    fun `re-staging wipes stale artifact files from previous versions`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        project.build("publish")
        val stagingRepo = project.file("build/stagingRepo")

        // Simulate a leftover from a previous version that the upload itself would not overwrite.
        val staleArtifact =
            project.mavenArtifact(stagingRepo, coordinates.copy(version = "0.9.9"))
        staleArtifact.parentFile.mkdirs()
        staleArtifact.writeText("stale leftover from 0.9.9")

        project.build("publish")

        assertSoftly { softly ->
            softly.assertThat(staleArtifact).doesNotExist()
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates)
        }
    }
}
