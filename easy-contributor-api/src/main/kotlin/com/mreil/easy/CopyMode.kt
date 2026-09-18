package com.mreil.easy

/**
 * Controls how a property is copied by [ExtensionCopier] from `Settings` to `Project`.
 *
 * Defaults to [Mode.DEEP]. [Mode.READ_ONLY] also marks a copied property as read-only (it keeps the
 * source convention and disallows further changes); [Mode.NONE] skips copying it entirely.
 */
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class CopyMode(
    val value: Mode = Mode.DEEP,
) {
    enum class Mode {
        DEEP,
        READ_ONLY,
        NONE,
    }
}
