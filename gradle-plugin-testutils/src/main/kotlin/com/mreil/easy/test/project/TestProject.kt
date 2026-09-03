package com.mreil.easy.test.project

import com.mreil.easy.test.project.assertj.MavenCoordinates
import com.mreil.easy.test.project.template.FileTemplate
import com.mreil.easy.test.project.template.GradlePropertiesTemplate
import com.mreil.easy.test.project.template.RawStringTemplate
import java.io.File

/**
 * A throwaway Gradle project on disk used by functional tests.
 *
 * File contents declared via [file], [buildGradle], [settings][GradleTestProject.settings],
 * [javaSource] and [kotlinSource] are staged in memory as [FileTemplate]s and written to
 * [projectDir] lazily: on the next [flushPendingFiles], on any resolving [file] read, or when
 * a build runs. Declaring the same path twice keeps the last content.
 *
 * The `gradle.properties` coordinates are managed via [group] and [version] instead of raw
 * file content; a `null` value omits the key. They apply to all projects of the test build.
 */
abstract class TestProject {
    /** Root directory of this project. Created eagerly for the root project, on demand for children. */
    abstract val projectDir: File

    private val pendingFiles = mutableMapOf<String, FileTemplate>()

    init {
        pendingFiles["gradle.properties"] = GradlePropertiesTemplate()
    }

    /**
     * Project group, staged into `gradle.properties`. `null` omits the key.
     * @throws IllegalStateException if `gradle.properties` was replaced with raw content.
     */
    var group: String?
        get() = propertiesTemplate.group
        set(value) {
            propertiesTemplate.group = value
        }

    /**
     * Project version, staged into `gradle.properties`. `null` omits the key.
     * @throws IllegalStateException if `gradle.properties` was replaced with raw content.
     */
    var version: String?
        get() = propertiesTemplate.version
        set(value) {
            propertiesTemplate.version = value
        }

    private val propertiesTemplate: GradlePropertiesTemplate
        get() =
            pendingFiles["gradle.properties"] as? GradlePropertiesTemplate
                ?: error("gradle.properties holds raw content, use group/version before file()")

    /** Paths staged via [file] but not yet written to disk. */
    internal val pendingFilePaths: Set<String> get() = pendingFiles.keys.toSet()

    /**
     * Resolves a handle for [child] without creating anything.
     * Flushes staged files first so reads observe previously declared contents.
     */
    fun file(child: String): File {
        flushPendingFiles()
        return File(projectDir, child)
    }

    /** Stages [content] for [path]; nothing is written until flush. Returns the future handle. */
    fun file(
        path: String,
        content: String,
    ): File {
        pendingFiles[path] = RawStringTemplate(content)
        return File(projectDir, path)
    }

    /** Stages a `build.gradle.kts` with [content]. */
    fun buildGradle(content: String): File = file("build.gradle.kts", content)

    /** Creates the directory at [path] eagerly (including parents) and returns its handle. */
    fun createDir(path: String): File = File(projectDir, path).apply { mkdirs() }

    /**
     * Stages a Java source file `src/main/java/<package>/<Class>.java`
     * with a `package` statement and an empty public class body.
     */
    fun javaSource(
        packageName: String = "com.example",
        className: String = "Hello",
    ): File =
        file(
            "src/main/java/${packageName.replace('.', '/')}/$className.java",
            """
            package $packageName;
            public class $className {}
            """.trimIndent(),
        )

    /**
     * Stages a Kotlin source file `src/main/kotlin/<package>/<Class>.kt`
     * with a `package` statement followed by [body].
     */
    fun kotlinSource(
        packageName: String = "com.example",
        className: String,
        body: String = "class $className",
    ): File =
        file(
            "src/main/kotlin/${packageName.replace('.', '/')}/$className.kt",
            """
            package $packageName
            $body
            """.trimIndent(),
        )

    /** Writes all staged files to disk and clears the staging area. */
    internal open fun flushPendingFiles() {
        for ((path, template) in pendingFiles) {
            File(projectDir, path).apply {
                parentFile.mkdirs()
                writeText(template.render())
            }
        }
        pendingFiles.clear()
    }

    /**
     * Resolves the Maven repository path of a published artifact,
     * e.g. `mavenArtifact(repo, MavenCoordinates("com.example", "demo", "1.0.0"))` →
     * `<repo>/com/example/demo/1.0.0/demo-1.0.0.jar`.
     */
    fun mavenArtifact(
        repo: File,
        coordinates: MavenCoordinates,
    ): File {
        val fileName =
            buildString {
                append("${coordinates.name}-${coordinates.version}")
                if (coordinates.classifier != null) append("-${coordinates.classifier}")
                append(".${coordinates.extension}")
            }
        return File(repo, "${coordinates.group.replace('.', '/')}/${coordinates.name}/${coordinates.version}/$fileName")
    }

    /**
     * Resolves the `maven-metadata.xml` path of a published module,
     * e.g. `<repo>/com/example/demo/1.0.0/maven-metadata.xml`.
     */
    fun mavenMetadata(
        repo: File,
        coordinates: MavenCoordinates,
    ): File = File(repo, "${coordinates.group.replace('.', '/')}/${coordinates.name}/${coordinates.version}/maven-metadata.xml")

    /** Deletes [projectDir] if it exists. Called automatically by [GradleTestProjectExtension]. */
    internal fun cleanup() {
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
    }
}
