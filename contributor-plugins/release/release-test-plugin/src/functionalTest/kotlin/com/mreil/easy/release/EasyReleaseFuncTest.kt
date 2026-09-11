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
class EasyReleaseFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `release succeeds without vcs`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun `release succeeds on clean git tree up to date with remote`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
        }
    }

    private fun initGitWithRemote(project: GradleTestProject) {
        project.file("build.gradle.kts")
        runGit(project.projectDir.absolutePath, "init", "-b", "main")
        runGit(project.projectDir.absolutePath, "config", "user.email", "test@example.com")
        runGit(project.projectDir.absolutePath, "config", "user.name", "Test")
        runGit(project.projectDir.absolutePath, "add", "-A")
        runGit(project.projectDir.absolutePath, "commit", "-m", "initial")
        val remoteDir = Files.createTempDirectory("git-remote-").toFile()
        runGit(remoteDir.absolutePath, "init", "--bare")
        runGit(project.projectDir.absolutePath, "remote", "add", "origin", remoteDir.absolutePath)
        runGit(project.projectDir.absolutePath, "push", "-u", "origin", "main")
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
        val exitCode = process.waitFor()
        check(exitCode == 0) { "git ${args.joinToString(" ")} failed with exit code $exitCode" }
    }
}
