package com.mreil.gradletest.project.assertj

import com.mreil.gradletest.project.GradleTestProject
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TestPomAssertTest {
    @TempDir
    lateinit var tempDir: File

    private fun writePom(
        name: String = "demo-1.0.0.pom",
        content: String = markerPom,
    ): File =
        File(tempDir, name).apply {
            parentFile.mkdirs()
            writeText(content.trimIndent())
        }

    @Test
    fun `hasPom returns pom assert for existing pom`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")
        val coordinates = MavenCoordinates(name = "demo")
        project.mavenArtifact(repo, coordinates.copy(extension = "pom")).apply {
            parentFile.mkdirs()
            writeText(markerPom)
        }

        assertSoftly { softly ->
            softly
                .assertThat(project)
                .hasPom(repo, coordinates)
                .hasGroupId("com.example")
                .hasArtifactId("demo")
                .hasVersion("1.0.0")
        }

        project.cleanup()
    }

    @Test
    fun `hasPom fails for missing pom`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")
        val coordinates = MavenCoordinates(name = "demo")

        assertThatThrownBy {
            assertSoftly { softly ->
                softly.assertThat(project).hasPom(repo, coordinates)
            }
        }.hasMessageContaining("to have pom")

        project.cleanup()
    }

    @Test
    fun `coordinate assertions fail with expected and actual values`() {
        val pom = writePom()

        assertThatThrownBy {
            PomAssert(pom).hasGroupId("org.other")
        }.hasMessageContaining("groupId").hasMessageContaining("org.other").hasMessageContaining("com.example")

        assertThatThrownBy {
            PomAssert(pom).hasArtifactId("other")
        }.hasMessageContaining("artifactId")

        assertThatThrownBy {
            PomAssert(pom).hasVersion("2.0.0")
        }.hasMessageContaining("version")
    }

    @Test
    fun `hasDependency matches dependency triple`() {
        val pom = writePom()

        PomAssert(pom).hasDependency("com.example", "demo-lib", "1.0.0")

        assertThatThrownBy {
            PomAssert(pom).hasDependency("com.example", "demo-lib", "2.0.0")
        }.hasMessageContaining("dependency").hasMessageContaining("com.example:demo-lib:2.0.0")
    }

    @Test
    fun `hasText checks raw pom content`() {
        val pom = writePom()

        PomAssert(pom).hasText("<packaging>jar</packaging>")

        assertThatThrownBy {
            PomAssert(pom).hasText("<packaging>war</packaging>")
        }.hasMessageContaining("to contain")
    }

    @Test
    fun `malformed xml fails with parse message`() {
        val pom = writePom(content = "not xml <oops>")

        assertThatThrownBy {
            PomAssert(pom).hasGroupId("com.example")
        }.hasMessageContaining("well-formed XML")
    }

    @Test
    fun `missing file fails with exists message`() {
        val pom = File(tempDir, "missing.pom")

        assertThatThrownBy {
            PomAssert(pom).hasGroupId("com.example")
        }.hasMessageContaining("to exist")
    }

    @Test
    fun `soft chain collects pom failures together`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val repo = project.createDir("repo")
        val coordinates = MavenCoordinates(name = "demo")
        project.mavenArtifact(repo, coordinates.copy(extension = "pom")).apply {
            parentFile.mkdirs()
            writeText(markerPom)
        }
        project.mavenArtifact(repo, coordinates).apply {
            parentFile.mkdirs()
            writeText("fake jar")
        }

        assertThatThrownBy {
            assertSoftly { softly ->
                softly
                    .assertThat(project)
                    .hasPom(repo, coordinates)
                    .hasGroupId("org.other")
                softly.assertThat(project).doesNotHaveArtifact(repo, coordinates)
            }
        }.hasMessageContaining("groupId").hasMessageContaining("not to have artifact")

        project.cleanup()
    }

    companion object {
        private val markerPom =
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
              <packaging>jar</packaging>
              <dependencies>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>demo-lib</artifactId>
                  <version>1.0.0</version>
                </dependency>
              </dependencies>
            </project>
            """.trimIndent()
    }
}
