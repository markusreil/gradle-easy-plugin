package com.mreil.easy

import kotlin.reflect.KClass

/**
 * Declares the public API type for an [EasyPluginExtension] implementation.
 *
 * When an extension implementation exposes a separate public interface (e.g. from a `-api` module),
 * annotate the implementation with [PublicType] referencing the interface. [ExtensionRegistrar] will
 * register the extension under the public type (using its [Named] companion) and instantiate the
 * annotated implementation.
 *
 * If absent, the extension is registered under its own type.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicType(
    val value: KClass<out EasyPluginExtension>,
)
