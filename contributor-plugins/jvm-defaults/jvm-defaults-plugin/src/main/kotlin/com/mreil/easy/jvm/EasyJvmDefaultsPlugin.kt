package com.mreil.easy.jvm

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.notifyRedundantConfig
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/** JVM defaults plugin contributed via [EasyJvmDefaultsContributor]. */
@ApplyToSubprojects
class EasyJvmDefaultsPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        target.pluginManager.withPlugin("java") {
            val javaExtension = target.extensions.getByType(JavaPluginExtension::class.java)
            ensureSourcesJar(target, javaExtension)
            ensureJavadocJar(target, javaExtension)
        }
    }

    private fun ensureSourcesJar(
        target: Project,
        javaExtension: JavaPluginExtension,
    ) {
        if (target.tasks.findByName("sourcesJar") == null) {
            javaExtension.withSourcesJar()
        } else {
            target.notifyRedundantConfig("sourcesJar", "withSourcesJar()")
        }
    }

    private fun ensureJavadocJar(
        target: Project,
        javaExtension: JavaPluginExtension,
    ) {
        if (target.tasks.findByName("javadocJar") == null) {
            javaExtension.withJavadocJar()
        } else {
            target.notifyRedundantConfig("javadocJar", "withJavadocJar()")
        }
    }
}
