package com.mreil.easy.jvm.kotlin

import com.mreil.easy.AbstractEasySettingsPlugin
import com.mreil.easy.EasyExtension
import com.mreil.easy.EnabledBy
import com.mreil.easy.jvm.DefaultEasyJvmDefaultsExtension
import com.mreil.easy.jvm.EasyJvmDefaultsExtension
import org.gradle.api.Action
import org.gradle.api.initialization.Settings

/**
 * Contributes the Dokka Javadoc wiring at settings scope. Gated by [EasyJvmDefaultsExtension]
 * (`enabled`); additionally opt-in via `easy.jvmDefaults.dokkaJavadoc(...)`, whose configured
 * version drives the marker added to the root project's buildscript classpath (subprojects inherit
 * it). Every project still registers the KGP-gated Dokka apply.
 */
@EnabledBy(EasyJvmDefaultsExtension::class)
class DokkaJavadocSettingsPlugin : AbstractEasySettingsPlugin() {
    override fun afterEnabled(target: Settings) {
        val jvmDefaults =
            target.extensions
                .getByType(EasyExtension::class.java)
                .extensions
                .getByType(EasyJvmDefaultsExtension::class.java)
        val version = (jvmDefaults as? DefaultEasyJvmDefaultsExtension)?.dokkaJavadocVersion?.orNull ?: return
        target.gradle.beforeProject(
            Action { project ->
                DokkaJavadocWiring.inject(project, version, includeClasspath = project.parent == null)
            },
        )
    }
}
