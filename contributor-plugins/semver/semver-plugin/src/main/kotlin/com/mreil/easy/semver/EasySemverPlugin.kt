package com.mreil.easy.semver

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EnabledBy
import org.gradle.api.Project

/**
 * Easy plugin that enables semver support.
 *
 * No eager work needed - the actual version lookup is via [EasySemver.of].
 * Plugin is gated behind `easy.semver`.
 */
@EnabledBy(EasySemverExtension::class)
class EasySemverPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        // Intentionally empty - EasySemver.of handles lazy lookup.
        // Could add validation that project.version is set after evaluation if desired.
    }
}
