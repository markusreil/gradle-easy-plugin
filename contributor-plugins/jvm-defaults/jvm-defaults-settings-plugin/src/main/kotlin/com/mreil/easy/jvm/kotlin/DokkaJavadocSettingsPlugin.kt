package com.mreil.easy.jvm.kotlin

import com.mreil.easy.AbstractEasySettingsPlugin
import com.mreil.easy.EasySettingsExtension
import com.mreil.easy.EnabledBy
import com.mreil.easy.jvm.DefaultEasyJvmDefaultsSettingsExtension
import com.mreil.easy.jvm.EasyJvmDefaultsSettingsExtension
import org.gradle.api.Action
import org.gradle.api.initialization.Settings

/**
 * Contributes the Dokka Javadoc wiring at settings scope. Gated by [EasyJvmDefaultsSettingsExtension]
 * (`enabled`); additionally opt-in via `easy.jvmDefaults.dokkaJavadoc(...)`, whose configured
 * version drives the marker added to the root project's buildscript classpath (subprojects inherit
 * it). Every project still registers the KGP-gated Dokka apply.
 */
@EnabledBy(EasyJvmDefaultsSettingsExtension::class)
class DokkaJavadocSettingsPlugin : AbstractEasySettingsPlugin() {
    override fun afterEnabled(target: Settings) {
        val jvmDefaults =
            target.extensions
                .getByType(EasySettingsExtension::class.java)
                .extensions
                .getByType(EasyJvmDefaultsSettingsExtension::class.java)
        val version = (jvmDefaults as? DefaultEasyJvmDefaultsSettingsExtension)?.dokkaJavadocVersion?.orNull ?: return
        target.gradle.beforeProject(
            Action { project ->
                DokkaJavadocSettingsWiring.inject(project, version, includeClasspath = project.parent == null)
            },
        )
    }
}
