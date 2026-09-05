package com.mreil.easy.projectdefaults

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named

/**
 * Public API for the `easy.projectDefaults` extension.
 *
 * Empty for now - serves as a marker to enable project convention checks
 * (required `group`/`version`) via `easy { projectDefaults {} }`.
 * Future configuration (e.g. additional conventions) can be added here.
 */
interface EasyProjectDefaultsExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "projectDefaults"
    }
}
