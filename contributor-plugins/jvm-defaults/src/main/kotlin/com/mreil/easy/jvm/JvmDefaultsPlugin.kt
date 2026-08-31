package com.mreil.easy.jvm

import com.mreil.easy.AbstractEasyProjectPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/** Placeholder JVM defaults plugin contributed via [JvmDefaultsContributor]. */
class JvmDefaultsPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        target.pluginManager.withPlugin("java") {
            target.extensions.getByType(JavaPluginExtension::class.java).apply {
                withSourcesJar()
                withJavadocJar()
            }
        }
    }
}
