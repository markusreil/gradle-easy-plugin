package com.mreil.easy.jvm

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.provider.Property

/**
 * Public API for the `easy.jvmDefaults` extension.
 *
 * Enables JVM defaults (toolchain pinning, sources/javadoc jars) via `easy { jvmDefaults {} }`.
 * When [configureTestSuites] is enabled, the plugin configures the framework-provided `test`
 * suite and discovers the project's `src/<name>test/{java,kotlin}` source directories as a basis
 * for auto-configuring further test suites.
 */
interface EasyJvmDefaultsExtension :
    EasyPluginExtension,
    CanBeEnabled {
    /**
     * Whether test suites are auto-configured: the framework-provided `test` suite and suites
     * discovered from the project's `src/<name>test/{java,kotlin}` source directories
     * (convention `true`).
     */
    val configureTestSuites: Property<Boolean>

    /**
     * Whether JaCoCo is applied to the project and wire auto-configured functional suites into
     * its coverage report (convention `true`). For the build's root project this also enables
     * `jacoco-report-aggregation` and the root-level `<suite>CodeCoverageReport` tasks; disabling
     * it on the root therefore disables coverage aggregation for the whole build.
     */
    val jacocoEnabled: Property<Boolean>

    companion object : Named {
        override val name: String = "jvmDefaults"
    }
}
