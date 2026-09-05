package com.mreil.gradletest.project.template

/**
 * A `gradle.properties` template with individually settable [group] and [version].
 * A `null` value omits the key, leaving the Gradle default (`unspecified`) in place.
 *
 * Mirrors `gradle.properties` (`org.gradle.configuration-cache=true`,
 * `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.warning.mode=all`)
 * so functional tests run with the same defaults as the production build.
 */
class GradlePropertiesTemplate(
    var group: String? = "com.example",
    var version: String? = "1.0.0",
    var configurationCache: Boolean? = true,
    var parallel: Boolean? = true,
    var caching: Boolean? = true,
    var warningMode: String? = "all",
) : FileTemplate {
    override fun render(): String =
        listOfNotNull(
            group?.let { "group=$it" },
            version?.let { "version=$it" },
            configurationCache?.let { "org.gradle.configuration-cache=$it" },
            parallel?.let { "org.gradle.parallel=$it" },
            caching?.let { "org.gradle.caching=$it" },
            warningMode?.let { "org.gradle.warning.mode=$it" },
        ).joinToString("\n")

    /** Shortcut to enable/disable configuration cache in the test build. */
    fun withConfigurationCache(enabled: Boolean): GradlePropertiesTemplate = apply { configurationCache = enabled }
}
