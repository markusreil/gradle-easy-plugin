package com.mreil.easy.jvm

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named

/**
 * Public API for the `easy.jvmDefaults` extension.
 *
 * Empty for now - serves as a marker to enable JVM defaults (toolchain pinning,
 * sources/javadoc jars) via `easy { jvmDefaults {} }`.
 * Future configuration (e.g. a default toolchain version) can be added here.
 */
interface EasyJvmDefaultsExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "jvmDefaults"
    }
}
