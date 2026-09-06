package com.mreil.easy.publish

/**
 * Pinned JReleaser CLI coordinates for [JreleaserPublishTask].
 *
 * Hardwired for now; may move to the version catalog or a `jreleaserVersion`
 * extension property later. The `org.jreleaser:jreleaser` artifact is the CLI
 * application jar (manifest `Main-Class: org.jreleaser.cli.Main`); a resolvable
 * `jreleaser` configuration pulls it with transitives at task execution time.
 */
internal object JreleaserVersions {
    const val CLI = "1.25.0"
    const val COORDINATES = "org.jreleaser:jreleaser"
    const val MAIN_CLASS = "org.jreleaser.cli.Main"
}
