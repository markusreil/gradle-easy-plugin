package com.mreil.easy.codemeta

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.provider.Property

/**
 * Public API for the `easy.codemeta` extension.
 *
 * Empty for now - serves as a marker to enable the codemeta plugin via `easy { codemeta {} }`.
 * Future configuration (e.g. output path, authors, license) can be added here.
 */
interface EasyCodemetaExtension :
    EasyPluginExtension,
    CanBeEnabled {
    val filename: Property<String>

    companion object : Named {
        override val name: String = "codemeta"
    }
}
