package com.mreil.easy

/**
 * Base marker interface for domain-specific sub-extensions contributed by modular Easy plugins
 * (discovered via [EasyPluginContributor.pluginExtensions]).
 *
 * Contributed extensions implementing this interface are automatically registered onto [EasyExtension.extensions]
 * on both `Settings` and `Project` scopes, and are copied across project boundaries using [CanBeCopied] semantics.
 *
 * ### Differences from [EasyExtension]:
 * - **Scope**: [EasyPluginExtension] represents modular, child feature extensions attached under [EasyExtension.extensions]
 *   (e.g., `easy.publish`), whereas [EasyExtension] is the top-level root extension (`easy`).
 * - **Discovery & Extensibility**: Implementations of [EasyPluginExtension] are contributed dynamically by third-party
 *   or contributor plugins via [EasyPluginContributor], rather than being the overarching root container.
 */
interface EasyPluginExtension : CanBeCopied
