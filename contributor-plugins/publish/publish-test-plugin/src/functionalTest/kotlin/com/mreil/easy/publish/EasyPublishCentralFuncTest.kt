package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Smoke test for Maven Central publishing via JReleaser.
 *
 * Verifies YAML generation (signing + staging, no `release` block). Inheritance, task registration,
 * and wiring are covered by fast unit tests in `publish-plugin`.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyPublishCentralFuncTest {
    lateinit var project: GradleTestProject

    /** Generated JReleaser YAML contains signing and staging-deploy config and no `release` block. */
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

        val result = project.build("generateJreleaserConfig", "--info")

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
}
