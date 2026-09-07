package com.mreil.easy.publish.central

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates Maven POM files against the Maven Central metadata requirements.
 *
 * Pure function over files (no Gradle API) so it stays unit-testable and reusable
 * from [CheckCentralPomsTask]. Required: `name`, `description`, `url`, at least one
 * `license` with `name` + `url`, at least one `developer` with `name`, and `scm`
 * with `connection` + `developerConnection` + `url`. Blank values and `TODO`-prefixed
 * scaffolding placeholders (see `GenerateCodemetaTask`) count as missing. A missing
 * developer `email` is a warning only.
 */
object PomRequirementsChecker {
    data class PomViolation(
        val file: File,
        val errors: List<String>,
        val warnings: List<String>,
    )

    fun check(pomFile: File): PomViolation {
        val root = parse(pomFile).documentElement
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        checkText(root, "name")?.let(errors::add)
        checkText(root, "description")?.let(errors::add)
        checkText(root, "url")?.let(errors::add)
        checkLicenses(root, errors)
        checkDevelopers(root, errors, warnings)
        checkScm(root, errors)

        return PomViolation(pomFile, errors, warnings)
    }

    fun isMissing(value: String?): Boolean = value.isNullOrBlank() || value.contains("TODO")

    private fun parse(pomFile: File) =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(pomFile)
            .also { it.documentElement.normalize() }

    private fun directChildren(
        parent: Element,
        tag: String,
    ): List<Element> {
        val nodes = parent.childNodes
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tag }
    }

    private fun directText(
        parent: Element,
        tag: String,
    ): String? = directChildren(parent, tag).firstOrNull()?.textContent

    private fun checkText(
        parent: Element,
        tag: String,
    ): String? = if (isMissing(directText(parent, tag))) "missing <$tag>" else null

    private fun checkLicenses(
        root: Element,
        errors: MutableList<String>,
    ) {
        val licenses =
            directChildren(root, "licenses").flatMap { directChildren(it, "license") }
        if (licenses.isEmpty()) {
            errors.add("no <licenses> entries")
            return
        }
        licenses.forEachIndexed { index, license ->
            checkText(license, "name")?.let { errors.add("license[$index] $it") }
            checkText(license, "url")?.let { errors.add("license[$index] $it") }
        }
    }

    private fun checkDevelopers(
        root: Element,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val developers =
            directChildren(root, "developers").flatMap { directChildren(it, "developer") }
        if (developers.isEmpty()) {
            errors.add("no <developers> entries")
            return
        }
        developers.forEachIndexed { index, developer ->
            val name = directText(developer, "name")
            if (isMissing(name)) {
                errors.add("developer[$index] missing <name>")
            } else if (isMissing(directText(developer, "email"))) {
                warnings.add("developer '$name' has no <email>")
            }
        }
    }

    private fun checkScm(
        root: Element,
        errors: MutableList<String>,
    ) {
        val scm = directChildren(root, "scm").firstOrNull()
        if (scm == null) {
            errors.add("missing <scm>")
            return
        }
        checkText(scm, "connection")?.let { errors.add("scm $it") }
        checkText(scm, "developerConnection")?.let { errors.add("scm $it") }
        checkText(scm, "url")?.let { errors.add("scm $it") }
    }
}
