package com.mreil.easy

/**
 * Controls how a property is copied by [ExtensionCopier] from `Settings` to `Project`.
 *
 * Currently unused — no property is annotated yet — but intentionally kept for future
 * fine-grained copy semantics (see `ExtensionCopier`).
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
