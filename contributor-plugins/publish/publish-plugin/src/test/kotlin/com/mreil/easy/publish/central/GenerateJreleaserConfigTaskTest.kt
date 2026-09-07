package com.mreil.easy.publish.central

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.mreil.easy.publish.central.JreleaserYaml.Config
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class GenerateJreleaserConfigTaskTest {
    @Test
    fun `central yaml has project signing and mavenCentral deployer`() {
        val yaml = JreleaserYaml.buildYaml(centralConfig())

        assertSoftly { softly ->
            softly.assertThat(yaml).contains("name: demo")
            softly.assertThat(yaml).contains("version: 1.0.0")
            softly.assertThat(yaml).contains("languages:")
            softly.assertThat(yaml).contains("groupId: com.example")
            softly.assertThat(yaml).contains("signing:")
            softly.assertThat(yaml).contains("armored: true")
            softly.assertThat(yaml).contains("mavenCentral:")
            softly.assertThat(yaml).contains("active: RELEASE")
            softly.assertThat(yaml).contains("https://central.sonatype.com/api/v1/publisher")
            softly.assertThat(yaml).contains("stagingRepositories:")
            softly.assertThat(yaml).contains("build/stagingRepo")
            softly.assertThat(yaml).doesNotContain("nexus3:")
            softly.assertThat(yaml).doesNotContain("release:")
        }
    }

    @Test
    fun `yaml includes nexus2 snapshots deployer`() {
        val yaml = JreleaserYaml.buildYaml(centralConfig())

        assertSoftly { softly ->
            softly.assertThat(yaml).contains("nexus2:")
            softly.assertThat(yaml).contains("sonatype-snapshots:")
            softly.assertThat(yaml).contains("active: SNAPSHOT")
            softly.assertThat(yaml).contains("https://central.sonatype.com/repository/maven-snapshots/")
            softly.assertThat(yaml).contains("snapshotSupported: true")
        }
    }

    @Test
    fun `nexus yaml demotes central and snapshots and adds nexus3 deployer`() {
        val yaml =
            JreleaserYaml.buildYaml(
                centralConfig().copy(
                    nexusUrl = "http://localhost:8081/service/rest/v1/components?repository=maven-releases",
                    nexusUsername = "admin",
                    nexusPassword = "admin123",
                ),
            )

        assertSoftly { softly ->
            softly.assertThat(yaml).contains("nexus3:")
            softly.assertThat(yaml).contains("local-test:")
            softly.assertThat(yaml).contains("http://localhost:8081/service/rest/v1/components?repository=maven-releases")
            softly.assertThat(yaml).contains("authorization: BASIC")
            softly.assertThat(yaml).contains("applyMavenCentralRules: true")
            softly.assertThat(yaml).contains("active: NEVER")
            softly.assertThat(yaml).doesNotContain("active: SNAPSHOT")
        }
    }

    @Test
    fun `multiline keys use block scalars`() {
        val yaml = JreleaserYaml.buildYaml(centralConfig(gpgPublicKey = ARMOR, gpgPrivateKey = ARMOR))

        assertSoftly { softly ->
            softly.assertThat(yaml).contains("pgp:")
            softly.assertThat(yaml).contains("publicKey: |-")
            softly.assertThat(yaml).contains("secretKey: |-")
            softly.assertThat(yaml).contains("-----BEGIN PGP PUBLIC KEY BLOCK-----")
        }
    }

    @Test
    fun `sequences use indented indicators`() {
        val yaml = JreleaserYaml.buildYaml(centralConfig())

        assertSoftly { softly ->
            // JReleaser rejects indicators at the parent key indent - guard the indented form.
            softly.assertThat(yaml).contains("stagingRepositories:\n          - build/stagingRepo")
        }
    }

    @Test
    fun `yaml parses back to expected structure`() {
        val parsed: Map<String, Any> = yamlReader.readValue(JreleaserYaml.buildYaml(centralConfig()))

        @Suppress("UNCHECKED_CAST")
        val maven = ((parsed["deploy"] as Map<String, Any>)["maven"] as Map<String, Any>)

        @Suppress("UNCHECKED_CAST")
        val central = (maven["mavenCentral"] as Map<String, Any>)["sonatype"] as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val project = parsed["project"] as Map<String, Any>

        assertSoftly { softly ->
            softly.assertThat(project["name"]).isEqualTo("demo")
            softly.assertThat(project["version"]).isEqualTo("1.0.0")
            softly.assertThat(central["active"]).isEqualTo("RELEASE")
            softly.assertThat(central["url"]).isEqualTo("https://central.sonatype.com/api/v1/publisher")
            softly.assertThat(central["stagingRepositories"]).isEqualTo(listOf("build/stagingRepo"))
        }
    }

    private fun centralConfig(
        gpgPublicKey: String = "dummy-gpg-public-key",
        gpgPrivateKey: String = "dummy-gpg-private-key",
    ): Config =
        Config(
            projectName = "demo",
            projectVersion = "1.0.0",
            projectGroupId = "com.example",
            stagingDirs = listOf("build/stagingRepo"),
            gpgPublicKey = gpgPublicKey,
            gpgPrivateKey = gpgPrivateKey,
            gpgPassphrase = "dummy-gpg-passphrase",
            mavenCentralUsername = "dummy-mavencentral-username",
            mavenCentralPassword = "dummy-mavencentral-password",
        )

    companion object {
        private val yamlReader = ObjectMapper(YAMLFactory())

        private val ARMOR =
            """
            -----BEGIN PGP PUBLIC KEY BLOCK-----

            mQGNBGqc2WkBDAC2/bkL2S1zt8gkGpghh3wNXgfjxUs8V0nj8yKYx0vg/gCkDV21
            0fgkrfu4DbTqmV9xcphYjGPOGlrgBbG7HAeKkxk4lt081tY27JfssZIGObtz0ocW
            =5Y9d
            -----END PGP PUBLIC KEY BLOCK-----
            """.trimIndent()
    }
}
