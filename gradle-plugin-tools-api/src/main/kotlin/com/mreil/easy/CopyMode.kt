package com.mreil.easy

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
