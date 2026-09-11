package com.mreil.easy

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import kotlin.reflect.KClass

/**
 * Lazily resolves a shared [BuildService][org.gradle.api.services.BuildService] registered by a
 * contributor, gated on that contributor's [EasyPluginExtension] being enabled.
 *
 * The returned [Provider] never throws at call time: it is absent when the contributor extension is
 * disabled or not applied, and it fails lazily only when enabled-but-unregistered (at realization).
 * The shared-service lookup is deferred inside a [flatMap], so the eager call only touches providers
 * and is CC-safe.
 *
 * @param S the service type to resolve (the `@PublicType` interface registered under [name]).
 * @param E the contributor extension type that gates availability.
 * @param name the shared-service registration name the contributor registered in its `init()`.
 * @param extensionClass the contributor extension class to read `enabled` from.
 */
inline fun <reified S : Any, E> Project.easyService(
    name: String,
    extensionClass: KClass<E>,
): Provider<S>
    where E : EasyPluginExtension, E : CanBeEnabled =
    gatedBy(extensionClass) {
        @Suppress("UNCHECKED_CAST")
        gradle.sharedServices.registrations
            .named(name)
            .map { reg -> reg.service.get() as S }
    }

/**
 * Lazily resolves a [Provider] only while the given contributor extension is enabled.
 *
 * The returned [Provider] never throws at call time: it is absent when the extension is disabled or not applied.
 * The [whenEnabled] supplier runs inside a [flatMap], so the eager call only touches providers and is CC-safe;
 * failures surface lazily at realization.
 *
 * Defensive note: in production the `easy` extension always exists — core registers it before any contributor
 * applies, and contributors are never applied standalone. The `runCatching` guard below covers tests only
 * (e.g. ProjectBuilder projects without core applied), where a missing `easy` extension would otherwise throw
 * at call time; it degrades to absent there.
 *
 * @param T the resolved value type.
 * @param E the contributor extension type that gates availability.
 * @param extensionClass the contributor extension class to read `enabled` from.
 * @param whenEnabled produces the real value provider when the extension is enabled.
 */
inline fun <reified T : Any, E> Project.gatedBy(
    extensionClass: KClass<E>,
    crossinline whenEnabled: () -> Provider<T>,
): Provider<T>
    where E : EasyPluginExtension, E : CanBeEnabled {
    // Enabled lookup reads the extension's `enabled` property, falling back to false when the
    // extension or the `easy` extension is absent. Eager call only touches providers, never the
    // shared service, so it is CC-safe.
    val enabled =
        runCatching { getEasyExtension().extensions.findByType(extensionClass.java)?.enabled }.getOrNull()
            ?: objects.property(Boolean::class.java).value(false)
    // Absent placeholder realized when disabled, avoiding any lookup of the shared service registration.
    val empty = objects.property(T::class.java)
    return enabled.flatMap { if (it) whenEnabled() else empty }
}
