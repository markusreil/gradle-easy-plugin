package com.mreil.easy.semver

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named

/**
 * Public API for the `easy.semver` extension.
 *
 * Empty for now - serves as a marker to enable the semver plugin via `easy { semver {} }`.
 * The actual version lookup is hidden behind [EasySemver] object which lazily reads
 * the project's version without exposing its storage.
 */
interface EasySemverExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "semver"
    }
}
