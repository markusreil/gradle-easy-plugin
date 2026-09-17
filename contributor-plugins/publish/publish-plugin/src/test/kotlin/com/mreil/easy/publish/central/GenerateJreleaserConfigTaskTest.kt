package com.mreil.easy.publish.central

import com.mreil.easy.publish.central.JreleaserJson.Config
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun `central json has mavenCentral deployer and no signing`() {
        val json = parse(JreleaserJson.buildJson(centralConfig()))
        val project = json["project"]!!.jsonObject
        val maven = json["deploy"]!!.jsonObject["maven"]!!.jsonObject
        val central = mavenCentral(json)

        assertSoftly { softly ->
            softly.assertThat(project["name"]?.jsonPrimitive?.content).isEqualTo("demo")
            softly.assertThat(project["version"]?.jsonPrimitive?.content).isEqualTo("1.0.0")
            softly
                .assertThat(
                    project["languages"]
                        ?.jsonObject
                        ?.get("java")
                        ?.jsonObject
                        ?.get("groupId")
                        ?.jsonPrimitive
                        ?.content,
                ).isEqualTo("com.example")
            softly.assertThat(maven).containsKey("mavenCentral")
            softly.assertThat(central["active"]?.jsonPrimitive?.content).isEqualTo("RELEASE")
            softly
                .assertThat(central["url"]?.jsonPrimitive?.content)
                .isEqualTo("https://central.sonatype.com/api/v1/publisher")
            softly
                .assertThat(central["stagingRepositories"]?.jsonArray?.map { it.jsonPrimitive.content })
                .containsExactly("build/stagingRepo")
            softly.assertThat(json).doesNotContainKeys("signing", "release")
            softly.assertThat(maven).doesNotContainKey("nexus3")
        }
    }

    /** Task action writes the rendered JSON to `outputFile` and creates parent directories. */
    @Test
    fun `generate writes json to outputFile when invoked as task action`() {
        val task = createTask()

        task.generate()

        val generated = File(tempDir, "jreleaser/jreleaser.json")
        assertThat(generated).exists()
        val json = parse(generated.readText())
        val project = json["project"]!!.jsonObject
        val central = mavenCentral(json)
        assertSoftly { softly ->
            softly.assertThat(project["name"]?.jsonPrimitive?.content).isEqualTo("demo")
            softly.assertThat(project["version"]?.jsonPrimitive?.content).isEqualTo("1.0.0")
            softly.assertThat(central["active"]?.jsonPrimitive?.content).isEqualTo("RELEASE")
        }
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
                it.outputFile.set(File(tempDir, "jreleaser/jreleaser.json"))
                it.mavenCentralUsername.set("user")
                it.mavenCentralPassword.set("pass")
            }.get()
    }

    @Test
    fun `json omits snapshots deployer`() {
        val json = parse(JreleaserJson.buildJson(centralConfig()))
        val maven = json["deploy"]!!.jsonObject["maven"]!!.jsonObject

        assertSoftly { softly ->
            // Snapshots publish directly via maven-publish (toSonatypeSnapshots), never via JReleaser.
            softly.assertThat(maven).doesNotContainKey("nexus2")
            softly.assertThat(maven).doesNotContainKey("sonatype-snapshots")
            softly.assertThat(json.toString()).doesNotContain("SNAPSHOT")
            softly.assertThat(json.toString()).doesNotContain("https://central.sonatype.com/repository/maven-snapshots/")
            softly.assertThat(json.toString()).doesNotContain("snapshotSupported")
        }
    }

    @Test
    fun `nexus json demotes central and snapshots and adds nexus3 deployer`() {
        val json =
            parse(
                JreleaserJson.buildJson(
                    centralConfig().copy(
                        nexusUrl = "http://localhost:8081/service/rest/v1/components?repository=maven-releases",
                        nexusUsername = "admin",
                        nexusPassword = "admin123",
                    ),
                ),
            )
        val maven = json["deploy"]!!.jsonObject["maven"]!!.jsonObject
        val central = mavenCentral(json)
        val nexus3 = maven["nexus3"]!!.jsonObject["local-test"]!!.jsonObject

        assertSoftly { softly ->
            softly
                .assertThat(nexus3["url"]?.jsonPrimitive?.content)
                .isEqualTo("http://localhost:8081/service/rest/v1/components?repository=maven-releases")
            softly.assertThat(nexus3["authorization"]?.jsonPrimitive?.content).isEqualTo("BASIC")
            softly.assertThat(central["active"]?.jsonPrimitive?.content).isEqualTo("NEVER")
            softly.assertThat(json.toString()).doesNotContain("applyMavenCentralRules")
            softly.assertThat(json.toString()).doesNotContain("\"SNAPSHOT\"")
        }
    }

    @Test
    fun `stagingRepositories is a json array`() {
        val central = mavenCentral(parse(JreleaserJson.buildJson(centralConfig())))

        assertThat(central["stagingRepositories"]?.jsonArray?.map { it.jsonPrimitive.content })
            .containsExactly("build/stagingRepo")
    }

    @Test
    fun `json parses back to expected structure`() {
        val json = parse(JreleaserJson.buildJson(centralConfig()))
        val project = json["project"]!!.jsonObject
        val central = mavenCentral(json)

        assertSoftly { softly ->
            softly.assertThat(project["name"]?.jsonPrimitive?.content).isEqualTo("demo")
            softly.assertThat(project["version"]?.jsonPrimitive?.content).isEqualTo("1.0.0")
            softly.assertThat(central["active"]?.jsonPrimitive?.content).isEqualTo("RELEASE")
            softly
                .assertThat(central["url"]?.jsonPrimitive?.content)
                .isEqualTo("https://central.sonatype.com/api/v1/publisher")
            softly
                .assertThat(central["stagingRepositories"]?.jsonArray?.map { it.jsonPrimitive.content })
                .containsExactly("build/stagingRepo")
        }
    }

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun mavenCentral(json: JsonObject): JsonObject =
        json["deploy"]!!
            .jsonObject["maven"]!!
            .jsonObject["mavenCentral"]!!
            .jsonObject["sonatype"]!!
            .jsonObject

    private fun centralConfig(): Config =
        Config(
            projectName = "demo",
            projectVersion = "1.0.0",
            projectGroupId = "com.example",
            stagingDirs = listOf("build/stagingRepo"),
            mavenCentralUsername = "dummy-mavencentral-username",
            mavenCentralPassword = "dummy-mavencentral-password",
        )
}
