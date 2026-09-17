package com.mreil.easy.jvm

import com.mreil.easy.easyInfo
import com.mreil.utils.PropertyResolver
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Pins the Java toolchain to the version declared via [PROPERTY_NAME] (env → system → Gradle
 * property), so JVM defaults build for a fixed bytecode target regardless of the JDK running
 * Gradle. Logs at [org.gradle.api.logging.LogLevel.INFO] when the property is not set.
 */
internal object ToolchainWiring {
    /** Property (env/system/Gradle) declaring the Java toolchain version to apply. */
    const val PROPERTY_NAME = "java.toolchainVersion"

    internal fun configure(target: Project) {
        val resolver = PropertyResolver(target.providers)
        val version = resolver.get(PROPERTY_NAME).orNull?.trim()
        if (version == null) {
            target.easyInfo(
                "No Java toolchain version declared via '{}' - leaving the default toolchain in place.",
                PROPERTY_NAME,
            )
            return
        }
        val javaExtension = target.extensions.getByType(JavaPluginExtension::class.java)
        javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(version))
        target.easyInfo("Java toolchain pinned to {}", version)
    }
}
