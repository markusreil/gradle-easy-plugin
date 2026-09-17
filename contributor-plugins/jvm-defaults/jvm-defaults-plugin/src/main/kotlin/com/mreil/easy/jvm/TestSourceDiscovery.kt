package com.mreil.easy.jvm

import org.gradle.api.Project
import java.io.File

/** Kind of test suite discovered from a `src/<name>` directory. */
internal enum class TestSuiteType {
    /**
     * Gradle's built-in `test` suite; configured in place (framework + catalog test dependencies)
     * but never registered, ordered or wired by the plugin.
     */
    UNIT,

    /** A `*Test` suite registered and configured by the plugin. */
    FUNCTIONAL,
}

/** A test source tree discovered under `src/`, grouped by suite name and type. */
internal data class DiscoveredTestSuite(
    val name: String,
    val type: TestSuiteType,
    val sourceDirectories: List<File>,
)

/**
 * Finds the project's test suites by convention: `src/test/{java,kotlin}` (unit) and
 * `src/<name>Test/{java,kotlin}` (functional).
 *
 * Only suites with at least one existing language directory are returned, ordered by name.
 * Directories that match neither convention (`testFixtures`, `latest`, ...) are ignored.
 */
internal fun Project.findTestSuites(): List<DiscoveredTestSuite> =
    file(SRC_DIRECTORY)
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .sortedBy { it.name }
        .mapNotNull { directory ->
            classifyTestSuite(directory.name)?.let { type ->
                DiscoveredTestSuite(
                    name = directory.name,
                    type = type,
                    sourceDirectories = LANGUAGES.map { directory.resolve(it) }.filter { it.isDirectory },
                )
            }
        }.filter { it.sourceDirectories.isNotEmpty() }

private fun classifyTestSuite(name: String): TestSuiteType? =
    when {
        name == DEFAULT_UNIT_SUITE -> TestSuiteType.UNIT
        FUNCTIONAL_SUITE_NAME.matches(name) -> TestSuiteType.FUNCTIONAL
        else -> null
    }

/** Name of Gradle's built-in unit test suite. */
internal const val DEFAULT_UNIT_SUITE = "test"

private const val SRC_DIRECTORY = "src"
private val FUNCTIONAL_SUITE_NAME = Regex("[A-Za-z][A-Za-z0-9]*Test")
private val LANGUAGES = listOf("java", "kotlin")
