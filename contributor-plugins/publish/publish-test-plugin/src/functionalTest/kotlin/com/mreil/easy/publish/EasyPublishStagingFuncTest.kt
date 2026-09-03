package com.mreil.easy.publish

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.MavenCoordinates
import com.mreil.easy.test.project.assertj.assertSoftly
import com.mreil.easy.test.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
class EasyPublishStagingFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `staging repo is only added to root in multi-module build`() {
        val stagingReposProbe =
            probeTask("verifyStagingRepos") {
                prelude(
                    "val rootRepos = project.extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java)" +
                        ".repositories.map { it.name }",
                    "val child = project.findProject(\":child\")!!",
                    "val childRepos = child.extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java)" +
                        ".repositories.map { it.name }",
                )
                expect("ROOT_REPOS", "rootRepos.joinToString(\",\")", "mavenStaging")
                expectAbsent("CHILD_REPOS", "childRepos.joinToString(\",\")", "mavenStaging")
                expect("CHILD_HAS_STAGING", "childRepos.contains(\"mavenStaging\")", "false")
            }
        project.configure {
            settings(
                """
                rootProject.name = "root"
                include(":child")
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
                        toMavenStaging()
                    }
                }
                ${stagingReposProbe.script()}
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
                javaSource("com.example", "Child")
            }
            javaSource("com.example", "Root")
        }

        val result =
            project.build("verifyStagingRepos", "--info")

        assertSoftly { softly ->
            stagingReposProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `staging repo resolves to root build directory not subproject`() {
        project.configure {
            settings(
                """
                rootProject.name = "root"
                include(":child")
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
                        toMavenStaging("customStaging")
                    }
                }
                tasks.register("verifyStagingUrl") {
                    doLast {
                        val publishing = project.extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java)
                        val repo = publishing.repositories.findByName("mavenStaging") as? org.gradle.api.artifacts.repositories.MavenArtifactRepository
                        println("STAGING_URL=" + repo?.url)
                        println("ROOT_BUILD=" + project.layout.buildDirectory.get().asFile.invariantSeparatorsPath)
                        println("CHILD_BUILD=" + project.findProject(":child")!!.layout.buildDirectory.get().asFile.invariantSeparatorsPath)
                    }
                }
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

        val result =
            project.build("verifyStagingUrl", "--info")

        val rootBuild = project.file("build").invariantSeparatorsPath
        val childBuild = project.file("child/build").invariantSeparatorsPath
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("STAGING_URL=")
            softly.assertThat(result.output).contains(rootBuild)
            softly.assertThat(result.output).contains("customStaging")
            // staging should not point to child build
            softly.assertThat(result.output).doesNotContain("CHILD_BUILD=$rootBuild/customStaging")
            softly.assertThat(childBuild).isNotEqualTo(rootBuild)
            // ensure staging url is under root, not child
            val stagingLine = result.output.lines().firstOrNull { it.contains("STAGING_URL=") } ?: ""
            softly.assertThat(stagingLine).contains(rootBuild)
            softly.assertThat(stagingLine).doesNotContain(childBuild)
        }
    }

    @Test
    fun `child cannot override stagingPath after root defines it`() {
        project.configure {
            settings(
                """
                rootProject.name = "root"
                include(":child")
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
                        toMavenStaging("rootStaging")
                    }
                }
                """.trimIndent(),
            )
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    easy {
                        publish {
                            enabled.set(true)
                            toMavenStaging("childStaging")
                        }
                    }
                    """.trimIndent(),
                )
            }
        }

        val result =
            project.buildAndFail("help", "--info")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("stagingPath")
            // disallowChanges produces Cannot mutate or similar
            softly.assertThat(result.output.lowercase()).contains("cannot")
        }
    }

    @Test
    fun `publish to global staging writes to root build directory`() {
        project.configure {
            settings(
                """
                rootProject.name = "root"
                include(":child")
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
                        toMavenStaging()
                    }
                }
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
                javaSource("com.example", "Child")
            }
            javaSource("com.example", "Root")
        }

        val result =
            project.build("publish", "--info")

        val rootStaging = project.file("build/stagingRepo")
        val childStaging = project.file("child/build/stagingRepo")
        val coordinates = MavenCoordinates(name = "root")
        // child has no staging repo, so its artifact should NOT be in global staging (only root's)
        // but current @ApplyToSubprojects still publishes child via its own publishing? child has no mavenStaging, so not published
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("mavenStaging")
            softly.assertThat(rootStaging).exists()
            softly.assertThat(project).hasArtifact(rootStaging, coordinates)
            softly.assertThat(childStaging).doesNotExist()
        }
    }

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
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("publish", "--info")

        val stagingRepo = project.file("build/stagingRepo")
        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("mavenStaging")
            softly.assertThat(stagingRepo).exists()
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates)
        }
    }

    @Test
    fun `toMavenStaging with custom path publishes to custom build directory`() {
        val customPath = "customStaging"
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
                        toMavenStaging("$customPath")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("publish", "--info")

        val stagingRepo = project.file("build/$customPath")
        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("mavenStaging")
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates)
        }
    }
}
