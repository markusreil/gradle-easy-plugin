package com.mreil.easy.publish

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.MavenCoordinates
import com.mreil.easy.test.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(GradleTestProjectExtension::class)
class EasyPublishRoutingFuncTest {
    lateinit var project: GradleTestProject

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
                    semver {}
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
                    semver {}
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

    @Test
    fun `publish also publishes to mavenLocal when toMavenLocal is enabled`() {
        lateinit var m2Repo: File
        project.configure {
            m2Repo = createDir("m2")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenLocal()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("publish", "-Dmaven.repo.local=${m2Repo.invariantSeparatorsPath}", "--info")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publishToMavenLocal")
            softly.assertThat(project).hasArtifact(m2Repo, coordinates)
        }
    }

    @Test
    fun `publishToMavenLocal publishes artifact`() {
        lateinit var m2Repo: File
        project.configure {
            group = "com.example.local"
            m2Repo = createDir("m2")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("publishToMavenLocal", "-Dmaven.repo.local=${m2Repo.invariantSeparatorsPath}")

        val coordinates = MavenCoordinates(group = "com.example.local", name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publishToMavenLocal")
            softly.assertThat(project).hasArtifact(m2Repo, coordinates)
        }
    }
}
