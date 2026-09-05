package com.mreil.gradletest.project.assertj

import com.mreil.gradletest.project.GradleTestProject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TestProjectAssertTest {
    @TempDir
    lateinit var tempDir: File

    private val coordinates = MavenCoordinates(name = "demo")

    @Test
    fun `coordinates default to example group and release version`() {
        assertThat(MavenCoordinates(name = "demo")).isEqualTo(MavenCoordinates("com.example", "demo", "1.0.0"))
    }

    @Test
    fun `mavenArtifact maps coordinates to repository path`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val artifact = project.mavenArtifact(File("repo"), coordinates)

        assertThat(artifact.invariantSeparatorsPath).endsWith("repo/com/example/demo/1.0.0/demo-1.0.0.jar")
        project.cleanup()
    }

    @Test
    fun `mavenArtifact supports classifier and custom extension`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val coords = MavenCoordinates(name = "demo", classifier = "sources", extension = "pom")

        val artifact = project.mavenArtifact(File("repo"), coords)

        assertThat(artifact.invariantSeparatorsPath).endsWith("repo/com/example/demo/1.0.0/demo-1.0.0-sources.pom")
        project.cleanup()
    }

    @Test
    fun `hasArtifact passes for existing release artifact`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")
        project.mavenArtifact(repo, coordinates).apply {
            parentFile.mkdirs()
            writeText("fake")
        }

        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(repo, coordinates)
        }

        project.cleanup()
    }

    @Test
    fun `hasArtifact fails with coordinates for missing artifact`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")

        assertThatThrownBy {
            assertSoftly { softly ->
                softly.assertThat(project).hasArtifact(repo, coordinates)
            }
        }.hasMessageContaining("MavenCoordinates")

        project.cleanup()
    }

    @Test
    fun `doesNotHaveArtifact passes when absent and fails when present`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")

        assertSoftly { softly ->
            softly.assertThat(project).doesNotHaveArtifact(repo, coordinates)
        }

        project.mavenArtifact(repo, coordinates).apply {
            parentFile.mkdirs()
            writeText("fake")
        }

        assertThatThrownBy {
            assertSoftly { softly ->
                softly.assertThat(project).doesNotHaveArtifact(repo, coordinates)
            }
        }.hasMessageContaining("not to have artifact")

        project.cleanup()
    }

    @Test
    fun `hasArtifact scans timestamped snapshot jars`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")
        val snapshot = MavenCoordinates(name = "demo", version = "1.0.0-SNAPSHOT")
        val versionDir = File(repo, "com/example/demo/1.0.0-SNAPSHOT").apply { mkdirs() }
        File(versionDir, "demo-1.0.0-20240101.120000-7.jar").writeText("fake")

        assertSoftly { softly ->
            softly.assertThat(project).hasArtifact(repo, snapshot)
        }

        project.cleanup()
    }

    @Test
    fun `hasArtifact fails for snapshot without matching jar`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")
        val snapshot = MavenCoordinates(name = "demo", version = "1.0.0-SNAPSHOT")
        val versionDir = File(repo, "com/example/demo/1.0.0-SNAPSHOT").apply { mkdirs() }
        File(versionDir, "other-1.0.0-20240101.120000-7.jar").writeText("fake")

        assertThatThrownBy {
            assertSoftly { softly ->
                softly.assertThat(project).hasArtifact(repo, snapshot)
            }
        }.hasMessageContaining("1.0.0-SNAPSHOT")

        project.cleanup()
    }

    @Test
    fun `maven metadata assertions check metadata file`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")

        assertSoftly { softly ->
            softly.assertThat(project).doesNotHaveMavenMetadata(repo, coordinates)
        }

        project.mavenMetadata(repo, coordinates).apply {
            parentFile.mkdirs()
            writeText("<metadata/>")
        }

        assertSoftly { softly ->
            softly.assertThat(project).hasMavenMetadata(repo, coordinates)
        }
        assertThatThrownBy {
            assertSoftly { softly ->
                softly.assertThat(project).doesNotHaveMavenMetadata(repo, coordinates)
            }
        }.hasMessageContaining("maven-metadata.xml")

        project.cleanup()
    }

    @Test
    fun `soft assertions collect project failures without throwing`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")

        assertThatThrownBy {
            assertSoftly { softly ->
                softly.assertThat(project).hasArtifact(repo, coordinates)
                softly.assertThat("plain").isEqualTo("plain")
            }
        }.hasMessageContaining("MavenCoordinates")

        project.cleanup()
    }

    @Test
    fun `soft assertions pass standard and project assertions together`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")

        assertSoftly { softly ->
            softly.assertThat(project).doesNotHaveArtifact(repo, coordinates)
            softly.assertThat("plain").isEqualTo("plain")
        }

        project.cleanup()
    }
}
