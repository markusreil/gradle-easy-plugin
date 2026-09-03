package com.mreil.utils

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
    fun get(name: String): StringProvider {
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

        return StringProvider(candidates.reduce { acc, next -> acc.orElse(next) })
    }

    /**
     * Custom [Provider] for `String` values with convenience transformations.
     *
     * Wraps a delegate [Provider] and delegates all [Provider] operations to it while exposing
     * additional helpers such as [base64Decode]. The implementation is configuration-cache compatible
     * because transformations are implemented via [Provider.map].
     */
    class StringProvider(
        private val delegate: Provider<String>,
    ) : Provider<String> by delegate {
        /**
         * Returns a [Provider] whose value is the Base64-decoded form of this provider's value.
         *
         * Uses [Base64.getDecoder] with UTF-8. If this provider is absent the result is absent.
         * Decoding is lazy and CC-compatible via [Provider.map].
         */
        fun base64Decode(): Provider<String> =
            delegate.map { encoded ->
                String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            }
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
