package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Functional tests for the unified Maven Central snapshot gate in [com.mreil.easy.publish.central.EasyJreleaserPlugin].
 *
 * A `-SNAPSHOT` pre-release skips the JReleaser wiring: no central task is registered for snapshots,
 * `publish` stays detached from `publishToMavenCentral`, and the skip is logged.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class CentralSnapshotGateFuncTest {
    lateinit var project: GradleTestProject

    private fun codemetaJson() =
        """
        {
          "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
          "@type": "SoftwareSourceCode",
          "name": "demo",
          "version": "1.0.0"
        }
        """.trimIndent()

    private fun centralBuildGradle() =
        """
        plugins {
            `java-library`
            id("com.mreil.easy.test.publish")
        }
        easy {
            publish {
                enabled.set(true)
                toMavenCentral()
                toMavenStaging()
                // toMavenCentral() turns signing on by default; these tests don't
                // supply GPG keys, so disable signing explicitly.
                signingEnabled.set(false)
            }
            codemeta { enabled.set(true) }
        }
        """.trimIndent()

    /** With semver off, the `-SNAPSHOT` suffix fallback still skips all central wiring, with a log. */
    @Test
    fun `snapshot version without semver skips central wiring with a log`() {
        project.configure {
            version = "1.0.0-SNAPSHOT"
            file("codemeta.json", codemetaJson())
            buildGradle(centralBuildGradle())
            javaSource()
        }

        val result = project.build("publish", "--dry-run")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Skipping Maven Central deploy")
            softly.assertThat(result.output).doesNotContain("publishToMavenCentral")
            softly.assertThat(result.output).doesNotContain("checkCentralPoms")
        }
    }
}
