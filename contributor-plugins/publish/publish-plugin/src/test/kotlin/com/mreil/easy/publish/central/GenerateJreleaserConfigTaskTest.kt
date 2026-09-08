package com.mreil.easy.publish.central

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.mreil.easy.publish.central.JreleaserYaml.Config
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class GenerateJreleaserConfigTaskTest {
    @Test
    fun `central yaml has mavenCentral deployer and no signing`() {
        val yaml = JreleaserYaml.buildYaml(centralConfig())

        assertSoftly { softly ->
            softly.assertThat(yaml).contains("name: demo")
            softly.assertThat(yaml).contains("version: 1.0.0")
            softly.assertThat(yaml).contains("languages:")
            softly.assertThat(yaml).contains("groupId: com.example")
            softly.assertThat(yaml).doesNotContain("signing:")
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
    fun `yaml omits snapshots deployer`() {
        val yaml = JreleaserYaml.buildYaml(centralConfig())

        assertSoftly { softly ->
            // Snapshots publish directly via maven-publish (toSonatypeSnapshots), never via JReleaser.
            softly.assertThat(yaml).doesNotContain("nexus2:")
            softly.assertThat(yaml).doesNotContain("sonatype-snapshots:")
            softly.assertThat(yaml).doesNotContain("active: SNAPSHOT")
            softly.assertThat(yaml).doesNotContain("https://central.sonatype.com/repository/maven-snapshots/")
            softly.assertThat(yaml).doesNotContain("snapshotSupported")
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
            softly.assertThat(yaml).doesNotContain("applyMavenCentralRules")
            softly.assertThat(yaml).contains("active: NEVER")
            softly.assertThat(yaml).doesNotContain("active: SNAPSHOT")
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

    private fun centralConfig(): Config =
        Config(
            projectName = "demo",
            projectVersion = "1.0.0",
            projectGroupId = "com.example",
            stagingDirs = listOf("build/stagingRepo"),
            mavenCentralUsername = "dummy-mavencentral-username",
            mavenCentralPassword = "dummy-mavencentral-password",
        )

    companion object {
        private val yamlReader = ObjectMapper(YAMLFactory())
    }
}
