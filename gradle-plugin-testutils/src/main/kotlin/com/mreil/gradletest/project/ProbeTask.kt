package com.mreil.gradletest.project

import org.assertj.core.api.SoftAssertions

/**
 * A probe task declared for a test build: registers `tasks.register(name)` printing
 * `KEY=value` lines, with the expected values asserted afterwards.
 *
 * ```kotlin
 * val publishProbe = probeTask("verifyPublish") {
 *     expect("HAS_PUBLISH", "tasks.findByName(\"publish\") != null", "true")
 * }
 * project.configure {
 *     buildGradle(
 *         """
 *         plugins { `java-library` }
 *         ${publishProbe.script()}
 *         """.trimIndent(),
 *     )
 * }
 * val result = project.build("verifyPublish")
 * assertSoftly { softly -> publishProbe.assertOutput(softly, result.output) }
 * ```
 *
 * The generated block is relative to column zero on purpose: interpolated at any
 * indentation inside a `trimIndent()` string the surrounding script stays valid.
 */
class ProbeTask(
    val name: String,
) {
    private data class Probe(
        val expression: String,
        val value: String,
        val absent: Boolean = false,
    )

    private val probes = linkedMapOf<String, Probe>()
    private val preludes = mutableListOf<String>()

    /** Adds raw [lines] at the top of the task action, e.g. shared `val` lookups for probes. */
    fun prelude(vararg lines: String) {
        preludes.addAll(lines)
    }

    /** Expects the probe [expression] to print `KEY=value` when the task runs. */
    fun expect(
        key: String,
        expression: String,
        value: String,
    ) {
        probes[key] = Probe(expression, value)
    }

    /** Expects `KEY=value` to be absent from the output when the task runs. */
    fun expectAbsent(
        key: String,
        expression: String,
        value: String,
    ) {
        probes[key] = Probe(expression, value, absent = true)
    }

    /**
     * Expects a task named [task] to exist (or not, when [expected] is false).
     * Use [inProject] for a subproject path like `":child"`, e.g.
     * `taskExists("CHILD_HAS_TASK", "generateJreleaserConfig", expected = false, inProject = ":child")`.
     */
    fun taskExists(
        key: String,
        task: String,
        expected: Boolean = true,
        inProject: String? = null,
    ) {
        val lookup = inProject?.let { "project.findProject(\"$it\")!!.tasks" } ?: "tasks"
        expect(key, "$lookup.findByName(\"$task\") != null", expected.toString())
    }

    /** Expects an extension named [name] to exist (or not) on the project. */
    fun extensionExists(
        key: String,
        name: String,
        expected: Boolean = true,
    ) {
        expect(key, "project.extensions.findByName(\"$name\") != null", expected.toString())
    }

    /**
     * Expects an extension of [type] (fully qualified class name) to exist (or not) on the project,
     * e.g. `extensionExistsByType("HAS_EASY", "com.mreil.easy.EasyExtension")`.
     */
    fun extensionExistsByType(
        key: String,
        type: String,
        expected: Boolean = true,
    ) {
        expect(key, "project.extensions.findByType($type::class.java) != null", expected.toString())
    }

    /** Renders the `tasks.register` block for embedding in a build script. */
    fun script(): String =
        buildString {
            appendLine("tasks.register(\"$name\") {")
            appendLine("    notCompatibleWithConfigurationCache(\"Probe task uses Task.project at execution time\")")
            appendLine("    doLast {")
            preludes.forEach { line ->
                appendLine("        $line")
            }
            probes.forEach { (key, probe) ->
                appendLine("        println(\"$key=\" + (${probe.expression}))")
            }
            appendLine("    }")
            append("}")
        }

    /**
     * Asserts every expected `KEY=value` line is contained in [output] and every absent one
     * is missing, collecting failures.
     */
    fun assertOutput(
        softly: SoftAssertions,
        output: String,
    ) {
        probes.forEach { (key, probe) ->
            if (probe.absent) {
                softly.assertThat(output).doesNotContain("$key=${probe.value}")
            } else {
                softly.assertThat(output).contains("$key=${probe.value}")
            }
        }
    }
}

/** Declares a [ProbeTask] with [block] configuring its expectations. */
fun probeTask(
    name: String,
    block: ProbeTask.() -> Unit,
): ProbeTask = ProbeTask(name).apply(block)
