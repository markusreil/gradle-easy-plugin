package com.mreil.easy.jvm.java

import com.mreil.easy.easyInfo
import com.mreil.utils.PropertyResolver
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Pins the Java source/target compatibility (and `--release`) to the version declared via
 * [PROPERTY_NAME], so a build can compile with a newer toolchain while producing bytecode and an
 * API compatible with an older JDK. Logs at [org.gradle.api.logging.LogLevel.INFO] when the
 * property is not set.
 *
 * The declared target must not exceed the toolchain pinned by [ToolchainWiring]; a mismatch fails
 * fast with a [GradleException] instead of a cryptic compiler error.
 */
internal object TargetCompatibilityWiring {
    /** Property (env/system/Gradle) declaring the Java target version to apply. */
    const val PROPERTY_NAME = "java.targetVersion"

    internal fun configure(target: Project) {
        val resolver = PropertyResolver(target.providers)
        val version = resolver.get(PROPERTY_NAME).orNull?.trim()
        if (version == null) {
            target.easyInfo(
                "No Java target version declared via '{}' - leaving the default source/target compatibility in place.",
                PROPERTY_NAME,
            )
            return
        }
        val javaVersion = JavaVersion.toVersion(version)
        val targetLevel = javaVersion.majorVersion.toInt()
        val javaExtension = target.extensions.getByType(JavaPluginExtension::class.java)
        javaExtension.toolchain.languageVersion.orNull?.asInt()?.let { toolchain ->
            if (toolchain < targetLevel) {
                throw GradleException(
                    "Java target version '$version' declared via '$PROPERTY_NAME' must not exceed the " +
                        "configured Java toolchain '$toolchain'.",
                )
            }
        }
        javaExtension.sourceCompatibility = javaVersion
        javaExtension.targetCompatibility = javaVersion
        target.tasks.withType(JavaCompile::class.java).configureEach { it.options.release.set(targetLevel) }
        target.easyInfo("Java source/target compatibility pinned to {}", version)
    }
}
