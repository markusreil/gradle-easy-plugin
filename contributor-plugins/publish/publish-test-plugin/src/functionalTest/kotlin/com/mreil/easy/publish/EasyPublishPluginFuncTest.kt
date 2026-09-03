package com.mreil.easy.publish

import com.mreil.easy.test.project.GradleTestProject
import com.mreil.easy.test.project.GradleTestProjectExtension
import com.mreil.easy.test.project.assertj.MavenCoordinates
import com.mreil.easy.test.project.assertj.assertSoftly
import com.mreil.easy.test.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(GradleTestProjectExtension::class)
class EasyPublishPluginFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `publish task is available via main plugin`() {
        val publishProbe =
            probeTask("verifyPublish") {
                taskExists("HAS_PUBLISH", "publish")
            }
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy { publish { enabled.set(true) } }
                ${publishProbe.script()}
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyPublish")

        assertSoftly { softly ->
            publishProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `publish fails when group or version is missing`() {
        project.configure {
            // neither group nor version staged - both default to unspecified
            group = null
            version = null
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                            easy { publish { enabled.set(true) } }
                """.trimIndent(),
            )
            javaSource()
        }

        val result =
            project.buildAndFail("publish", "--info")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Project group must be set")
        }
    }

    @Test
    fun `plugin marker artifactId contains plugin id and dependency points to java jar`() {
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

        val result =
            project.build("publish", "--info")

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

    @Test
    fun `no duplicate maven publication for java-gradle-plugin projects regardless of order`() {
        val pluginId = "com.example.myplugin"
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                    `java-gradle-plugin`
                }
                gradlePlugin {
                    plugins {
                        create("myPlugin") {
                            id = "$pluginId"
                            implementationClass = "com.example.MyPlugin"
                        }
                    }
                }
                tasks.register("verifyPublications") {
                    doLast {
                        println("HAS_JAVA=" + project.plugins.hasPlugin("java"))
                        println("HAS_MAVEN_PUBLISH=" + project.plugins.hasPlugin("maven-publish"))
                        println("HAS_JAVA_GRADLE=" + project.plugins.hasPlugin("org.gradle.java-gradle-plugin"))
                        println("HAS_EASY_BY_NAME=" + (project.extensions.findByName("easy") != null))
                        println("HAS_EASY_BY_TYPE=" + (project.extensions.findByType(com.mreil.easy.EasyExtension::class.java) != null))
                        println("EXTS=" + project.extensions.extensionsSchema.elements.map { it.name }.joinToString(","))
                        val easy = project.extensions.findByName("easy") as? org.gradle.api.plugins.ExtensionAware
                            ?: project.extensions.findByType(com.mreil.easy.EasyExtension::class.java) as? org.gradle.api.plugins.ExtensionAware
                        println("HAS_PUBLISH_EXT_BY_NAME=" + (easy?.extensions?.findByName("publish") != null))
                        println("HAS_PUBLISH_EXT_BY_TYPE=" + (easy?.extensions?.findByType(com.mreil.easy.publish.EasyPublishExtension::class.java) != null))
                        val publishing = project.extensions.findByName("publishing") as? org.gradle.api.publish.PublishingExtension
                        if (publishing == null) {
                            println("NO_PUBLISHING")
                            println("HAS_MAVEN=false")
                            println("PUB_NAMES=")
                            return@doLast
                        }
                        val hasMaven = publishing.publications.findByName("maven") != null
                        val names = publishing.publications.map { it.name }.sorted().joinToString(",")
                        println("HAS_MAVEN=" + hasMaven)
                        println("PUB_NAMES=" + names)
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

        val result =
            project.build("verifyPublications")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("HAS_MAVEN=false")
            softly.assertThat(result.output).contains("PUB_NAMES=")
            softly.assertThat(result.output).doesNotContain("PUB_NAMES=maven,")
            softly.assertThat(result.output).doesNotContain("PUB_NAMES=maven\n")
        }
    }

    @Test
    fun `creates maven publication for plain java projects`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy { publish { enabled.set(true) } }
                tasks.register("verifyHasMaven") {
                    doLast {
                        println("HAS_JAVA=" + project.plugins.hasPlugin("java"))
                        println("HAS_MAVEN_PUBLISH=" + project.plugins.hasPlugin("maven-publish"))
                        println("HAS_JAVA_GRADLE=" + project.plugins.hasPlugin("org.gradle.java-gradle-plugin"))
                        println("HAS_EASY_BY_NAME=" + (project.extensions.findByName("easy") != null))
                        println("HAS_EASY_BY_TYPE=" + (project.extensions.findByType(com.mreil.easy.EasyExtension::class.java) != null))
                        println("EXTS=" + project.extensions.extensionsSchema.elements.map { it.name }.joinToString(","))
                        val easy = project.extensions.findByName("easy") as? org.gradle.api.plugins.ExtensionAware
                            ?: project.extensions.findByType(com.mreil.easy.EasyExtension::class.java) as? org.gradle.api.plugins.ExtensionAware
                        println("HAS_PUBLISH_EXT_BY_NAME=" + (easy?.extensions?.findByName("publish") != null))
                        println("HAS_PUBLISH_EXT_BY_TYPE=" + (easy?.extensions?.findByType(com.mreil.easy.publish.EasyPublishExtension::class.java) != null))
                        if (easy != null) {
                            val pubExt = easy.extensions.findByName("publish")
                                ?: easy.extensions.findByType(com.mreil.easy.publish.EasyPublishExtension::class.java)
                            println("PUBLISH_EXT_CLASS=" + (pubExt?.let { it::class.qualifiedName } ?: "null"))
                            if (pubExt is com.mreil.easy.CanBeEnabled) {
                                println("PUBLISH_ENABLED=" + pubExt.enabled.getOrElse(true))
                            }
                        }
                        val publishing = project.extensions.findByName("publishing") as? org.gradle.api.publish.PublishingExtension
                        if (publishing == null) {
                            println("NO_PUBLISHING")
                            println("HAS_MAVEN=false")
                            return@doLast
                        }
                        val hasMaven = publishing.publications.findByName("maven") != null
                        println("HAS_MAVEN=" + hasMaven)
                        println("PUB_NAMES=" + publishing.publications.map { it.name }.sorted().joinToString(","))
                    }
                }
                """.trimIndent(),
            )
        }

        val result =
            project.build("verifyHasMaven")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("HAS_MAVEN=true")
        }
    }
}
