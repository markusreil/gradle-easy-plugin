package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

/**
 * Smoke test for Maven Central publishing via JReleaser.
 *
 * Verifies YAML generation (staging deploy config, no `signing`/`release` blocks). Inheritance, task registration,
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
            systemProperty("jreleaser.mavencentral.username", "test-central-username")
            systemProperty("jreleaser.mavencentral.password", "test-central-password")
        }
    }

    /** Generated JReleaser YAML contains staging-deploy config and no `signing`/`release` blocks. */
    @Test
    fun `generateJreleaserConfig creates yaml with staging and no signing and no release`() {
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
            softly.assertThat(text).doesNotContain("signing:")
            softly.assertThat(text).contains("deploy:")
            softly.assertThat(text).contains("mavenCentral:")
            softly.assertThat(text).contains("active: RELEASE")
            // Snapshots publish directly via maven-publish (toSonatypeSnapshots), never via JReleaser.
            softly.assertThat(text).doesNotContain("nexus2:")
            softly.assertThat(text).doesNotContain("sonatype-snapshots:")
            softly.assertThat(text).doesNotContain("active: SNAPSHOT")
            softly.assertThat(text).doesNotContain("snapshotSupported")
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

    /** Publication-less projects (e.g. a java-less root) stage nothing: their phantom dirs must not reach the YAML. */
    @Test
    fun `generateJreleaserConfig omits staging dirs of projects without publications`() {
        project.configure {
            settings("include(\"child\")")
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
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                        id("com.mreil.easy.test.publish")
                    }
                    """.trimIndent(),
                )
                javaSource()
            }
            stageCentralCredentials()
        }

        project.build("generateJreleaserConfig")

        val yaml = project.file("build/jreleaser/jreleaser.yml")
        val rootStaging = project.file("build/stagingRepo").invariantSeparatorsPath
        val childStaging = File(project.projectDir, "child/build/stagingRepo").invariantSeparatorsPath
        assertSoftly { softly ->
            softly.assertThat(yaml).exists()
            val text = yaml.readText()
            softly.assertThat(text).contains("\n          - $childStaging")
            softly.assertThat(text).doesNotContain("\n          - $rootStaging")
        }
    }
}
