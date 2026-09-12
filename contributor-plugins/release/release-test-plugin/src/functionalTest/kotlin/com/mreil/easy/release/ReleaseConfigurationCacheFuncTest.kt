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

/**
 * Configuration-cache compatibility test for the `release` contributor.
 *
 * The release path shells out to `git` (`push`, `tag`, `add`/`commit`, `reset`) through
 * `providers.exec`, which must stay lazy across configuration time. Any regression that
 * starts an external process at configuration time (e.g. raw `ProcessBuilder`, or a VCS
 * provider realized eagerly) emits "external process was started" / "problems were found"
 * CC warnings — and would have shipped, since the VCS plugin's `git rev-parse @{u}` calls
 * previously broke CC and were only caught after release.
 *
 * `preReleaseCheck` is exercised twice (idempotent — same task on the same project, just
 * like the VCS test does with `vcsStatus`); the full `release` chain exercises the
 * push/tag/commit paths and asserts the cache entry is stored on first run.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class ReleaseConfigurationCacheFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `preReleaseCheck configuration cache stores and reuses without external process problems`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val first = project.build("--configuration-cache", "preReleaseCheck")
        val second = project.build("--configuration-cache", "preReleaseCheck")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(first.output).doesNotContain("external process")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
            softly.assertThat(second.output).doesNotContain("external process")
        }
    }

    @Test
    fun `release chain configuration cache stores without external process problems`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val first = project.build("--configuration-cache", "release")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(first.output).doesNotContain("external process")
        }
    }

    private fun initGitWithRemote(project: GradleTestProject): File {
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
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed with exit code $process.exitValue()" }
    }
}
