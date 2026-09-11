package com.mreil.easy.publish

import com.mreil.easy.findEasyChild
import com.mreil.easy.isEnabled
import org.gradle.api.Project

/**
 * Finds the internal publish extension for wiring code.
 *
 * Shared by the JReleaser wiring units and [EasyPublishPlugin] so the
 * `EasyExtension` → [EasyPublishExtension] lookup lives in one place.
 * Returns null when the publish child extension is not registered (e.g. wiring
 * applied without the contributor extension present). A missing `easy` itself
 * fails fast via `getEasyExtension`.
 */
internal fun Project.publishExtension(): DefaultEasyPublishExtension? = findEasyChild<EasyPublishExtension, DefaultEasyPublishExtension>()

/**
 * True when this project has opted into Maven Central (its own `toMavenCentral` value
 * or the inherited root convention) and the publish extension is enabled.
 *
 * The single gate shared by every Central wiring unit: config collection, deploy
 * dependencies and per-project POM/strip tasks all filter to central-enabled projects.
 */
internal fun Project.isCentralEnabled(): Boolean =
    publishExtension()
        ?.takeIf { it.isEnabled() }
        ?.let { it.toMavenCentral.get() == true }
        ?: false
