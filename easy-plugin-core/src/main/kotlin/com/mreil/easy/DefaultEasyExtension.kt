package com.mreil.easy

/** Root `easy` extension implementation. Abstract for Gradle decoration via extensions.create (requires a non-final type). */
@Suppress("UnnecessaryAbstractClass")
abstract class DefaultEasyExtension : EasyExtension {
    companion object : Named {
        override val name: String = EasyExtension.name
    }
}
