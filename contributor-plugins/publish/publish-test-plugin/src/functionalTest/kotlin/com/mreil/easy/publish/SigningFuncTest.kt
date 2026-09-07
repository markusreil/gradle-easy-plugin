package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Base64

/**
 * Smoke tests for Gradle `signing` wiring (`SigningWiring`).
 *
 * Uses a committed throwaway test-only PGP key (`signing-test-key.asc`, passphrase
 * `easy-test-passphrase`, `easy-publish-test@example.com`) — never a real key.
 * Test builds run with configuration cache enabled by default, which guards the
 * task actions against capturing project objects (see `SigningWiring`).
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class SigningFuncTest {
    lateinit var project: GradleTestProject

    /** `publish` signs all artifacts (jar + pom) when GPG keys are present. */
    @Test
    fun `publish signs artifacts when keys are present`() {
        project.configure {
            buildGradle(publishBuild())
            javaSource()
            systemProperty("jreleaser.gpg.privateKey", testPrivateKeyBase64())
            systemProperty("jreleaser.gpg.passphrase", "easy-test-passphrase")
        }

        val result = project.build("publish", "--info")
        // Second build must reuse the entry — task actions capturing the project fail CC store.
        val reused = project.build("publish")

        val stagingRepo = project.file("build/stagingRepo")
        val coordinates = MavenCoordinates(name = project.projectDir.name)
        val jarAsc = project.mavenArtifact(stagingRepo, coordinates.copy(extension = "jar.asc"))
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("signMavenPublication")
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates)
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates.copy(extension = "jar.asc"))
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates.copy(extension = "pom.asc"))
            softly.assertThat(jarAsc.readText()).startsWith("-----BEGIN PGP SIGNATURE-----")
            softly.assertThat(reused.output).contains("Reusing configuration cache")
        }
    }

    /** `publish` signs both the plugin and the marker publications of a `java-gradle-plugin` project. */
    @Test
    @Suppress("LongMethod")
    fun `publish signs gradle plugin and marker publications`() {
        val pluginId = "com.example.myplugin"
        val projectName = project.projectDir.name
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-gradle-plugin`
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                gradlePlugin {
                    plugins {
                        create("myPlugin") {
                            id = "$pluginId"
                            implementationClass = "com.example.MyPlugin"
                        }
                    }
                }
                easy {
                    publish {
                        enabled.set(true)
                        toMavenStaging()
                    }
                }
                """.trimIndent(),
            )
            kotlinSource(
                className = "MyPlugin",
                body =
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project
                    class MyPlugin : Plugin<Project> {
                        override fun apply(target: Project) {}
                    }
                    """.trimIndent(),
            )
            systemProperty("jreleaser.gpg.privateKey", testPrivateKeyBase64())
            systemProperty("jreleaser.gpg.passphrase", "easy-test-passphrase")
        }

        val result = project.build("publish", "--info")
        // Second build must reuse the entry — task actions capturing the project fail CC store.
        val reused = project.build("publish")

        val stagingRepo = project.file("build/stagingRepo")
        val pluginCoordinates = MavenCoordinates(name = projectName)
        val markerCoordinates = MavenCoordinates(group = pluginId, name = "$pluginId.gradle.plugin")
        val pluginJarAsc = project.mavenArtifact(stagingRepo, pluginCoordinates.copy(extension = "jar.asc"))
        val markerPomAsc = project.mavenArtifact(stagingRepo, markerCoordinates.copy(extension = "pom.asc"))
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("signPluginMavenPublication")
            softly.assertThat(result.output).contains("signMyPluginPluginMarkerMavenPublication")
            softly.assertThat(project).hasArtifact(stagingRepo, pluginCoordinates)
            softly.assertThat(project).hasArtifact(stagingRepo, pluginCoordinates.copy(extension = "jar.asc"))
            softly.assertThat(project).hasArtifact(stagingRepo, pluginCoordinates.copy(extension = "pom.asc"))
            softly.assertThat(project).hasArtifact(stagingRepo, markerCoordinates.copy(extension = "pom.asc"))
            softly.assertThat(pluginJarAsc.readText()).startsWith("-----BEGIN PGP SIGNATURE-----")
            softly.assertThat(markerPomAsc.readText()).startsWith("-----BEGIN PGP SIGNATURE-----")
            softly.assertThat(reused.output).contains("Reusing configuration cache")
        }
    }

    /** `publish` fails with an actionable message when signing is enabled but keys are missing. */
    @Test
    fun `publish fails with actionable message when keys are missing`() {
        project.configure {
            buildGradle(publishBuild())
            javaSource()
        }

        val result = project.buildAndFail("publish", "--info")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Signing is enabled but missing GPG properties")
            softly.assertThat(result.output).contains("signingEnabled")
        }
    }

    /** `publish` succeeds without signatures when signing is disabled. */
    @Test
    fun `publish succeeds without signatures when signing is disabled`() {
        project.configure {
            buildGradle(publishBuild(signing = false))
            javaSource()
        }

        project.build("publish")

        val stagingRepo = project.file("build/stagingRepo")
        val coordinates = MavenCoordinates(name = project.projectDir.name)
        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(stagingRepo, coordinates)
            softly.assertThat(project).doesNotHaveArtifact(stagingRepo, coordinates.copy(extension = "jar.asc"))
        }
    }

    private fun publishBuild(signing: Boolean = true): String {
        val signingLine = if (signing) "" else "\n                        signingEnabled.set(false)"
        return """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            easy {
                publish {
                    enabled.set(true)$signingLine
                    toMavenStaging()
                }
            }
            """.trimIndent()
    }

    private fun testPrivateKeyBase64(): String {
        val armored =
            javaClass.getResource("/signing-test-key.asc")?.readText()
                ?: error("Test PGP key resource signing-test-key.asc not found")
        return Base64.getEncoder().encodeToString(armored.toByteArray(Charsets.UTF_8))
    }
}
