package com.mreil.easy.publish.central

/**
 * Maven deployers rendered under `deploy.maven` in the generated JReleaser YAML.
 *
 * Each deployer knows its section (`mavenCentral`, `nexus3`), its repository
 * name and how to render itself as an ordered map. Shared keys (`stagingRepositories`,
 * `username`, `password`) are built by [commonDeployerMap] so subclasses only declare
 * their unique keys.
 *
 * Snapshots are deliberately not deployed via JReleaser (single-threaded per-file
 * upload is too slow) — they go directly through `maven-publish` via
 * `easy.publish.toSonatypeSnapshots()`, so only the release deployer remains.
 */
internal sealed interface JreleaserMavenDeployer {
    val section: String
    val name: String

    fun toMap(): Map<String, Any>
}

internal data class MavenCentralDeployer(
    val active: String,
    val stagingDirs: List<String>,
    val username: String,
    val password: String,
) : JreleaserMavenDeployer {
    override val section = "mavenCentral"
    override val name = "sonatype"

    override fun toMap(): Map<String, Any> =
        linkedMapOf<String, Any>(
            "active" to active,
            "url" to "https://central.sonatype.com/api/v1/publisher",
            // Signing is handled by Gradle's `signing` plugin (SigningWiring); JReleaser must not
            // sign or its validation fails on a `sign: true` deployer with no `signing` block.
            "sign" to false,
        ).also { it.putAll(commonDeployerMap(stagingDirs, username, password)) }
}

internal data class Nexus3TestDeployer(
    val url: String,
    val stagingDirs: List<String>,
    val username: String,
    val password: String,
    val active: String = "ALWAYS",
) : JreleaserMavenDeployer {
    override val section = "nexus3"
    override val name = "local-test"

    override fun toMap(): Map<String, Any> =
        linkedMapOf<String, Any>(
            "active" to active,
            "url" to url,
            "authorization" to "BASIC",
            "applyMavenCentralRules" to true,
            // Signing is handled by Gradle's `signing` plugin (SigningWiring); JReleaser must not
            // sign or its validation fails on a `sign: true` deployer with no `signing` block.
            "sign" to false,
        ).also { it.putAll(commonDeployerMap(stagingDirs, username, password)) }
}

internal fun commonDeployerMap(
    stagingDirs: List<String>,
    username: String,
    password: String,
): Map<String, Any> =
    linkedMapOf(
        "stagingRepositories" to stagingDirs,
        "username" to username,
        "password" to password,
    )

/**
 * Resolves the deployer list for a [JreleaserYaml.Config].
 *
 * Only releases go through JReleaser (Portal); snapshots publish directly via
 * `maven-publish` (see `toSonatypeSnapshots`). In nexus test mode everything
 * remote is NEVER so smoke runs stay local-only.
 */
internal fun deployersFor(config: JreleaserYaml.Config): List<JreleaserMavenDeployer> {
    val testMode = config.nexusUrl != null
    val deployers =
        mutableListOf<JreleaserMavenDeployer>(
            MavenCentralDeployer(
                active = if (testMode) "NEVER" else "RELEASE",
                stagingDirs = config.stagingDirs,
                username = config.mavenCentralUsername,
                password = config.mavenCentralPassword,
            ),
        )
    if (config.nexusUrl != null) {
        deployers +=
            Nexus3TestDeployer(
                url = config.nexusUrl,
                stagingDirs = config.stagingDirs,
                username = config.nexusUsername,
                password = config.nexusPassword,
            )
    }
    return deployers
}
