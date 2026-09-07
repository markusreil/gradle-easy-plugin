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
}
