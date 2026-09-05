package com.mreil.gradletest.project.assertj

import org.assertj.core.api.AbstractAssert
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * AssertJ assertions for a Maven pom file, parsed as XML.
 * The class and its methods are `open` so soft assertions can proxy them.
 */
@Suppress("TooGenericExceptionCaught")
open class PomAssert(
    val pomFile: File,
) : AbstractAssert<PomAssert, File>(pomFile, PomAssert::class.java) {
    /** Asserts the `/project/groupId` element equals [expected]. */
    open fun hasGroupId(expected: String): PomAssert {
        isNotNull()
        val groupId = parsedDocument().projectChild("groupId")
        if (groupId != expected) {
            failWithMessage("Expecting pom <%s> to have groupId <%s> but was <%s>", actual, expected, groupId)
        }
        return this
    }

    /** Asserts the `/project/artifactId` element equals [expected]. */
    open fun hasArtifactId(expected: String): PomAssert {
        isNotNull()
        val artifactId = parsedDocument().projectChild("artifactId")
        if (artifactId != expected) {
            failWithMessage("Expecting pom <%s> to have artifactId <%s> but was <%s>", actual, expected, artifactId)
        }
        return this
    }

    /** Asserts the `/project/version` element equals [expected]. */
    open fun hasVersion(expected: String): PomAssert {
        isNotNull()
        val version = parsedDocument().projectChild("version")
        if (version != expected) {
            failWithMessage("Expecting pom <%s> to have version <%s> but was <%s>", actual, expected, version)
        }
        return this
    }

    /** Asserts a `/project/dependencies/dependency` triple `group:artifact:version` exists. */
    open fun hasDependency(
        group: String,
        artifact: String,
        version: String,
    ): PomAssert {
        isNotNull()
        val dependencies = parsedDocument().getElementsByTagName("dependency")
        val found =
            (0 until dependencies.length).any { index ->
                val dependency = dependencies.item(index)
                dependency.childText("groupId") == group &&
                    dependency.childText("artifactId") == artifact &&
                    dependency.childText("version") == version
            }
        if (!found) {
            failWithMessage("Expecting pom <%s> to have dependency %s:%s:%s but it was not found", actual, group, artifact, version)
        }
        return this
    }

    /** Asserts the raw pom text contains [fragment]. Escape hatch for anything not modeled above. */
    open fun hasText(fragment: String): PomAssert {
        isNotNull()
        val text = existingText()
        if (!text.contains(fragment)) {
            failWithMessage("Expecting pom <%s> to contain <%s>", actual, fragment)
        }
        return this
    }

    private fun existingText(): String {
        if (!actual.exists()) {
            failWithMessage("Expecting pom <%s> to exist but it was not found", actual)
        }
        return actual.readText()
    }

    private fun parsedDocument(): Document {
        existingText()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            return factory.newDocumentBuilder().parse(actual)
        } catch (e: Exception) {
            failWithMessage("Expecting pom <%s> to be well-formed XML but parsing failed: %s", actual, e.message)
            throw AssertionError("pom <%s> is not well-formed XML".format(actual), e)
        }
    }

    private fun Document.projectChild(tag: String): String? {
        val project = getElementsByTagName("project").item(0) ?: return null
        return project.childText(tag)
    }

    private fun Node.childText(tag: String): String? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tag) {
                return child.textContent.trim()
            }
        }
        return null
    }
}
