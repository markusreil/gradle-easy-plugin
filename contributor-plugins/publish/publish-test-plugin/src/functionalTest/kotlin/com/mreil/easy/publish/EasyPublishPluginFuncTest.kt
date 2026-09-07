package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

/**
 * Smoke tests for the publish contributor.
 *
 * Two end-to-end cases execute `publish` against a file repository and assert produced
 * artifacts and POM metadata. All other wiring (task registration, validation,
 * `java-gradle-plugin` guard) is covered by fast unit tests in `publish-plugin`.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyPublishPluginFuncTest {
    lateinit var project: GradleTestProject

    /** Smoke: `java-library` publishes jar + POM with pom fields to a file repo. */
    @Test
    fun `smoke publishes java library artifact with pom metadata`() {
        lateinit var repoDir: File
        val projectName = project.projectDir.name
        project.configure {
            repoDir = createDir("repo")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        mavenRepo("testRepo", "${repoDir.invariantSeparatorsPath}")
                    }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        val result = project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = projectName)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
            softly.assertThat(project).hasArtifact(repoDir, coordinates)
            softly
                .assertThat(project)
                .hasPom(repoDir, coordinates)
                .hasGroupId("com.example")
                .hasArtifactId(projectName)
                .hasVersion("1.0.0")
        }
    }

    /** Smoke: `java-gradle-plugin` marker POM uses `<pluginId>.gradle.plugin` and depends on main jar. */
    @Test
    fun `smoke publishes gradle plugin marker and artifact`() {
        lateinit var repoDir: File
        val pluginId = "com.example.myplugin"
        val pluginGroup = "com.example"
        val pluginVersion = "1.0.0"
        val projectName = project.projectDir.name
        project.configure {
            repoDir = createDir("repo")
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
                        signingEnabled.set(false)
                        mavenRepo("testRepo", "${repoDir.invariantSeparatorsPath}")
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
        }

        val result = project.build("publish", "--info")

        val markerCoordinates = MavenCoordinates(group = pluginId, name = "$pluginId.gradle.plugin")
        val pluginCoordinates = MavenCoordinates(name = projectName)
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
            softly
                .assertThat(project)
                .hasPom(repoDir, markerCoordinates)
                .hasGroupId(pluginId)
                .hasArtifactId("$pluginId.gradle.plugin")
                .hasVersion(pluginVersion)
                .hasDependency(pluginGroup, projectName, pluginVersion)
            softly.assertThat(project).hasArtifact(repoDir, pluginCoordinates)
        }
    }
}
