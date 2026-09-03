package com.mreil.easy.test.project

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files

/**
 * Root of a throwaway multi-project Gradle build used by functional tests.
 *
 * Typical lifecycle: declare everything in one [configure] block, then run [build] or [buildAndFail].
 * An empty `settings.gradle.kts` and a `gradle.properties` with `group=com.example` / `version=1.0.0`
 * are staged by default; declaring [settings] or [gradleProperties][TestProject.gradleProperties]
 * explicitly overwrites them. The properties apply to all projects of the test build.
 * Staged files are written to disk lazily when the build runs.
 *
 * Child projects are created with [createChild] (default name `"child"`) and behave like the root,
 * except builds always run from here. System properties for the TestKit child JVM are staged
 * with [systemProperty].
 */
class GradleTestProject(
    override val projectDir: File = Files.createTempDirectory("gradle-test-").toFile(),
) : TestProject() {
    init {
        settings("")
    }

    private val childProjects = mutableMapOf<String, ChildGradleTestProject>()

    private val systemProperties = mutableMapOf<String, String>()

    /** Previously created children by name, in creation order. */
    internal val children: Map<String, ChildGradleTestProject> get() = childProjects

    /** Returns the cached child [name], creating its directory on first access. */
    internal fun child(name: String = "child"): ChildGradleTestProject = childProjects.getOrPut(name) { ChildGradleTestProject(this, name) }

    /**
     * Creates (or reuses) the child [name] and applies [configure] to it in one go,
     * e.g. `createChild { buildGradle(...) }` for the default `"child"` project.
     */
    fun createChild(
        name: String = "child",
        configure: ChildGradleTestProject.() -> Unit = {},
    ): ChildGradleTestProject = child(name).apply(configure)

    /** Returns the child [name], or null if it was never created. */
    internal operator fun get(name: String): ChildGradleTestProject? = childProjects[name]

    /** Stages `settings.gradle.kts` with [content], overwriting the default empty one. */
    fun settings(content: String): File = file("settings.gradle.kts", content)

    /** Writes this project's staged files plus every child's. */
    internal override fun flushPendingFiles() {
        super.flushPendingFiles()
        childProjects.values.forEach { it.flushPendingFiles() }
    }

    /** Applies [block] to this project for one-shot setup and returns this project. */
    fun configure(block: GradleTestProject.() -> Unit): GradleTestProject {
        block()
        return this
    }

    /**
     * Stages a `-Dkey=value` argument forwarded to the TestKit child JVM,
     * e.g. `systemProperty("easy.disableAllPlugins", "true")`.
     */
    fun systemProperty(
        key: String,
        value: String,
    ): GradleTestProject = apply { systemProperties[key] = value }

    /** CLI arguments for [runner]: staged system properties first, then [tasks]. */
    internal fun runnerArguments(vararg tasks: String): List<String> =
        systemProperties.map { (key, value) -> "-D$key=$value" } + tasks.toList()

    /** Builds a TestKit runner for [tasks] after flushing all staged files. */
    fun runner(vararg tasks: String): GradleRunner {
        flushPendingFiles()
        return GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*runnerArguments(*tasks).toTypedArray())
            .forwardOutput()
    }

    /** Runs [tasks] expecting success. */
    fun build(vararg tasks: String): BuildResult = runner(*tasks).build()

    /** Runs [tasks] expecting failure. */
    fun buildAndFail(vararg tasks: String): BuildResult = runner(*tasks).buildAndFail()
}
