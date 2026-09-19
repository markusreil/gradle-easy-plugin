package com.mreil.easy

/**
 * Base marker interface for domain-specific sub-extensions contributed by modular Easy plugins
 * (discovered via [EasyPluginContributor.pluginExtensions]).
 *
 * Contributed extensions implementing this interface are automatically registered onto [EasyExtension.extensions]
 * on the project root and copied into subprojects using [CanBeCopied] semantics. The settings root
 * ([EasySettingsExtension]) currently hosts no contributed child extensions.
 *
 * ### Differences from [EasyExtension]:
 * - **Scope**: [EasyPluginExtension] represents modular, child feature extensions attached under [EasyExtension.extensions]
 *   (e.g., `easy.publish`), whereas [EasyExtension] is the top-level root extension (`easy`).
 * - **Discovery & Extensibility**: Implementations of [EasyPluginExtension] are contributed dynamically by third-party
 *   or contributor plugins via [EasyPluginContributor], rather than being the overarching root container.
 */
interface EasyPluginExtension : CanBeCopied
