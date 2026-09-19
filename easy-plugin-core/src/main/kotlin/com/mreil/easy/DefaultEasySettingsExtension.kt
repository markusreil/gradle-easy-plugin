package com.mreil.easy

/** Settings-scope `easy` extension implementation. Abstract for Gradle decoration via extensions.create (requires a non-final type). */
@Suppress("AbstractClassCanBeInterface")
abstract class DefaultEasySettingsExtension : EasySettingsExtension {
    companion object : Named {
        override val name: String = EasySettingsExtension.name
    }
}
