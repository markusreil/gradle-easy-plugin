package com.mreil.easy

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/**
 * Returns true when this project is the root project of the build.
 *
 * Centralizes the `project == project.rootProject` guard duplicated across
 * core (`ExtensionRegistrar`, `SettingsPlugin`) and contributors
 * (publish wirings, codemeta) for root-only wiring.
 */
fun Project.isRoot(): Boolean = this == rootProject

/**
 * Returns the `easy` extension, failing fast when absent.
 *
 * Callers run inside `withPlugin`/`withType` application context where core always
 * registers `easy` first, so a missing extension is a programming error, not a
 * case to handle. `EasyExtension` already extends `ExtensionAware`, giving access
 * to contributed child extensions via `easy.extensions`.
 */
fun Project.getEasyExtension(): EasyExtension = extensions.getByType(EasyExtension::class.java)

/**
 * Finds a contributed child extension inside `easy`, or null when absent.
 *
 * Looks the child up by its public API type [P] (the `@PublicType` interface from
 * the contributor's `-api` module) and returns it as the implementation type [I].
 * Null when the contributor extension is not registered on this project — unlike
 * [getEasyExtension], child presence depends on per-contributor SPI fan-out, so
 * absence is a case callers handle, not a programming error.
 */
inline fun <reified P : EasyPluginExtension, reified I : P> Project.findEasyChild(): I? = getEasyExtension().findEasyChild<P, I>()

/**
 * Workhorse behind [Project.findEasyChild]. Kept `internal` so contributors only go
 * through the `Project` API, never reaching into `easy.extensions` directly.
 * `@PublishedApi` allows the public inline [Project.findEasyChild] to call it —
 * source-level visibility stays `internal`.
 */
@PublishedApi
internal inline fun <reified P : EasyPluginExtension, reified I : P> ExtensionAware.findEasyChild(): I? =
    extensions.findByType(P::class.java) as? I

/**
 * Returns true when the contributed child extension [P] is registered and enabled.
 *
 * Single type parameter: `enabled` is always declared on the public API interface
 * (every `EasyPluginExtension` also implements [CanBeEnabled]), so the
 * implementation class is never needed for this check. Null (absent) counts as
 * disabled. The `CanBeEnabled` conformance that [ExtensionAware.isExtensionEnabled]
 * verifies at runtime is a compile-time bound here instead.
 *
 * Timing contract: call only once user configuration is final — inside
 * `afterEvaluate`, a [org.gradle.api.provider.Provider], or a task action.
 * Eager calls during `apply`/`init` read convention defaults, silently ignoring
 * `easy { }` configuration. This cannot be guarded programmatically: Gradle
 * exposes only `project.state.executed`, which stays false *during* `afterEvaluate`
 * (where every legitimate call site lives) and turns true only after full
 * evaluation — so any check would reject exactly the valid callers.
 */
inline fun <reified P> Project.isEasyChildEnabled(): Boolean where P : EasyPluginExtension, P : CanBeEnabled =
    findEasyChild<P, P>()?.isEnabled() == true
