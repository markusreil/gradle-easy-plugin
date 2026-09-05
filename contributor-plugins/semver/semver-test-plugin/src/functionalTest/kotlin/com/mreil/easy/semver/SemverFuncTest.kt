package com.mreil.easy.semver

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
class SemverFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `easySemver of returns lazy semver`() {
        val semverProbe =
            probeTask("verifySemver") {
                prelude("val semver = com.mreil.easy.semver.EasySemver.of(project).get()")
                expect("MAJOR", "semver.major", "1")
                expect("MINOR", "semver.minor", "2")
                expect("PATCH", "semver.patch", "3")
                expect("VERSION", "semver.version", "1.2.3")
            }
        project.configure {
            version = "1.2.3"
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver { enabled.set(true) }
                }
                ${semverProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifySemver")

        assertSoftly { softly ->
            semverProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `easySemver of fails on unspecified version`() {
        val semverProbe =
            probeTask("verifySemver") {
                prelude(
                    "try { com.mreil.easy.semver.EasySemver.of(project).get(); println(\"UNEXPECTED_SUCCESS\") } catch (e: Exception) { println(\"ERROR=\" + (e.message ?: \"null\")) }",
                )
            }
        project.configure {
            // no version staged - defaults to unspecified
            version = null
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver { enabled.set(true) }
                }
                ${semverProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifySemver")

        assertSoftly { softly ->
            semverProbe.assertOutput(softly, result.output)
            softly.assertThat(result.output).contains("Project version must be set")
        }
    }

    @Test
    fun `easySemver of fails on invalid semver`() {
        val semverProbe =
            probeTask("verifySemver") {
                prelude(
                    "try { com.mreil.easy.semver.EasySemver.of(project).get(); println(\"UNEXPECTED_SUCCESS\") } catch (e: Exception) { println(\"ERROR=\" + (e.message ?: \"null\")) }",
                )
            }
        project.configure {
            version = "not-semver"
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver { enabled.set(true) }
                }
                ${semverProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifySemver")

        assertSoftly { softly ->
            semverProbe.assertOutput(softly, result.output)
            softly.assertThat(result.output).contains("not valid semver")
        }
    }

    @Test
    fun `easySemver of fails when semver not enabled`() {
        val semverProbe =
            probeTask("verifySemver") {
                prelude(
                    "try { com.mreil.easy.semver.EasySemver.of(project).get(); println(\"UNEXPECTED_SUCCESS\") } catch (e: Exception) { println(\"ERROR=\" + (e.message ?: \"null\")) }",
                )
            }
        project.configure {
            version = "1.2.3"
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver {
                        enabled.set(false)
                    }
                }
                ${semverProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifySemver")

        assertSoftly { softly ->
            semverProbe.assertOutput(softly, result.output)
            softly.assertThat(result.output).contains("EasySemver plugin is not enabled")
        }
    }

    @Test
    fun `external plugin can use EasySemver to configure task`() {
        val pluginProbe =
            probeTask("verifyMyPlugin") {
                prelude("val semverProvider = com.mreil.easy.semver.EasySemver.of(project)")
                prelude("val semver = semverProvider.get()")
                expect("MYPLUGIN_MAJOR", "semver.major", "2")
                expect("MYPLUGIN_MINOR", "semver.minor", "5")
                expect("MYPLUGIN_PATCH", "semver.patch", "7")
                expect("MYPLUGIN_VERSION", "semver.version", "2.5.7")
                expect("MYPLUGIN_STABLE", "semver.isStable", "true")
            }
        project.configure {
            group = "com.example.myplugin"
            version = "2.5.7"
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver { enabled.set(true) }
                }
                ${pluginProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyMyPlugin")

        assertSoftly { softly ->
            pluginProbe.assertOutput(softly, result.output)
        }
    }
}
