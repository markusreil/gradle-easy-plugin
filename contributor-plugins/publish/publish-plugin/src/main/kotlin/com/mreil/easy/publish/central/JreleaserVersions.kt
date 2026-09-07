package com.mreil.easy.publish.central

/**
 * JReleaser CLI coordinates for [JreleaserPublishTask].
 *
 * The version resolves from the consumer's `libs` version catalog (`jreleaser` alias)
 * with [DEFAULT_VERSION] as fallback (see `catalogVersionOrDefault`). The
 * `org.jreleaser:jreleaser` artifact is the CLI application jar
 * (manifest `Main-Class: org.jreleaser.cli.Main`); a resolvable `jreleaser`
 * configuration pulls it with transitives at task execution time.
 */
internal object JreleaserVersions {
    const val DEFAULT_VERSION = "1.25.0"
    const val COORDINATES = "org.jreleaser:jreleaser"
    const val MAIN_CLASS = "org.jreleaser.cli.Main"
}
