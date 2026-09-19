package com.mreil.easy.jvm

/**
 * Shared Dokka Javadoc constants used by both scopes: the settings-scope opt-in
 * (`com.mreil.easy.jvm.kotlin.DokkaJavadocSettingsPlugin`) and the project-scope `javadocJar`
 * rewire. Dokka is referenced by plugin id/marker only; no Dokka dependency is added anywhere.
 */
object DokkaJavadoc {
    /** Dokka Javadoc Gradle plugin id. */
    const val PLUGIN_ID: String = "org.jetbrains.dokka-javadoc"

    /** Gradle plugin marker coordinates for [PLUGIN_ID]. */
    const val MARKER: String = "org.jetbrains.dokka-javadoc:org.jetbrains.dokka-javadoc.gradle.plugin"

    /** Task producing the Dokka publication Javadoc output. */
    const val PUBLICATION_JAVADOC_TASK: String = "dokkaGeneratePublicationJavadoc"
}
