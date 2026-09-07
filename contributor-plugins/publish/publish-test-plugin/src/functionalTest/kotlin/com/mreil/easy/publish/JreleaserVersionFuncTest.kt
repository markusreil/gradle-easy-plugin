package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The JReleaser CLI version resolves from the consumer's `libs` version catalog
 * (`jreleaser` alias), falling back to the hard default when absent.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class JreleaserVersionFuncTest {
    lateinit var project: GradleTestProject

    /** Catalog version wins over the hard default. */
    @Test
    fun `jreleaser version comes from libs catalog when present`() {
        val versionProbe =
            probeTask("verifyJreleaserVersion") {
                prelude(
                    "val dep = project.configurations.getByName(\"jreleaser\").dependencies.find { it.name == \"jreleaser\" }!!",
                )
                expect("JRELEASER_VERSION", "dep.version", "9.9.9-catalog")
            }
        project.configure {
            file(
                "gradle/libs.versions.toml",
                """
                [versions]
                jreleaser = "9.9.9-catalog"
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                    }
                }
                ${versionProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyJreleaserVersion")

        assertSoftly { softly ->
            versionProbe.assertOutput(softly, result.output)
        }
    }

    /** Without a catalog alias the hard default is used (mirrors `JreleaserVersions.DEFAULT_VERSION`). */
    @Test
    fun `jreleaser version falls back to default without catalog`() {
        val versionProbe =
            probeTask("verifyJreleaserVersion") {
                prelude(
                    "val dep = project.configurations.getByName(\"jreleaser\").dependencies.find { it.name == \"jreleaser\" }!!",
                )
                expect("JRELEASER_VERSION", "dep.version", "1.25.0")
            }
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                    }
                }
                ${versionProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyJreleaserVersion")

        assertSoftly { softly ->
            versionProbe.assertOutput(softly, result.output)
        }
    }
}
