package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

/**
 * Functional tests for publish routing (snapshot vs. release vs. neutral repositories).
 *
 * Covers version-based repository filtering (via semver). `toMavenLocal` wiring is covered by
 * unit tests in `publish-plugin`.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyPublishRoutingFuncTest {
    lateinit var project: GradleTestProject

    /** SNAPSHOT version is routed only to snapshot and neutral repositories. */
    @Test
    fun `snapshot version publishes only to snapshot and neutral repos`() {
        lateinit var releaseDir: File
        lateinit var snapshotDir: File
        lateinit var neutralDir: File
        project.configure {
            version = "1.0.0-SNAPSHOT"
            releaseDir = createDir("repoRelease")
            snapshotDir = createDir("repoSnapshot")
            neutralDir = createDir("repoNeutral")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    semver { enabled.set(true) }
                    publish {
                        enabled.set(true)
                        mavenRepo("myRelease", "${releaseDir.invariantSeparatorsPath}")
                        mavenRepo("mySnapshot", "${snapshotDir.invariantSeparatorsPath}")
                        mavenRepo("myNeutral", "${neutralDir.invariantSeparatorsPath}")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = project.projectDir.name, version = "1.0.0-SNAPSHOT")
        assertSoftly { softly ->
            softly.assertThat(project).doesNotHaveMavenMetadata(releaseDir, coordinates)
            softly.assertThat(project).hasMavenMetadata(snapshotDir, coordinates)
            softly.assertThat(project).hasMavenMetadata(neutralDir, coordinates)
            softly.assertThat(project).hasArtifact(snapshotDir, coordinates)
        }
    }

    /** Release version is routed only to release and neutral repositories. */
    @Test
    fun `release version publishes only to release and neutral repos`() {
        lateinit var releaseDir: File
        lateinit var snapshotDir: File
        lateinit var neutralDir: File
        project.configure {
            releaseDir = createDir("repoRelease")
            snapshotDir = createDir("repoSnapshot")
            neutralDir = createDir("repoNeutral")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    semver { enabled.set(true) }
                    publish {
                        enabled.set(true)
                        mavenRepo("myRelease", "${releaseDir.invariantSeparatorsPath}")
                        mavenRepo("mySnapshot", "${snapshotDir.invariantSeparatorsPath}")
                        mavenRepo("myNeutral", "${neutralDir.invariantSeparatorsPath}")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(releaseDir, coordinates)
            softly.assertThat(project).doesNotHaveArtifact(snapshotDir, coordinates)
            softly.assertThat(project).hasArtifact(neutralDir, coordinates)
        }
    }

    /** Without semver, publishes to all repos (snapshot routing disabled). */
    @Test
    fun `publishes to all repos when semver is disabled`() {
        lateinit var releaseDir: File
        lateinit var snapshotDir: File
        lateinit var neutralDir: File
        project.configure {
            releaseDir = createDir("repoRelease")
            snapshotDir = createDir("repoSnapshot")
            neutralDir = createDir("repoNeutral")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        mavenRepo("myRelease", "${releaseDir.invariantSeparatorsPath}")
                        mavenRepo("mySnapshot", "${snapshotDir.invariantSeparatorsPath}")
                        mavenRepo("myNeutral", "${neutralDir.invariantSeparatorsPath}")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(releaseDir, coordinates)
            softly.assertThat(project).hasArtifact(snapshotDir, coordinates)
            softly.assertThat(project).hasArtifact(neutralDir, coordinates)
        }
    }
}
