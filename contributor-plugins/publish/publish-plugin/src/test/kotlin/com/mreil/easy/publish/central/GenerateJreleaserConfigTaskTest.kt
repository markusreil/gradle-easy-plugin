package com.mreil.easy.publish.central

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.mreil.easy.publish.central.JreleaserYaml.Config
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateJreleaserConfigTaskTest {
    @TempDir
    lateinit var tempDir: File

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

    /** Task action writes the rendered YAML to `outputFile` and creates parent directories. */
    @Test
    fun `generate writes yaml to outputFile when invoked as task action`() {
        val task = createTask()

        task.generate()

        val generated = File(tempDir, "jreleaser/jreleaser.yml")
        assertThat(generated).exists()
        val text = generated.readText()
        assertThat(text).contains("name: demo")
        assertThat(text).contains("version: 1.0.0")
        assertThat(text).contains("active: RELEASE")
    }

    /** Missing Central credentials must fail at task action (not configuration) so absent
     *  `-D` flags still produce an actionable error during the first build attempt. */
    @Test
    fun `generate fails with actionable GradleException when mavenCentralUsername is missing`() {
        val task = createTask().apply { mavenCentralUsername.set(null as String?) }

        assertThatThrownBy { task.generate() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("Maven Central username is required")
    }

    @Test
    fun `generate fails with actionable GradleException when mavenCentralPassword is missing`() {
        val task = createTask().apply { mavenCentralPassword.set(null as String?) }

        assertThatThrownBy { task.generate() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("Maven Central password is required")
    }

    private fun createTask(): GenerateJreleaserConfigTask {
        val project = ProjectBuilder.builder().build()
        return project
            .tasks
            .register("generateJreleaserConfig", GenerateJreleaserConfigTask::class.java) {
                it.projectName.set("demo")
                it.projectVersion.set("1.0.0")
                it.projectGroupId.set("com.example")
                it.stagingDirs.set(listOf("build/stagingRepo"))
                it.outputFile.set(File(tempDir, "jreleaser/jreleaser.yml"))
                it.mavenCentralUsername.set("user")
                it.mavenCentralPassword.set("pass")
            }.get()
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
