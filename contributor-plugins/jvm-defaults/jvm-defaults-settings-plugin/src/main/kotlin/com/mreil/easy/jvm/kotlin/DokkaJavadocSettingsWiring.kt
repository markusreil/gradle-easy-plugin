package com.mreil.easy.jvm.kotlin

import com.mreil.easy.jvm.DokkaJavadoc
import com.mreil.easy.jvm.JvmDefaultsPlugins
import org.gradle.api.Project

/**
 * Settings-scope Dokka wiring: adds the Dokka marker to the root project's buildscript classpath
 * and applies the Dokka Javadoc plugin to Kotlin projects. Dokka is referenced by id/marker only.
 *
 * Called from a settings-level `beforeProject` hook only when
 * `easy.jvmDefaults.dokkaJavadoc(...)` opted in.
 */
internal object DokkaJavadocSettingsWiring {
    @Suppress("ForbiddenComment")
    fun inject(
        project: Project,
        version: String,
        includeClasspath: Boolean,
    ) {
        if (includeClasspath) {
            // TODO: provide sensible default buildscript repositories (or reuse the build's
            //  pluginManagement repositories) as a separate default-plugin step; for now the build
            //  must declare them itself.
            project.buildscript.dependencies.add("classpath", "${DokkaJavadoc.MARKER}:$version")
        }
        project.pluginManager.withPlugin(JvmDefaultsPlugins.KOTLIN_JVM_PLUGIN) {
            project.pluginManager.apply(DokkaJavadoc.PLUGIN_ID)
        }
    }
}
