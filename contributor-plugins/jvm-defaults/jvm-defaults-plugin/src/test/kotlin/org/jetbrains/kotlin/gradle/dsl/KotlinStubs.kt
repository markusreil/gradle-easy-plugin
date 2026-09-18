package org.jetbrains.kotlin.gradle.dsl

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Test-only stand-ins for the KGP types [com.mreil.easy.jvm.kotlin.KotlinTargetWiring] accesses
 * reflectively. They exist so the reflection path can be exercised without a KGP dependency on the
 * test classpath.
 */
interface KotlinJvmExtension {
    val compilerOptions: KotlinJvmCompilerOptions
}

interface KotlinJvmCompilerOptions {
    val jvmTarget: Property<JvmTarget>
    val freeCompilerArgs: ListProperty<String>
}

enum class JvmTarget(
    val target: String,
) {
    JVM_1_8("1.8"),
    JVM_11("11"),
    ;

    companion object {
        @JvmStatic
        fun fromTarget(target: String): JvmTarget = entries.first { it.target == target }
    }
}
