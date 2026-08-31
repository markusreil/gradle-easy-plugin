package com.mreil.easy

import org.gradle.api.provider.Property

interface CanBeEnabled {
    val enabled: Property<Boolean>
}

fun CanBeEnabled.isEnabled(): Boolean = enabled.getOrElse(true)
