package com.mreil.easy.test.project.assertj

import com.mreil.easy.test.project.TestProject
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.SoftAssertionsProvider
import java.io.File
import java.util.function.Consumer

/**
 * AssertJ assertions for [TestProject], covering published Maven artifacts.
 *
 * Release versions resolve to their exact repository path (see [TestProject.mavenArtifact]).
 * Snapshot versions (`*-SNAPSHOT`) are resolved by scanning the version directory for a jar
 * matching `<name>-<baseVersion>-*.jar`, since Gradle publishes timestamped snapshot files.
 * The class and its methods are `open` so soft assertions can proxy them.
 */
open class TestProjectAssert(
    actual: TestProject,
) : AbstractAssert<TestProjectAssert, TestProject>(actual, TestProjectAssert::class.java) {
    /** Asserts a matching [coordinates] artifact exists in [repo]. */
    open fun hasArtifact(
        repo: File,
        coordinates: MavenCoordinates,
    ): TestProjectAssert {
        isNotNull()
        val found = actual.artifactCandidates(repo, coordinates).filter { it.exists() }
        if (found.isEmpty()) {
            failWithMessage(
                "Expecting test project <%s> to have artifact %s in <%s> but no matching file was found",
                actual.projectDir,
                coordinates,
                repo,
            )
        }
        return this
    }

    /** Asserts no matching [coordinates] artifact exists in [repo]. */
    open fun doesNotHaveArtifact(
        repo: File,
        coordinates: MavenCoordinates,
    ): TestProjectAssert {
        isNotNull()
        val found = actual.artifactCandidates(repo, coordinates).filter { it.exists() }
        if (found.isNotEmpty()) {
            failWithMessage(
                "Expecting test project <%s> not to have artifact %s in <%s> but found <%s>",
                actual.projectDir,
                coordinates,
                repo,
                found.joinToString(),
            )
        }
        return this
    }

    /** Asserts `maven-metadata.xml` exists in [repo] for [coordinates]. */
    open fun hasMavenMetadata(
        repo: File,
        coordinates: MavenCoordinates,
    ): TestProjectAssert {
        isNotNull()
        val metadata = actual.mavenMetadata(repo, coordinates)
        if (!metadata.exists()) {
            failWithMessage(
                "Expecting test project <%s> to have maven-metadata.xml for %s in <%s> but it was not found",
                actual.projectDir,
                coordinates,
                repo,
            )
        }
        return this
    }

    /** Asserts `maven-metadata.xml` does not exist in [repo] for [coordinates]. */
    open fun doesNotHaveMavenMetadata(
        repo: File,
        coordinates: MavenCoordinates,
    ): TestProjectAssert {
        isNotNull()
        val metadata = actual.mavenMetadata(repo, coordinates)
        if (metadata.exists()) {
            failWithMessage(
                "Expecting test project <%s> not to have maven-metadata.xml for %s in <%s> but found <%s>",
                actual.projectDir,
                coordinates,
                repo,
                metadata,
            )
        }
        return this
    }

    /**
     * Asserts the [coordinates] pom exists in [repo] and returns [PomAssert] for content assertions,
     * e.g. `hasPom(repo, coordinates).hasGroupId("com.example")`.
     * The pom path always uses the `pom` extension. Chained [PomAssert] checks participate in
     * soft assertion collection like any other check.
     */
    open fun hasPom(
        repo: File,
        coordinates: MavenCoordinates,
    ): PomAssert {
        isNotNull()
        val pom = actual.mavenArtifact(repo, coordinates.copy(extension = "pom"))
        if (!pom.exists()) {
            failWithMessage(
                "Expecting test project <%s> to have pom %s in <%s> but it was not found",
                actual.projectDir,
                coordinates.copy(extension = "pom"),
                repo,
            )
        }
        val harness = softHarness
        return if (harness != null) harness.proxy(PomAssert::class.java, File::class.java, pom) else PomAssert(pom)
    }

    /**
     * Soft harness that created this assertion, if any. Set by [SoftTestProjectAssertions] so
     * navigation methods like [hasPom] can return proxied (collecting) assertions.
     */
    internal var softHarness: SoftTestProjectAssertions? = null
}

/**
 * Soft assertions for [TestProject], usable via
 * `assertSoftly { it.assertThat(project).hasArtifact(...) }`
 * (imported from this package).
 * Extends [SoftAssertions] so all standard AssertJ assertions remain available.
 */
class SoftTestProjectAssertions : SoftAssertions() {
    /** Asserts on [actual], collecting failures instead of throwing. */
    fun assertThat(actual: TestProject): TestProjectAssert {
        val harness = this
        return proxy(TestProjectAssert::class.java, TestProject::class.java, actual).apply { softHarness = harness }
    }
}

/** Runs [consumer], collecting all failures and throwing them combined. */
fun assertSoftly(consumer: Consumer<SoftTestProjectAssertions>) {
    SoftAssertionsProvider.assertSoftly(SoftTestProjectAssertions::class.java, consumer)
}

private fun TestProject.artifactCandidates(
    repo: File,
    coordinates: MavenCoordinates,
): List<File> {
    if (!coordinates.version.endsWith("-SNAPSHOT")) {
        return listOf(mavenArtifact(repo, coordinates))
    }
    val baseVersion = coordinates.version.removeSuffix("-SNAPSHOT")
    val prefix = "${coordinates.name}-$baseVersion-"
    val versionDir = File(repo, "${coordinates.group.replace('.', '/')}/${coordinates.name}/${coordinates.version}")
    return versionDir
        .listFiles { file ->
            file.isFile &&
                file.name.startsWith(prefix) &&
                file.name.endsWith(".${coordinates.extension}") &&
                (coordinates.classifier == null || file.name.contains("-${coordinates.classifier}."))
        }?.toList() ?: emptyList()
}
