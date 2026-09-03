package com.mreil.easy.test.project.template

/**
 * A `gradle.properties` template with individually settable [group] and [version].
 * A `null` value omits the key, leaving the Gradle default (`unspecified`) in place.
 */
class GradlePropertiesTemplate(
    var group: String? = "com.example",
    var version: String? = "1.0.0",
) : FileTemplate {
    override fun render(): String =
        listOfNotNull(
            group?.let { "group=$it" },
            version?.let { "version=$it" },
        ).joinToString("\n")
}
