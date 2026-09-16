package com.mreil.utils

import org.gradle.api.GradleException
import org.gradle.api.provider.Provider

/**
 * Returns the value of this provider, or throws [GradleException] with [message] when absent.
 *
 * Reads lazily at call time (task action or finalized configuration), so it stays
 * configuration-cache compatible — prefer it over a throwing provider convention.
 */
fun <T : Any> Provider<T>.required(message: String): T = orNull ?: throw GradleException(message)
