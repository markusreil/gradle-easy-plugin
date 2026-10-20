package com.mreil.utils

import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.util.Base64

/**
 * Resolves a value from environment, system property or Gradle property via a single name.
 *
 * The lookup is configuration-cache compatible – it returns a [Provider] that is lazy and tracks
 * its inputs. Resolution order is: environment variable → system property → Gradle property.
 *
 * Name conversions are applied so callers can use any common convention:
 * - environment: `UPPER_SNAKE_CASE` (e.g. `MY_PROPERTY`)
 * - system / Gradle property: `lower.dot.case` or `lower-kebab` or `camelCase`
 *
 * Examples — all of these resolve the same logical key:
 * ```
 * resolver.get("myProperty")  // checks MY_PROPERTY, my.property, myProperty
 * resolver.get("my.property") // checks MY_PROPERTY, my.property
 * resolver.get("MY_PROPERTY") // checks MY_PROPERTY, my.property
 * resolver.get("my-property") // checks MY_PROPERTY, my.property
 * ```
 */
class PropertyResolver(
    private val providers: ProviderFactory,
) {
    /**
     * Returns a lazy [StringProvider] that resolves [name] from env → system → Gradle property.
     *
     * Each source is queried with name variants derived from [name] so e.g. `my.property`
     * also matches `MY_PROPERTY` and vice-versa. The provider is absent if no source has the key.
     * The result is a [StringProvider] that still implements [Provider] but adds convenience
     * helpers such as [StringProvider.base64Decode].
     */
    operator fun get(name: String): StringProvider {
        require(name.isNotBlank()) { "name must not be blank" }
        val envKey = toEnvKey(name)
        val propertyKey = toPropertyKey(name)

        val candidates = mutableListOf<Provider<String>>()
        candidates += providers.environmentVariable(envKey)

        val systemKeys = linkedSetOf(propertyKey)
        if (name != propertyKey) systemKeys += name

        for (key in systemKeys) {
            candidates += providers.systemProperty(key)
        }

        val gradleKeys = linkedSetOf(propertyKey)
        if (name != propertyKey) gradleKeys += name

        for (key in gradleKeys) {
            candidates += providers.gradleProperty(key)
        }

        return StringProvider(
            candidates.reduce { acc, next -> acc.orElse(next) },
            providers,
        )
    }

    /**
     * Custom [Provider] for `String` values with convenience transformations.
     *
     * Plain nested (static) class holding the delegate [Provider] and the [ProviderFactory]
     * (an injected Gradle service reference, safe to hold for configuration-cache
     * purposes). Deliberately not `inner` so it can be constructed as
     * `PropertyResolver.StringProvider(delegate, providers)` without an outer instance.
     * Exposes additional helpers such as [base64Decode]; transformations
     * stay lazy and CC-compatible because they are built via [Provider.map]/[Provider.orElse].
     *
     * Implements Gradle's internal [ProviderInternal] by delegating to the (likewise
     * internal) delegate, so instances can go directly into `Property.convention(...)`
     * without unwrapping. This couples to Gradle-internal API — acceptable here since
     * the build pins the Gradle version via the wrapper; re-evaluate on Gradle upgrades.
     *
     * Note: do NOT add fail-fast helpers based on `orElse` with a throwing provider —
     * a throwing fallback detonates at configuration-cache store time (not-yet-realized
     * system-property providers read absent there), failing every build whose task graph
     * contains the task. Validate required values at task execution time instead.
     */
    class StringProvider(
        private val delegate: Provider<String>,
        private val providers: ProviderFactory,
    ) : ProviderInternal<String> by (delegate as ProviderInternal<String>) {
        /**
         * Returns a [StringProvider] whose value is the Base64-decoded form of this provider's value.
         *
         * Uses [Base64.getDecoder] with UTF-8. If this provider is absent the result is absent.
         * Decoding is lazy and CC-compatible via [Provider.map].
         */
        fun base64Decode(): StringProvider =
            StringProvider(
                delegate.map { encoded ->
                    String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                },
                providers,
            )
    }

    companion object {
        /**
         * Converts any convention to env form: `UPPER_SNAKE_CASE`.
         * Handles `.`, `-`, `_` and `camelCase` boundaries.
         */
        fun toEnvKey(name: String): String {
            val withUnderscores = name.replace('.', '_').replace('-', '_')
            val withCamel = withUnderscores.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_")
            return withCamel.uppercase()
        }

        /**
         * Converts any convention to Gradle/system property form: `lower.dot.case`.
         * Handles `_`, `-` and `camelCase` boundaries.
         */
        fun toPropertyKey(name: String): String {
            val withDots = name.replace('_', '.').replace('-', '.')
            val withCamel = withDots.replace(Regex("(?<=[a-z])(?=[A-Z])"), ".")
            return withCamel.lowercase().replace(Regex("\\.+"), ".").trim('.')
        }
    }
}

/** Backwards-compatible alias for the nested provider — prefer [PropertyResolver.StringProvider]. */
typealias StringProvider = PropertyResolver.StringProvider
