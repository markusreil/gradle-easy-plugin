package com.mreil.easy.codemeta

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.gitChangedFiles
import com.mreil.gradletest.project.initGitWithRemote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class CodemetaReleaseLifecycleFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `preReleaseCommit updates and commits codemeta version and dateModified`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            file("version.txt", "version=1.0.0-SNAPSHOT\n")
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "test",
                  "description": "test",
                  "version": "1.0.0-SNAPSHOT"
                }
                """.trimIndent(),
            )
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                import com.mreil.easy.release.EasyRelease

                plugins {
                    id("com.mreil.easy.test.codemeta")
                }

                version = "1.0.0-SNAPSHOT"

                easy {
                    codemeta { enabled.set(true) }
                    release {
                        enabled.set(true)
                        versionFile.set(layout.projectDirectory.file("version.txt"))
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("--configuration-cache", "preReleaseCommit")
        val committedFiles = gitChangedFiles(project)
        val codemetaContent = project.file("codemeta.json").readText()

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Configuration cache entry stored")
            softly.assertThat(result.output).doesNotContain("problems were found")
            softly.assertThat(codemetaContent).contains("\"version\" : \"1.0.0\"")
            softly.assertThat(codemetaContent).contains("\"dateModified\" : \"${LocalDate.now()}\"")
            softly.assertThat(committedFiles).contains("codemeta.json", "version.txt")
        }
    }

    @Test
    fun `preReleaseCommit leaves codemeta untouched when updateOnRelease disabled`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            file("version.txt", "version=1.0.0-SNAPSHOT\n")
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "test",
                  "description": "test",
                  "version": "1.0.0-SNAPSHOT"
                }
                """.trimIndent(),
            )
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.codemeta")
                }

                version = "1.0.0-SNAPSHOT"

                easy {
                    codemeta {
                        enabled.set(true)
                        updateOnRelease.set(false)
                    }
                    release {
                        enabled.set(true)
                        versionFile.set(layout.projectDirectory.file("version.txt"))
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("--configuration-cache", "preReleaseCommit")
        val committedFiles = gitChangedFiles(project)

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Configuration cache entry stored")
            softly.assertThat(project.file("codemeta.json").readText()).contains("1.0.0-SNAPSHOT")
            softly.assertThat(project.file("codemeta.json").readText()).doesNotContain("dateModified")
            softly.assertThat(committedFiles).doesNotContain("codemeta.json")
        }
    }
}
