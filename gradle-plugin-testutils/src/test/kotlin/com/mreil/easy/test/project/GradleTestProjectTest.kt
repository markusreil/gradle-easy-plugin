package com.mreil.easy.test.project

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GradleTestProjectTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `root project stages empty settings by default`() {
        val project = GradleTestProject(File(tempDir, "root"))

        assertThat(project.pendingFilePaths).containsExactly("gradle.properties", "settings.gradle.kts")

        project.flushPendingFiles()

        assertThat(File(project.projectDir, "settings.gradle.kts").readText()).isEmpty()
        project.cleanup()
    }

    @Test
    fun `root project stages default group and version by default`() {
        val project = GradleTestProject(File(tempDir, "root"))

        project.flushPendingFiles()

        assertThat(File(project.projectDir, "gradle.properties").readText())
            .isEqualTo("group=com.example\nversion=1.0.0")
        project.cleanup()
    }

    @Test
    fun `explicit version overwrites the default`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.version = "2.0.0"

        project.flushPendingFiles()

        assertThat(File(project.projectDir, "gradle.properties").readText()).isEqualTo("group=com.example\nversion=2.0.0")
        project.cleanup()
    }

    @Test
    fun `null version omits the key`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.version = null

        project.flushPendingFiles()

        assertThat(File(project.projectDir, "gradle.properties").readText()).isEqualTo("group=com.example")
        project.cleanup()
    }

    @Test
    fun `explicit settings overwrite the default`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.settings("rootProject.name = \"root\"")

        project.flushPendingFiles()

        assertThat(File(project.projectDir, "settings.gradle.kts").readText()).isEqualTo("rootProject.name = \"root\"")
        project.cleanup()
    }

    @Test
    fun `file with content is lazy until flush`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val written = project.file("build.gradle.kts", "plugins { `java-library` }")

        assertSoftly { softly ->
            softly.assertThat(written.exists()).isFalse()
            softly
                .assertThat(
                    project.pendingFilePaths,
                ).containsExactlyInAnyOrder("settings.gradle.kts", "gradle.properties", "build.gradle.kts")
        }

        project.flushPendingFiles()

        assertSoftly { softly ->
            softly.assertThat(written).exists()
            softly.assertThat(written.readText()).isEqualTo("plugins { `java-library` }")
            softly.assertThat(project.pendingFilePaths).isEmpty()
        }
        project.cleanup()
    }

    @Test
    fun `last write wins for same path`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.file("build.gradle.kts", "first")
        project.file("build.gradle.kts", "second")

        project.flushPendingFiles()

        assertThat(File(project.projectDir, "build.gradle.kts").readText()).isEqualTo("second")
        project.cleanup()
    }

    @Test
    fun `single-arg file read flushes pending writes`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.file("build.gradle.kts", "plugins { `java-library` }")

        val readBack = project.file("build.gradle.kts").readText()

        assertThat(readBack).isEqualTo("plugins { `java-library` }")
        project.cleanup()
    }

    @Test
    fun `buildGradle writes to build file and nested file creates parent dirs`() {
        val project = GradleTestProject(File(tempDir, "root"))

        project.buildGradle("group = \"com.example\"")
        project.file("src/main/java/com/example/Root.java", "package com.example;")
        project.flushPendingFiles()

        assertSoftly { softly ->
            softly.assertThat(File(project.projectDir, "build.gradle.kts")).exists()
            softly.assertThat(File(project.projectDir, "src/main/java/com/example/Root.java")).exists()
        }
        project.cleanup()
    }

    @Test
    fun `settings writes settings file`() {
        val project = GradleTestProject(File(tempDir, "root"))

        project.settings("rootProject.name = \"root\"")
        project.flushPendingFiles()

        assertThat(File(project.projectDir, "settings.gradle.kts")).exists()
        project.cleanup()
    }

    @Test
    fun `configure applies block and returns same instance`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val result = project.configure { buildGradle("group = \"com.example\"") }
        project.flushPendingFiles()

        assertSoftly { softly ->
            softly.assertThat(result).isSameAs(project)
            softly.assertThat(File(project.projectDir, "build.gradle.kts")).exists()
        }
        project.cleanup()
    }

    @Test
    fun `child defaults to child name and caches instance`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val first = project.child()
        val second = project.child()

        assertSoftly { softly ->
            softly.assertThat(first.name).isEqualTo("child")
            softly.assertThat(second).isSameAs(first)
            softly.assertThat(first.projectDir).isEqualTo(File(project.projectDir, "child"))
            softly.assertThat(project.children).containsKey("child")
            softly.assertThat(project["child"]).isSameAs(first)
            softly.assertThat(project["missing"]).isNull()
        }
        project.cleanup()
    }

    @Test
    fun `child with custom name is nested under parent`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val custom = project.child("api")

        assertSoftly { softly ->
            softly.assertThat(custom.name).isEqualTo("api")
            softly.assertThat(custom.projectDir).isEqualTo(File(project.projectDir, "api"))
            softly.assertThat(project.children).containsKeys("api")
        }
        project.cleanup()
    }

    @Test
    fun `createChild configures child in one go`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val child =
            project.createChild {
                buildGradle("group = \"com.example\"")
                file("src/main/java/com/example/Child.java", "package com.example;")
            }

        assertThat(File(child.projectDir, "build.gradle.kts").exists()).isFalse()

        project.flushPendingFiles()

        assertSoftly { softly ->
            softly.assertThat(child.name).isEqualTo("child")
            softly.assertThat(File(child.projectDir, "build.gradle.kts")).exists()
            softly.assertThat(File(child.projectDir, "src/main/java/com/example/Child.java")).exists()
            softly.assertThat(project["child"]).isSameAs(child)
        }
        project.cleanup()
    }

    @Test
    fun `createDir creates directories eagerly`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val dir = project.createDir("repo/nested")

        assertSoftly { softly ->
            softly.assertThat(dir).exists()
            softly.assertThat(dir.isDirectory).isTrue()
        }
        project.cleanup()
    }

    @Test
    fun `javaSource stages default hello class lazily`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val source = project.javaSource()

        assertSoftly { softly ->
            softly.assertThat(source.exists()).isFalse()
            softly.assertThat(project.pendingFilePaths).contains("src/main/java/com/example/Hello.java")
        }

        project.flushPendingFiles()

        assertSoftly { softly ->
            softly.assertThat(source).exists()
            softly.assertThat(source.readText()).isEqualTo("package com.example;\npublic class Hello {}")
        }
        project.cleanup()
    }

    @Test
    fun `javaSource supports custom package and class name`() {
        val project = GradleTestProject(File(tempDir, "root"))

        project.javaSource("org.acme", "Root")
        project.flushPendingFiles()

        val source = File(project.projectDir, "src/main/java/org/acme/Root.java")
        assertSoftly { softly ->
            softly.assertThat(source).exists()
            softly.assertThat(source.readText()).isEqualTo("package org.acme;\npublic class Root {}")
        }
        project.cleanup()
    }

    @Test
    fun `kotlinSource stages class with default body lazily`() {
        val project = GradleTestProject(File(tempDir, "root"))

        val source = project.kotlinSource(className = "Hello")

        assertThat(source.exists()).isFalse()

        project.flushPendingFiles()

        assertSoftly { softly ->
            softly.assertThat(source).exists()
            softly.assertThat(source.readText()).isEqualTo("package com.example\nclass Hello")
        }
        project.cleanup()
    }

    @Test
    fun `kotlinSource supports custom body`() {
        val project = GradleTestProject(File(tempDir, "root"))

        project.kotlinSource(
            className = "MyPlugin",
            body = "class MyPlugin : Plugin<Project>",
        )
        project.flushPendingFiles()

        val source = File(project.projectDir, "src/main/kotlin/com/example/MyPlugin.kt")
        assertThat(source.readText()).isEqualTo("package com.example\nclass MyPlugin : Plugin<Project>")
        project.cleanup()
    }

    @Test
    fun `systemProperty is forwarded as dash-D runner argument`() {
        val project = GradleTestProject(File(tempDir, "root"))

        project.systemProperty("easy.disableAllPlugins", "true")

        assertThat(project.runnerArguments("verify", "--info"))
            .containsExactly("-Deasy.disableAllPlugins=true", "verify", "--info")
        project.cleanup()
    }

    @Test
    fun `runnerArguments without system properties passes tasks through`() {
        val project = GradleTestProject(File(tempDir, "root"))

        assertThat(project.runnerArguments("verify")).containsExactly("verify")
        project.cleanup()
    }

    @Test
    fun `cleanup deletes project directory`() {
        val project = GradleTestProject(File(tempDir, "root"))
        project.buildGradle("group = \"com.example\"")
        project.flushPendingFiles()
        assertThat(project.projectDir).exists()

        project.cleanup()

        assertThat(project.projectDir).doesNotExist()
    }

    @Test
    fun `cleanup removes child directories`() {
        val project = GradleTestProject(File(tempDir, "root"))
        val child = project.createChild { buildGradle("") }
        assertThat(child.projectDir).exists()

        project.cleanup()

        assertThat(project.projectDir).doesNotExist()
        assertThat(child.projectDir).doesNotExist()
    }
}

@ExtendWith(GradleTestProjectExtension::class)
class GradleTestProjectExtensionTest {
    lateinit var project: GradleTestProject

    @Test
    fun `field is injected before test`() {
        assertSoftly { softly ->
            softly.assertThat(::project.isInitialized).isTrue()
            softly.assertThat(project.projectDir).exists()
        }
    }

    @Test
    fun `parameter is resolved`(injected: GradleTestProject) {
        assertThat(injected.projectDir).exists()
    }
}
