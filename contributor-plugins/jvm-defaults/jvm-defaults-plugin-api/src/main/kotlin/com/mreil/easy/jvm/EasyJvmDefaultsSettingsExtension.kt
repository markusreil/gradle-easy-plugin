package com.mreil.easy.jvm

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named

/**
 * Public API for the settings-scope `easy.jvmDefaults` extension.
 *
 * Attached under `EasySettingsExtension` (not `EasyExtension`) and configured in
 * `settings.gradle.kts` via `easy { jvmDefaults { ... } }`. It is the settings-only counterpart of
 * [EasyJvmDefaultsExtension] — the two are distinct types and are never copied into each other.
 *
 * It gates [com.mreil.easy.jvm.kotlin.DokkaJavadocSettingsPlugin]: calling [dokkaJavadoc] opts in
 * to adding the Dokka Javadoc plugin marker to the root project's buildscript classpath.
 */
interface EasyJvmDefaultsSettingsExtension :
    EasyPluginExtension,
    CanBeEnabled {
    /**
     * Settings-scope opt-in: adds the Dokka Javadoc plugin marker to every project's buildscript
     * classpath so it can be applied to Kotlin projects and back their `javadocJar` with Dokka
     * output.
     *
     * [version] pins the Dokka plugin version and defaults to [DEFAULT_DOKKA_VERSION]. Not calling
     * this function means no classpath inclusion. It is intentionally settings-only (call it in
     * `settings.gradle(.kts)`) and is never copied to projects. If Dokka is present by other means,
     * the `javadocJar` is still backed by Dokka output.
     */
    fun dokkaJavadoc(version: String? = null)

    companion object : Named {
        /** Dokka plugin version used when [dokkaJavadoc] is called without an explicit version. */
        const val DEFAULT_DOKKA_VERSION: String = "2.2.0"

        override val name: String = "jvmDefaults"
    }
}
