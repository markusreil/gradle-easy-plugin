package com.mreil.easy.semver

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.assertSoftly
import com.mreil.easy.test.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
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
                    semver {}
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
                    semver {}
                }
                tasks.register("verifySemver") {
                    doLast {
                        com.mreil.easy.semver.EasySemver.of(project).get()
                    }
                }
                """.trimIndent(),
            )
        }

        val result =
            project.buildAndFail("verifySemver")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Project version must be set")
        }
    }

    @Test
    fun `easySemver of fails on invalid semver`() {
        project.configure {
            version = "not-semver"
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver {}
                }
                tasks.register("verifySemver") {
                    doLast {
                        com.mreil.easy.semver.EasySemver.of(project).get()
                    }
                }
                """.trimIndent(),
            )
        }

        val result =
            project.buildAndFail("verifySemver")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("not valid semver")
        }
    }

    @Test
    fun `easySemver of fails when semver not enabled`() {
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
                tasks.register("verifySemver") {
                    doLast {
                        com.mreil.easy.semver.EasySemver.of(project).get()
                    }
                }
                """.trimIndent(),
            )
        }

        val result =
            project.buildAndFail("verifySemver")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("EasySemver plugin is not enabled")
        }
    }

    @Test
    fun `external plugin can use EasySemver to configure task`() {
        project.configure {
            group = "com.example.myplugin"
            version = "2.5.7"
            buildGradle(
                """
                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.kotlin.dsl.apply

                plugins {
                    `java-library`
                    id("com.mreil.easy.test.semver")
                }
                easy {
                    semver {}
                }

                class MyPlugin : Plugin<Project> {
                    override fun apply(target: Project) {
                        val semverProvider = com.mreil.easy.semver.EasySemver.of(target)
                        target.tasks.register("verifyMyPlugin") {
                            doLast {
                                val semver = semverProvider.get()
                                println("MYPLUGIN_MAJOR=" + semver.major)
                                println("MYPLUGIN_MINOR=" + semver.minor)
                                println("MYPLUGIN_PATCH=" + semver.patch)
                                println("MYPLUGIN_VERSION=" + semver.version)
                                println("MYPLUGIN_STABLE=" + semver.isStable)
                            }
                        }
                    }
                }

                apply<MyPlugin>()
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyMyPlugin")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("MYPLUGIN_MAJOR=2")
            softly.assertThat(result.output).contains("MYPLUGIN_MINOR=5")
            softly.assertThat(result.output).contains("MYPLUGIN_PATCH=7")
            softly.assertThat(result.output).contains("MYPLUGIN_VERSION=2.5.7")
            softly.assertThat(result.output).contains("MYPLUGIN_STABLE=true")
        }
    }
}
