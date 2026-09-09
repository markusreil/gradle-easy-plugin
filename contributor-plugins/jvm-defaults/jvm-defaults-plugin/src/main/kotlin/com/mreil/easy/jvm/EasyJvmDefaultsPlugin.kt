package com.mreil.easy.jvm

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import com.mreil.easy.notifyRedundantConfig
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

/** JVM defaults plugin contributed via [EasyJvmDefaultsContributor]. */
@ApplyToSubprojects
@EnabledBy(EasyJvmDefaultsExtension::class)
class EasyJvmDefaultsPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        target.pluginManager.withPlugin("java") {
            ensureJarTask(target, "sourcesJar", "withSourcesJar()", JavaPluginExtension::withSourcesJar)
            ensureJarTask(target, "javadocJar", "withJavadocJar()", JavaPluginExtension::withJavadocJar)
            applyToolchain(target)
        }
    }

    /**
     * Pins the Java toolchain to the version declared via [PROPERTY_NAME] (env → system → Gradle
     * property), so JVM defaults build for a fixed bytecode target regardless of the JDK running
     * Gradle. Logs at [org.gradle.api.logging.LogLevel.INFO] when the property is not set.
     */
    private fun applyToolchain(target: Project) {
        val version = propertyResolver.get(PROPERTY_NAME).orNull?.trim()
        if (version == null) {
            target.logger.info(
                "[easy] [{}] {}",
                target.path,
                "No Java toolchain version declared via '$PROPERTY_NAME' - leaving the default toolchain in place.",
            )
            return
        }
        val javaExtension = target.extensions.getByType(JavaPluginExtension::class.java)
        javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(version))
        target.logger.info("[easy] [{}] Java toolchain pinned to {}", target.path, version)
    }

    companion object {
        /** Property (env/system/Gradle) declaring the Java toolchain version to apply. */
        const val PROPERTY_NAME = "java.toolchainVersion"
    }

    private fun ensureJarTask(
        target: Project,
        taskName: String,
        configSnippet: String,
        enable: JavaPluginExtension.() -> Unit,
    ) {
        if (target.tasks.findByName(taskName) == null) {
            target.extensions.getByType(JavaPluginExtension::class.java).enable()
        } else {
            target.notifyRedundantConfig(taskName, configSnippet)
        }
    }
}
