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

    private fun stageCodemetaJson() {
        // generateJreleaserConfig is gated on checkCentralPoms, so the fixture needs valid POM metadata.
        project.configure {
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "license": "https://spdx.org/licenses/MIT",
                  "codeRepository": "https://github.com/example/demo",
                  "author": [{ "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }]
                }
                """.trimIndent(),
            )
        }
    }

    private fun stageCentralCredentials() {
        project.configure {
            // Credentials are required at execution time: the fixture must supply them.
            // GPG keys are base64-encoded, the wiring decodes them before rendering.
            systemProperty("jreleaser.gpg.publicKey", "dGVzdC1ncGctcHVibGljLWtleQ==")
            systemProperty("jreleaser.gpg.privateKey", "dGVzdC1ncGctcHJpdmF0ZS1rZXk=")
            systemProperty("jreleaser.gpg.passphrase", "test-gpg-passphrase")
            systemProperty("jreleaser.mavencentral.username", "test-central-username")
            systemProperty("jreleaser.mavencentral.password", "test-central-password")
        }
    }

    /** Generated JReleaser YAML contains signing and staging-deploy config and no `release` block. */
    @Test
    fun `generateJreleaserConfig creates yaml with signing and staging and no release`() {
        project.configure {
            stageCodemetaJson()
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
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
            stageCentralCredentials()
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
            softly.assertThat(text).contains("pgp:")
            softly.assertThat(text).contains("publicKey:")
            softly.assertThat(text).contains("secretKey:")
            softly.assertThat(text).contains("passphrase:")
            softly.assertThat(text).contains("test-gpg-public-key")
            softly.assertThat(text).contains("test-gpg-private-key")
            softly.assertThat(text).contains("test-gpg-passphrase")
            softly.assertThat(text).contains("deploy:")
            softly.assertThat(text).contains("mavenCentral:")
            softly.assertThat(text).contains("active: RELEASE")
            softly.assertThat(text).contains("nexus2:")
            softly.assertThat(text).contains("sonatype-snapshots:")
            softly.assertThat(text).contains("active: SNAPSHOT")
            softly.assertThat(text).contains("snapshotSupported: true")
            softly.assertThat(text).contains("stagingRepositories:")
            softly.assertThat(text).contains(project.file("build/stagingRepo").invariantSeparatorsPath)
            // JReleaser rejects sequences whose indicator sits at the parent key indent -
            // guard the indented form explicitly (plain contains() would miss it).
            softly.assertThat(text).contains("\n          - ${project.file("build/stagingRepo").invariantSeparatorsPath}")
            softly.assertThat(text).contains("username:")
            softly.assertThat(text).contains("password:")
            softly.assertThat(text).contains("test-central-username")
            softly.assertThat(text).contains("version: 1.0.0")
            softly.assertThat(text).doesNotContain("release:")
            softly.assertThat(text).doesNotContain("nexus3:")
        }
    }

    /** With the test nexus URL set, YAML targets a `nexus3/local-test` deployer and demotes Central. */
    @Test
    fun `generateJreleaserConfig with nexus url emits nexus3 deployer`() {
        project.configure {
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "license": "https://spdx.org/licenses/MIT",
                  "codeRepository": "https://github.com/example/demo",
                  "author": [{ "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }]
                }
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
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
            systemProperty("jreleaser.gpg.publicKey", "dGVzdC1ncGctcHVibGljLWtleQ==")
            systemProperty("jreleaser.gpg.privateKey", "dGVzdC1ncGctcHJpdmF0ZS1rZXk=")
            systemProperty("jreleaser.gpg.passphrase", "test-gpg-passphrase")
            systemProperty("jreleaser.mavencentral.username", "test-central-username")
            systemProperty("jreleaser.mavencentral.password", "test-central-password")
            systemProperty(
                "jreleaser.testNexusUrl",
                "http://localhost:8081/service/rest/v1/components?repository=maven-releases",
            )
        }

        project.build("generateJreleaserConfig", "--info")

        val yaml = project.file("build/jreleaser/jreleaser.yml")
        assertSoftly { softly ->
            softly.assertThat(yaml).exists()
            val text = yaml.readText()
            softly.assertThat(text).contains("nexus3:")
            softly.assertThat(text).contains("local-test:")
            softly.assertThat(text).contains("http://localhost:8081/service/rest/v1/components?repository=maven-releases")
            softly.assertThat(text).contains("applyMavenCentralRules: true")
            softly.assertThat(text).contains("NEVER")
            softly.assertThat(text).doesNotContain("SNAPSHOT")
            softly.assertThat(text).contains("\n          - ")
        }
    }
}
