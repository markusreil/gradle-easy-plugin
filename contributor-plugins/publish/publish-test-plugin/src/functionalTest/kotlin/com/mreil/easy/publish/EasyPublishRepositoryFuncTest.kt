package com.mreil.easy.publish

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.MavenCoordinates
import com.mreil.easy.test.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(GradleTestProjectExtension::class)
class EasyPublishRepositoryFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `publish publishes to custom file repository`() {
        lateinit var repoDir: File
        project.configure {
            repoDir = createDir("repo")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        mavenRepo("testRepo", "${repoDir.invariantSeparatorsPath}")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
            softly.assertThat(project).hasArtifact(repoDir, coordinates)
            softly.assertThat(project).hasPom(repoDir, coordinates).hasGroupId("com.example")
        }
    }

    @Test
    fun `mavenRepo declared in publish extension is attached to publishing repositories`() {
        lateinit var repoDir: File
        project.configure {
            repoDir = createDir("repo")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        mavenRepo("testRepo", "${repoDir.invariantSeparatorsPath}")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
            softly.assertThat(project).hasArtifact(repoDir, coordinates)
        }
    }

    @Test
    fun `passwordCredentials enables credentials resolution for a repository`() {
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
                        mavenRepo("secureRepo", "http://localhost:1/repo", true)
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.buildAndFail("publish", "--info")

        assertSoftly { softly ->
            softly
                .assertThat(result.output)
                .contains("Credentials required for this build could not be resolved")
                .contains("secureRepoUsername")
                .contains("secureRepoPassword")
        }
    }
}
