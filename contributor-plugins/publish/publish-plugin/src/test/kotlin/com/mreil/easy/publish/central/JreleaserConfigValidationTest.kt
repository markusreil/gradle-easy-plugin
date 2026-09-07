package com.mreil.easy.publish.central

import com.mreil.easy.publish.central.JreleaserYaml.Config
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.jreleaser.cli.Main
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Validates generated configs with the real JReleaser model (`config --deploy`,
 * default non-strict mode mirroring [JreleaserPublishTask]).
 *
 * Runs in-process via [Main.run] (returns an exit code, never `System.exit`).
 * No remotes are touched; the `org.jreleaser:jreleaser` test dependency resolves
 * once from Maven Central and is cached afterwards.
 */
class JreleaserConfigValidationTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `central config passes jreleaser validation`() {
        validate(JreleaserYaml.buildYaml(centralConfig()))
    }

    @Test
    fun `nexus config passes jreleaser validation`() {
        validate(
            JreleaserYaml.buildYaml(
                centralConfig().copy(
                    nexusUrl = "http://localhost:8081/service/rest/v1/components?repository=maven-releases",
                    nexusUsername = "admin",
                    nexusPassword = "admin123",
                ),
            ),
        )
    }

    /**
     * Multi-module smoke proof: every module stages into its own dir and JReleaser
     * accepts the whole collection in `stagingRepositories` (see `deployersFor`).
     */
    @Test
    fun `multi-dir staging config passes jreleaser validation`() {
        val rootStaging = File(tempDir, "root-staging").apply { mkdirs() }
        val childStaging = File(tempDir, "child-staging").apply { mkdirs() }
        val yaml =
            JreleaserYaml.buildYaml(
                centralConfig().copy(stagingDirs = listOf(rootStaging.absolutePath, childStaging.absolutePath)),
            )

        assertSoftly { softly ->
            softly.assertThat(yaml).contains(rootStaging.absolutePath)
            softly.assertThat(yaml).contains(childStaging.absolutePath)
        }
        validate(yaml)
    }

    private fun validate(yaml: String) {
        initGitRepo()
        val configFile = File(tempDir, "jreleaser.yml").apply { writeText(yaml) }
        val settingsFile = File(tempDir, "settings.properties").apply { writeText("") }
        val outDir = File(tempDir, "out").apply { mkdirs() }

        val out = StringWriter()
        val err = StringWriter()
        val exitCode =
            Main.run(
                PrintWriter(out),
                PrintWriter(err),
                "config",
                "--deploy",
                "-b",
                tempDir.absolutePath,
                "-c",
                configFile.absolutePath,
                "--output-directory",
                outDir.absolutePath,
                "--settings-file",
                settingsFile.absolutePath,
            )

        assertSoftly { softly ->
            val trace =
                File(outDir, "jreleaser/trace.log")
                    .takeIf { it.isFile }
                    ?.readText()
                    ?.lines()
                    ?.takeLast(40)
                    ?.joinToString("\n")
            softly
                .assertThat(exitCode)
                .withFailMessage { "JReleaser validation failed.\nstdout:\n$out\nstderr:\n$err\ntrace:\n$trace" }
                .isZero()
        }
    }

    /**
     * JReleaser resolves git HEAD and the `origin` remote during context creation,
     * so the fixture basedir must be a repo with at least one commit. Initialized
     * via JGit (already on the test classpath transitively) - no `git` binary or
     * surrounding checkout needed. The remote is never contacted.
     */
    private fun initGitRepo() {
        Git.init().setDirectory(tempDir).call().use { git ->
            File(tempDir, "README.md").writeText("fixture")
            git.add().addFilepattern("README.md").call()
            git
                .commit()
                .setMessage("init")
                .setAuthor("Test", "test@example.com")
                .setCommitter("Test", "test@example.com")
                .call()
            git
                .remoteAdd()
                .setName("origin")
                .setUri(URIish("https://example.com/demo.git"))
                .call()
        }
    }

    private fun centralConfig(): Config =
        Config(
            projectName = "demo",
            projectVersion = "1.0.0",
            projectGroupId = "com.example",
            // Point at a real (empty) dir in case validation requires staging to exist.
            stagingDirs = listOf(File(tempDir, "staging").absolutePath),
            mavenCentralUsername = "dummy-mavencentral-username",
            mavenCentralPassword = "dummy-mavencentral-password",
        )
}
