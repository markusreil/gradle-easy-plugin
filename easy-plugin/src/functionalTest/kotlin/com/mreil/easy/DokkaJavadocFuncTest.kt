package com.mreil.easy

import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.util.zip.ZipFile

/**
 * End-to-end proof that the settings plugin adds the Dokka marker to the root project's
 * buildscript classpath (inherited by subprojects), applies Dokka to Kotlin projects and rewires
 * `javadocJar` to Dokka output, with no Dokka dependency in any module.
 */
@Suppress("TooManyFunctions")
@ExtendWith(GradleTestProjectExtension::class)
class DokkaJavadocFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `applies dokka to kotlin projects and backs javadocJar with dokka output`() {
        val dokkaProbe =
            probeTask("verifyDokka") {
                prelude("val javadocJar = project.tasks.named(\"javadocJar\").get()")
                expect("HAS_DOKKA_PLUGIN", "project.pluginManager.hasPlugin(\"org.jetbrains.dokka-javadoc\")", "true")
                expect(
                    "HAS_DOKKA_TASK",
                    "project.tasks.findByName(\"dokkaGeneratePublicationJavadoc\") != null",
                    "true",
                )
                expect(
                    "JAVADOC_JAR_DEPENDS_ON_DOKKA",
                    "javadocJar.taskDependencies.getDependencies(javadocJar).any " +
                        "{ it.name == \"dokkaGeneratePublicationJavadoc\" }",
                    "true",
                )
            }
        project.configure { stageDokkaProject(dokkaProbe.script()) }

        val result = project.build("verifyDokka", "javadocJar")

        val dokkaOutput = javadocJarContainsDokkaOutput(project.file("build/libs"))

        assertSoftly { softly ->
            dokkaProbe.assertOutput(softly, result.output)
            softly.assertThat(dokkaOutput).describedAs("javadocJar contains real Dokka Javadoc pages").isTrue()
        }
    }

    @Test
    fun `applies dokka to subprojects via inherited root classpath`() {
        val childProbe =
            probeTask("verifyChildDokka") {
                prelude("val child = project.findProject(\":child\")!!")
                expect(
                    "CHILD_HAS_DOKKA_PLUGIN",
                    "child.pluginManager.hasPlugin(\"org.jetbrains.dokka-javadoc\")",
                    "true",
                )
                expect(
                    "CHILD_HAS_DOKKA_TASK",
                    "child.tasks.findByName(\"dokkaGeneratePublicationJavadoc\") != null",
                    "true",
                )
            }
        project.configure { stageDokkaProject(childProbe.script(), includeChild = true) }

        val result = project.build("verifyChildDokka", ":child:javadocJar")

        val childDokkaOutput = javadocJarContainsDokkaOutput(project.file("child/build/libs"))

        assertSoftly { softly ->
            childProbe.assertOutput(softly, result.output)
            softly.assertThat(childDokkaOutput).describedAs("child javadocJar contains real Dokka Javadoc pages").isTrue()
        }
    }

    @Test
    fun `is configuration cache compatible`() {
        project.configure { stageDokkaProject(kotlinProject = false) }

        val first = project.build("--configuration-cache", "help")
        val second = project.build("--configuration-cache", "help")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
        }
    }

    @Test
    fun `does not apply dokka when not opted in on settings extension`() {
        val dokkaProbe = noDokkaProbe()
        project.configure { stageDokkaProject(dokkaProbe.script(), optIn = false) }

        val result = project.build("verifyNoDokka")

        assertSoftly { softly ->
            dokkaProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `does not apply dokka when jvm defaults disabled`() {
        val dokkaProbe = noDokkaProbe()
        project.configure { stageDokkaProject(dokkaProbe.script(), jvmDefaultsEnabled = false) }

        val result = project.build("verifyNoDokka")

        assertSoftly { softly ->
            dokkaProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `does not apply dokka to java-only projects`() {
        val dokkaProbe = noDokkaProbe()
        project.configure { stageDokkaProject(dokkaProbe.script(), kotlinProject = false) }

        val result = project.build("verifyNoDokka")

        assertSoftly { softly ->
            dokkaProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `does not rewire a user-provided javadocJar`() {
        // CC disabled: this test runs no probe task, and KGP otherwise fails CC serialization in
        // TestKit (unrelated __buildFusService__ bug).
        project.withConfigurationCache(false)
        project.configure {
            file("custom-javadoc.txt", "user javadoc")
            stageDokkaProject(
                probeScript =
                    """
                    tasks.register<Jar>("javadocJar") {
                        archiveClassifier.set("javadoc")
                        from(layout.projectDirectory.file("custom-javadoc.txt"))
                    }
                    """.trimIndent(),
            )
        }

        project.build("javadocJar")

        val javadocJar =
            project
                .file("build/libs")
                .listFiles()
                .orEmpty()
                .single { it.name.endsWith("-javadoc.jar") }
        val entries =
            ZipFile(javadocJar).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            }

        assertSoftly { softly ->
            softly.assertThat(entries).contains("custom-javadoc.txt")
            softly.assertThat(entries).doesNotContain("dokka-javadoc-stylesheet.css")
        }
    }

    private fun javadocJarContainsDokkaOutput(libsDir: File): Boolean {
        val javadocJar = libsDir.listFiles().orEmpty().single { it.name.endsWith("-javadoc.jar") }
        return ZipFile(javadocJar).use { zip ->
            val names =
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            names.contains("dokka-javadoc-stylesheet.css") &&
                names.contains("index.html") &&
                names.contains("package-list") &&
                names.any { it.startsWith("com/example/") && it.endsWith(".html") }
        }
    }

    private fun noDokkaProbe() =
        probeTask("verifyNoDokka") {
            expect("HAS_DOKKA_PLUGIN", "project.pluginManager.hasPlugin(\"org.jetbrains.dokka-javadoc\")", "false")
            expect(
                "HAS_DOKKA_TASK",
                "project.tasks.findByName(\"dokkaGeneratePublicationJavadoc\") != null",
                "false",
            )
        }

    private fun GradleTestProject.stageDokkaProject(
        probeScript: String = "",
        jvmDefaultsEnabled: Boolean = true,
        optIn: Boolean = true,
        includeChild: Boolean = false,
        kotlinProject: Boolean = true,
    ) {
        val optInLine = if (optIn) "dokkaJavadoc()" else ""
        val childInclude = if (includeChild) "include(\"child\")" else ""
        val kotlinPlugin = if (kotlinProject) """id("org.jetbrains.kotlin.jvm")""" else ""
        settings(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$KGP_VERSION"
                }
            }
            plugins {
                id("com.mreil.easy.settings")
            }
            rootProject.name = "dokka-javadoc"
            extensions.configure<com.mreil.easy.EasyExtension>("easy") {
                extensions.configure<com.mreil.easy.jvm.EasyJvmDefaultsExtension>("jvmDefaults") {
                    enabled.set($jvmDefaultsEnabled)
                    $optInLine
                }
            }
            $childInclude
            """.trimIndent(),
        )
        file("codemeta.json", CODEMETA_JSON)
        if (kotlinProject) {
            file("src/main/kotlin/com/example/Placeholder.kt", kotlinSource("Placeholder"))
        }
        buildGradle(
            """
            buildscript {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            plugins {
                `java-library`
                $kotlinPlugin
            }
            repositories { mavenCentral() }
            $probeScript
            """.trimIndent(),
        )
        if (includeChild) {
            stageChildProject()
        }
    }

    private fun GradleTestProject.stageChildProject() {
        createChild {
            file("codemeta.json", CODEMETA_JSON)
            file("src/main/kotlin/com/example/ChildPlaceholder.kt", kotlinSource("ChildPlaceholder"))
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("org.jetbrains.kotlin.jvm")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
            )
        }
    }

    private fun kotlinSource(className: String): String =
        """
        package com.example

        /** A placeholder. */
        class $className
        """.trimIndent()

    private companion object {
        /** Pinned to the `kotlin` version in `gradle/libs.versions.toml`. */
        const val KGP_VERSION = "2.4.20"

        val CODEMETA_JSON =
            """
            {
              "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
              "@type": "SoftwareSourceCode",
              "name": "test",
              "description": "test",
              "version": "1.0.0"
            }
            """.trimIndent()
    }
}
