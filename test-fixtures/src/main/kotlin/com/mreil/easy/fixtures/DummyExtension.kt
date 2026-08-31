package com.mreil.easy.fixtures

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.provider.Property

/** Test fixture extension for verifying contributor extension registration and copying. */
abstract class DummyExtension :
    EasyPluginExtension,
    CanBeEnabled {
    abstract val message: Property<String>

    companion object : Named {
        override val name: String = "dummy"
    }
}
