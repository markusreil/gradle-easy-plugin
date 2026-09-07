package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Executes `publishToMavenCentral` itself (never `-x`-excluded) with the configuration
 * cache on: the deploy task must store and reuse its CC entry cleanly.
 *
 * The fixture enables Central with no publications (no `java` plugin), so the
 * empty-staging guard trips before the JReleaser CLI runs — hermetic, no network —
 * while still forcing CC to serialize the deploy task and its actions.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class JreleaserDeployFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `publishToMavenCentral skips friendly when nothing staged and reuses configuration cache`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.publish")
                }
                // A repository is required so CC can fingerprint the JReleaser CLI
                // classpath (mirrors real builds; resolution itself never runs here —
                // the empty-staging guard trips before the CLI executes).
                repositories {
                    mavenCentral()
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenCentral()
                    }
                }
                """.trimIndent(),
            )
            systemProperty("jreleaser.gpg.publicKey", "dGVzdC1ncGctcHVibGljLWtleQ==")
            systemProperty("jreleaser.gpg.privateKey", "dGVzdC1ncGctcHJpdmF0ZS1rZXk=")
            systemProperty("jreleaser.gpg.passphrase", "test-gpg-passphrase")
            systemProperty("jreleaser.mavencentral.username", "test-central-username")
            systemProperty("jreleaser.mavencentral.password", "test-central-password")
        }

        val first = project.build("publishToMavenCentral")
        assertSoftly { softly ->
            softly.assertThat(first.output).contains("nothing staged, skipping deploy")
            softly.assertThat(first.output).contains("Configuration cache entry stored")
        }

        val second = project.build("publishToMavenCentral")
        assertSoftly { softly ->
            softly.assertThat(second.output).contains("nothing staged, skipping deploy")
            softly.assertThat(second.output).contains("Reusing configuration cache")
        }
    }
}
