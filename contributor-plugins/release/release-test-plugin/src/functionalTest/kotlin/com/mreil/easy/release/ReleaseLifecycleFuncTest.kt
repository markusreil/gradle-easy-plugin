package com.mreil.easy.release

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.nio.file.Files

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class ReleaseLifecycleFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `beforePreReleaseCommit fires with resolved version under configuration cache`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            file("version.txt", "version=1.0.0-SNAPSHOT\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                import com.mreil.easy.release.EasyRelease

                plugins {
                    id("com.mreil.easy.test.release")
                }

                version = "1.0.0-SNAPSHOT"

                easy {
                    release {
                        enabled.set(true)
                        versionFile.set(layout.projectDirectory.file("version.txt"))
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }

                val markerFile = layout.projectDirectory.file("lifecycle-marker.txt").asFile
                EasyRelease.beforePreReleaseCommit(
                    project,
                    EasyRelease.writeToFileListener(markerFile, prefix = "before="),
                )
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val first = project.build("--configuration-cache", "preReleaseCommit")
        val second = project.build("--configuration-cache", "preReleaseCommit")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(first.output).doesNotContain("external process")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
            softly.assertThat(second.output).doesNotContain("external process")
            softly.assertThat(project.file("lifecycle-marker.txt").readText()).isEqualTo("before=1.0.0")
        }
    }

    @Test
    fun `multiple listeners contribute files to the same preReleaseCommit`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            file("version.txt", "version=1.0.0-SNAPSHOT\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                import com.mreil.easy.release.EasyRelease

                plugins {
                    id("com.mreil.easy.test.release")
                }

                version = "1.0.0-SNAPSHOT"

                easy {
                    release {
                        enabled.set(true)
                        versionFile.set(layout.projectDirectory.file("version.txt"))
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }

                val markerFile = layout.projectDirectory.file("lifecycle-marker.txt").asFile
                val extraFile = layout.projectDirectory.file("extra-marker.txt").asFile
                EasyRelease.beforePreReleaseCommit(
                    project,
                    EasyRelease.writeToFileListener(markerFile, prefix = "before="),
                )
                EasyRelease.beforePreReleaseCommit(
                    project,
                    EasyRelease.writeToFileListener(extraFile, prefix = "extra="),
                )
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("--configuration-cache", "preReleaseCommit")
        val committedFiles = gitLogNameOnly(project.projectDir.absolutePath)

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Configuration cache entry stored")
            softly.assertThat(result.output).doesNotContain("problems were found")
            softly.assertThat(result.output).doesNotContain("external process")
            softly.assertThat(project.file("lifecycle-marker.txt").readText()).isEqualTo("before=1.0.0")
            softly.assertThat(project.file("extra-marker.txt").readText()).isEqualTo("extra=1.0.0")
            softly.assertThat(committedFiles).contains("lifecycle-marker.txt", "extra-marker.txt", "version.txt")
        }
    }

    private fun gitLogNameOnly(workDir: String): List<String> {
        val process =
            ProcessBuilder("git", "log", "-1", "--name-only", "--format=")
                .directory(File(workDir))
                .redirectErrorStream(true)
                .start()
        check(process.waitFor() == 0) { "git log failed with exit code ${process.exitValue()}" }
        return process.inputStream
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
    }

    private fun initGitWithRemote(project: GradleTestProject): File {
        project.file("build.gradle.kts")
        runGit(project.projectDir.absolutePath, "init", "-b", "main")
        runGit(project.projectDir.absolutePath, "config", "user.email", "test@example.com")
        runGit(project.projectDir.absolutePath, "config", "user.name", "Test")
        runGit(project.projectDir.absolutePath, "add", "-A")
        runGit(project.projectDir.absolutePath, "commit", "-m", "initial")
        val remoteDir = Files.createTempDirectory("git-remote-").toFile()
        runGit(remoteDir.absolutePath, "init", "--bare", "-b", "main")
        runGit(project.projectDir.absolutePath, "remote", "add", "origin", remoteDir.absolutePath)
        runGit(project.projectDir.absolutePath, "push", "-u", "origin", "main")
        return remoteDir
    }

    private fun runGit(
        workDir: String,
        vararg args: String,
    ) {
        val process =
            ProcessBuilder(listOf("git") + args.toList())
                .directory(File(workDir))
                .redirectErrorStream(true)
                .start()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed with exit code ${process.exitValue()}" }
    }
}
