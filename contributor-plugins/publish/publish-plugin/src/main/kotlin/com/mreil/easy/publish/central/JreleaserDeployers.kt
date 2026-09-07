package com.mreil.easy.publish.central

internal const val CENTRAL_SNAPSHOTS_URL = "https://central.sonatype.com/repository/maven-snapshots/"

/**
 * Maven deployers rendered under `deploy.maven` in the generated JReleaser YAML.
 *
 * Each deployer knows its section (`mavenCentral`, `nexus2`, `nexus3`), its repository
 * name and how to render itself as an ordered map. Shared keys (`stagingRepositories`,
 * `username`, `password`) are built by [commonDeployerMap] so subclasses only declare
 * their unique keys.
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
        ).also { it.putAll(commonDeployerMap(stagingDirs, username, password)) }
}

internal data class Nexus2SnapshotsDeployer(
    val active: String,
    val stagingDirs: List<String>,
    val username: String,
    val password: String,
) : JreleaserMavenDeployer {
    override val section = "nexus2"
    override val name = "sonatype-snapshots"

    // Snapshots cannot go through the Portal API; same account/token as central.
    // No close/release: snapshots are PUT directly to snapshotUrl, there is no staging repo to transition.
    // `url` is required by JReleaser validation (NPE in Nexus2MavenDeployerValidator when blank
    // unless active is SNAPSHOT) but unused at deploy time since staging is disabled.
    override fun toMap(): Map<String, Any> =
        linkedMapOf<String, Any>(
            "active" to active,
            "url" to CENTRAL_SNAPSHOTS_URL,
            "snapshotUrl" to CENTRAL_SNAPSHOTS_URL,
            "applyMavenCentralRules" to true,
            "snapshotSupported" to true,
            "closeRepository" to false,
            "releaseRepository" to false,
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
 * JReleaser dispatches on version: releases go to the Portal, snapshots to the snapshots
 * repo. In nexus test mode everything remote is NEVER so smoke runs stay local-only.
 */
internal fun deployersFor(config: JreleaserYaml.Config): List<JreleaserMavenDeployer> {
    val testMode = config.nexusUrl != null
    val deployers =
        mutableListOf(
            MavenCentralDeployer(
                active = if (testMode) "NEVER" else "RELEASE",
                stagingDirs = config.stagingDirs,
                username = config.mavenCentralUsername,
                password = config.mavenCentralPassword,
            ),
            Nexus2SnapshotsDeployer(
                active = if (testMode) "NEVER" else "SNAPSHOT",
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
