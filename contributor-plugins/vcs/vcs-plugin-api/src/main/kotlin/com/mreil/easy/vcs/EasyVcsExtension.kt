package com.mreil.easy.vcs

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named

/**
 * Public API for the `easy.vcs` extension.
 *
 * Empty for now - serves as a marker to enable the vcs plugin via `easy { vcs {} }`.
 * Future configuration can be added here.
 */
interface EasyVcsExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "vcs"
    }
}
