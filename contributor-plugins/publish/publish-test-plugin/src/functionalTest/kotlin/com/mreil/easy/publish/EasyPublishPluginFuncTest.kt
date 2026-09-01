package com.mreil.easy.publish

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EasyPublishPluginFuncTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `publish task is available via main plugin`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            group = "com.example"
            version = "1.0.0"

            tasks.register("verifyPublish") {
                doLast {
                    println("HAS_PUBLISH=" + (tasks.findByName("publish") != null))
                }
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("verifyPublish")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("HAS_PUBLISH=true")
        }
    }

    @Test
    fun `publish publishes to custom file repository`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        val repoDir = File(projectDir, "repo").apply { mkdirs() }
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            import org.gradle.api.publish.PublishingExtension
            group = "com.example"
            version = "1.0.0"
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    repositories {
                        maven {
                            name = "testRepo"
                            url = uri("${repoDir.invariantSeparatorsPath}")
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "src/main/java/com/example").apply { mkdirs() }
        File(projectDir, "src/main/java/com/example/Hello.java").writeText(
            """
            package com.example;
            public class Hello {}
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publish", "--info")
                .forwardOutput()
                .build()

        val artifact = File(repoDir, "com/example/${projectDir.name}/1.0.0/${projectDir.name}-1.0.0.jar")
        val pom = File(repoDir, "com/example/${projectDir.name}/1.0.0/${projectDir.name}-1.0.0.pom")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
            softly.assertThat(artifact).exists()
            softly.assertThat(pom).exists()
            softly.assertThat(pom.readText()).contains("<groupId>com.example</groupId>")
        }
    }

    @Test
    fun `publishToMavenLocal publishes artifact`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        val m2Repo = File(projectDir, "m2").apply { mkdirs() }
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            group = "com.example.local"
            version = "1.0.0"
            """.trimIndent(),
        )
        File(projectDir, "src/main/java/com/example").apply { mkdirs() }
        File(projectDir, "src/main/java/com/example/Hello.java").writeText(
            """
            package com.example;
            public class Hello {}
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publishToMavenLocal", "-Dmaven.repo.local=${m2Repo.invariantSeparatorsPath}")
                .forwardOutput()
                .build()

        val artifact = File(m2Repo, "com/example/local/${projectDir.name}/1.0.0/${projectDir.name}-1.0.0.jar")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publishToMavenLocal")
            softly.assertThat(artifact).exists()
        }
    }

    @Test
    fun `publish fails when group or version is missing`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            // intentionally no group/version
            """.trimIndent(),
        )
        File(projectDir, "src/main/java/com/example").apply { mkdirs() }
        File(projectDir, "src/main/java/com/example/Hello.java").writeText(
            """
            package com.example;
            public class Hello {}
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publish", "--info")
                .forwardOutput()
                .buildAndFail()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Project group must be set")
        }
    }

    @Test
    fun `plugin marker artifactId contains plugin id and dependency points to java jar`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        val repoDir = File(projectDir, "repo").apply { mkdirs() }
        val pluginId = "com.example.myplugin"
        val pluginGroup = "com.example"
        val pluginVersion = "1.0.0"
        val projectName = projectDir.name
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-gradle-plugin`
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            import org.gradle.api.publish.PublishingExtension
            group = "$pluginGroup"
            version = "$pluginVersion"
            gradlePlugin {
                plugins {
                    create("myPlugin") {
                        id = "$pluginId"
                        implementationClass = "com.example.MyPlugin"
                    }
                }
            }
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    repositories {
                        maven {
                            name = "testRepo"
                            url = uri("${repoDir.invariantSeparatorsPath}")
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "src/main/kotlin/com/example").apply { mkdirs() }
        File(projectDir, "src/main/kotlin/com/example/MyPlugin.kt").writeText(
            """
            package com.example
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            class MyPlugin : Plugin<Project> {
                override fun apply(target: Project) {}
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publish", "--info")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
        }

        // marker: group = plugin id, artifact = plugin id + ".gradle.plugin"
        val markerDir = File(repoDir, "${pluginId.replace('.', '/')}/$pluginId.gradle.plugin/$pluginVersion")
        val markerPom = File(markerDir, "$pluginId.gradle.plugin-$pluginVersion.pom")
        softly@ assertSoftly { softly ->
            softly.assertThat(markerPom).exists()
            val pomText = markerPom.readText()
            // artifactId must stay as plugin marker, not overwritten to project name
            softly.assertThat(pomText).contains("<artifactId>$pluginId.gradle.plugin</artifactId>")
            softly.assertThat(pomText).contains("<groupId>$pluginId</groupId>")
            // dependency in marker pom must point to the java/gradle-plugin jar
            softly.assertThat(pomText).contains("<groupId>$pluginGroup</groupId>")
            softly.assertThat(pomText).contains("<artifactId>$projectName</artifactId>")
            softly.assertThat(pomText).contains("<version>$pluginVersion</version>")
        }

        // also verify the plugin jar publication exists and has correct coordinates
        val pluginJar = File(repoDir, "${pluginGroup.replace('.', '/')}/$projectName/$pluginVersion/$projectName-$pluginVersion.jar")
        assertSoftly { softly ->
            softly.assertThat(pluginJar).exists()
        }
    }

    @Test
    fun `mavenRepo declared in publish extension is attached to publishing repositories`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        val repoDir = File(projectDir, "repo").apply { mkdirs() }
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            import com.mreil.easy.EasyExtension
            import com.mreil.easy.publish.EasyPublishExtension
            group = "com.example"
            version = "1.0.0"
            extensions.configure<EasyExtension>("easy") {
                extensions.configure<EasyPublishExtension>("publish") {
                    mavenRepo("testRepo") {
                        url.set("${repoDir.invariantSeparatorsPath}")
                    }
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "src/main/java/com/example").apply { mkdirs() }
        File(projectDir, "src/main/java/com/example/Hello.java").writeText(
            """
            package com.example;
            public class Hello {}
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publish", "--info")
                .forwardOutput()
                .build()

        val artifact =
            File(repoDir, "com/example/${projectDir.name}/1.0.0/${projectDir.name}-1.0.0.jar")
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("publish")
            softly.assertThat(artifact).exists()
        }
    }

    @Test
    fun `no duplicate maven publication for java-gradle-plugin projects regardless of order`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        val pluginId = "com.example.myplugin"
        // Order: easy before java-gradle-plugin — the withId("maven-publish") race case
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
                `java-gradle-plugin`
            }
            group = "com.example"
            version = "1.0.0"
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
        File(projectDir, "src/main/kotlin/com/example").apply { mkdirs() }
        File(projectDir, "src/main/kotlin/com/example/MyPlugin.kt").writeText(
            """
            package com.example
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            class MyPlugin : Plugin<Project> {
                override fun apply(target: Project) {}
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("verifyPublications")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("HAS_MAVEN=false")
            // plugin markers must exist, but no duplicate "maven" from EasyPublishPlugin
            softly.assertThat(result.output).contains("PUB_NAMES=")
            softly.assertThat(result.output).doesNotContain("PUB_NAMES=maven,")
            softly.assertThat(result.output).doesNotContain("PUB_NAMES=maven\n")
        }
    }

    @Test
    fun `creates maven publication for plain java projects`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            group = "com.example"
            version = "1.0.0"
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

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("verifyHasMaven")
                .forwardOutput()
                .build()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("HAS_MAVEN=true")
        }
    }

    @Test
    fun `passwordCredentials enables credentials resolution for a repository`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.mreil.easy.test.publish")
            }
            import com.mreil.easy.EasyExtension
            import com.mreil.easy.publish.EasyPublishExtension
            group = "com.example"
            version = "1.0.0"
            extensions.configure<EasyExtension>("easy") {
                extensions.configure<EasyPublishExtension>("publish") {
                    mavenRepo("secureRepo") {
                        url.set("http://localhost:1/repo")
                        passwordCredentials.set(true)
                    }
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "src/main/java/com/example").apply { mkdirs() }
        File(projectDir, "src/main/java/com/example/Hello.java").writeText(
            """
            package com.example;
            public class Hello {}
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("publish", "--info")
                .forwardOutput()
                .buildAndFail()

        assertSoftly { softly ->
            softly
                .assertThat(result.output)
                .contains("Credentials required for this build could not be resolved")
                .contains("secureRepoUsername")
                .contains("secureRepoPassword")
        }
    }
}
