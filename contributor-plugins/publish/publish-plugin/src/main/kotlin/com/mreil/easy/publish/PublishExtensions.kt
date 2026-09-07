package com.mreil.easy.publish

import com.mreil.easy.findEasyChild
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
