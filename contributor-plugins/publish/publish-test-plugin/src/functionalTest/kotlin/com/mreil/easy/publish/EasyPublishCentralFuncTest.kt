package com.mreil.easy.publish

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.assertSoftly
import com.mreil.easy.test.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
class EasyPublishCentralFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `toMavenCentral is inherited read-only and child cannot change staging after root central`() {
        val centralProbe =
            probeTask("verifyCentral") {
                prelude(
                    "val easy = project.extensions.getByType(com.mreil.easy.EasyExtension::class.java) " +
                        "as org.gradle.api.plugins.ExtensionAware",
                    "val pub = easy.extensions.getByType(com.mreil.easy.publish.EasyPublishExtension::class.java) " +
                        "as com.mreil.easy.publish.DefaultEasyPublishExtension",
                    "val child = project.findProject(\":child\")!!",
                    "val childEasy = child.extensions.getByType(com.mreil.easy.EasyExtension::class.java) " +
                        "as org.gradle.api.plugins.ExtensionAware",
                    "val childPub = childEasy.extensions.getByType(com.mreil.easy.publish.EasyPublishExtension::class.java) " +
                        "as com.mreil.easy.publish.DefaultEasyPublishExtension",
                )
                expect("ROOT_CENTRAL", "pub.toMavenCentral.get()", "true")
                expect("ROOT_STAGING", "pub.stagingPath.get()", "rootCentralStaging")
                expect("CHILD_CENTRAL", "childPub.toMavenCentral.get()", "true")
                expect("CHILD_STAGING", "childPub.stagingPath.get()", "rootCentralStaging")
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
                        toMavenCentral()
                        toMavenStaging("rootCentralStaging")
                    }
                }
                ${centralProbe.script()}
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
            project.build("verifyCentral", "--info")

        assertSoftly { softly ->
            centralProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `generateJreleaserConfig is registered on root when toMavenCentral is set`() {
        val expectedOutput = project.file("build/jreleaser/jreleaser.yml").invariantSeparatorsPath
        val expectedStaging = project.file("build/stagingRepo").invariantSeparatorsPath
        val jreleaserProbe =
            probeTask("verifyJreleaserTask") {
                prelude(
                    "val task = tasks.findByName(\"generateJreleaserConfig\") " +
                        "as? com.mreil.easy.publish.GenerateJreleaserConfigTask",
                )
                taskExists("ROOT_HAS_TASK", "generateJreleaserConfig")
                taskExists("CHILD_HAS_TASK", "generateJreleaserConfig", expected = false, inProject = ":child")
                expect("TASK_ENABLED", "task?.enabled ?: false", "true")
                expect("OUTPUT", "task?.outputFile?.get()?.asFile?.invariantSeparatorsPath ?: \"null\"", expectedOutput)
                expect("STAGING_DIR", "task?.stagingDirectory?.get() ?: \"null\"", expectedStaging)
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
                        toMavenCentral()
                    }
                }
                ${jreleaserProbe.script()}
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
            project.build("verifyJreleaserTask", "--info")

        assertSoftly { softly ->
            jreleaserProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `generateJreleaserConfig creates yaml with signing and staging and no release`() {
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
                        toMavenCentral()
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.build("generateJreleaserConfig", "--info")

        val yaml = project.file("build/jreleaser/jreleaser.yml")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("generateJreleaserConfig")
            softly.assertThat(yaml).exists()
            val text = yaml.readText()
            softly.assertThat(text).contains("signing:")
            softly.assertThat(text).contains("active: ALWAYS")
            softly.assertThat(text).contains("armored: true")
            softly.assertThat(text).contains("gpgPublicKey:")
            softly.assertThat(text).contains("gpgPrivateKey:")
            softly.assertThat(text).contains("gpgPassphrase:")
            softly.assertThat(text).contains("dummy-gpg-public-key")
            softly.assertThat(text).contains("deploy:")
            softly.assertThat(text).contains("mavenCentral:")
            softly.assertThat(text).contains("stagingRepositories:")
            softly.assertThat(text).contains(project.file("build/stagingRepo").invariantSeparatorsPath)
            softly.assertThat(text).contains("username:")
            softly.assertThat(text).contains("password:")
            softly.assertThat(text).contains("dummy-mavencentral-username")
            softly.assertThat(text).doesNotContain("release:")
        }
    }

    @Test
    fun `generateJreleaserConfig respects custom staging path`() {
        val custom = "myCustomStaging"
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
                        toMavenCentral()
                        toMavenStaging("$custom")
                    }
                }
                """.trimIndent(),
            )
        }

        project.build("generateJreleaserConfig", "--info")

        val yaml = project.file("build/jreleaser/jreleaser.yml")
        val text = yaml.readText()
        assertSoftly { softly ->
            softly.assertThat(text).contains(project.file("build/$custom").invariantSeparatorsPath)
            softly.assertThat(text).doesNotContain("build/stagingRepo")
        }
    }

    @Test
    fun `generateJreleaserConfig is skipped when toMavenCentral not set`() {
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
        }

        val result =
            project.build("generateJreleaserConfig", "--info")

        val yaml = project.file("build/jreleaser/jreleaser.yml")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("SKIPPED")
            softly.assertThat(yaml).doesNotExist()
        }
    }

    @Test
    fun `publish does not depend on generateJreleaserConfig yet`() {
        val publishDepsProbe =
            probeTask("verifyPublishDeps") {
                prelude("val deps = tasks.named(\"publish\").get().dependsOn.map { it.toString() }")
                expect("HAS_JRELEASER_DEP", "deps.any { it.contains(\"generateJreleaserConfig\") }", "false")
            }
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
                        toMavenCentral()
                    }
                }
                ${publishDepsProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyPublishDeps", "--info")

        assertSoftly { softly ->
            publishDepsProbe.assertOutput(softly, result.output)
        }
    }
}
