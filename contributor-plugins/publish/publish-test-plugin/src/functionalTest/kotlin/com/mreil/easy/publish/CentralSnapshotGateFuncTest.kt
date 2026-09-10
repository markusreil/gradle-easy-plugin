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
 * Only `-SNAPSHOT` pre-release versions skip the JReleaser wiring: semver decides when enabled,
 * and a `-SNAPSHOT` suffix fallback decides when semver is off. Other pre-releases (e.g. `1.0.0-RC1`)
 * are treated as deployable releases. Either way no central task is registered for snapshots and
 * `publish` stays detached from `publishToMavenCentral`.
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

    private fun centralBuildGradle(semver: String = "") =
        """
        plugins {
            `java-library`
            id("com.mreil.easy.test.publish")
        }
        easy {
            $semver
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

    /** A pre-release like RC1 is not a SNAPSHOT, so it is a deployable release and central
     *  wiring proceeds. */
    @Test
    fun `RC1 pre-release with semver wires central wiring`() {
        project.configure {
            version = "1.0.0-RC1"
            file("codemeta.json", codemetaJson())
            buildGradle(centralBuildGradle(semver = "semver { enabled.set(true) }"))
            javaSource()
        }

        val tasks = project.build("tasks", "--all")
        assertSoftly { softly ->
            softly.assertThat(tasks.output).contains("checkCentralPoms")
            softly.assertThat(tasks.output).contains("publishToMavenCentral")
            softly.assertThat(tasks.output).contains("generateJreleaserConfig")
        }
    }

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

    /** A release version still wires central when semver is off (default here). */
    @Test
    fun `release version wires central`() {
        project.configure {
            file("codemeta.json", codemetaJson())
            buildGradle(centralBuildGradle())
            javaSource()
        }

        val result = project.build("tasks", "--all")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("checkCentralPoms")
            softly.assertThat(result.output).contains("publishToMavenCentral")
        }
    }
}
