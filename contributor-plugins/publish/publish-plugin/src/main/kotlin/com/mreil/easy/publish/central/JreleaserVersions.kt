package com.mreil.easy.publish.central

/**
 * JReleaser CLI coordinates for [JreleaserPublishTask].
 *
 * The version resolves from the consumer's `libs` version catalog (`jreleaser` alias)
 * with [DEFAULT_VERSION] as fallback (see `catalogVersionOrDefault`). The
 * `org.jreleaser:jreleaser` artifact is the CLI application jar
 * (manifest `Main-Class: org.jreleaser.cli.Main`); a resolvable `jreleaser`
 * configuration pulls it with transitives at task execution time.
 *
 * [PROPERTY_*] constants are the JReleaser system-property keys read via
 * [com.mreil.utils.PropertyResolver] (Gradle `-P` / `gradle.properties` / env).
 */
object JreleaserVersions {
    const val DEFAULT_VERSION = "1.25.0"
    const val COORDINATES = "org.jreleaser:jreleaser"
    const val MAIN_CLASS = "org.jreleaser.cli.Main"

    // region JReleaser system-property keys

    const val PROPERTY_MAVENCENTRAL_USERNAME = "jreleaser.mavencentral.username"
    const val PROPERTY_MAVENCENTRAL_PASSWORD = "jreleaser.mavencentral.password"
    const val PROPERTY_NEXUS_USERNAME = "jreleaser.nexus.username"
    const val PROPERTY_NEXUS_PASSWORD = "jreleaser.nexus.password"
    const val PROPERTY_TEST_NEXUS_URL = "jreleaser.testNexusUrl"
    const val PROPERTY_GPG_PRIVATE_KEY = "jreleaser.gpg.privateKey"
    const val PROPERTY_GPG_PASSPHRASE = "jreleaser.gpg.passphrase"

    // endregion
}
