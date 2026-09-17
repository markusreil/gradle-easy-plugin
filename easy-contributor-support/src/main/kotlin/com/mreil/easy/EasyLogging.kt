package com.mreil.easy

import org.gradle.api.Project

private const val LOG_PREFIX = "[easy]"

/**
 * Logs [message] (with slf4j-style `{}` arguments) at INFO level, prefixed with
 * `[easy] [<project path>]`.
 */
fun Project.easyInfo(
    message: String,
    vararg args: Any,
) = logger.info("$LOG_PREFIX [{}] $message", path, *args)

/**
 * Logs [message] (with slf4j-style `{}` arguments) at LIFECYCLE level, prefixed with
 * `[easy] [<project path>]`.
 */
fun Project.easyLifecycle(
    message: String,
    vararg args: Any,
) = logger.lifecycle("$LOG_PREFIX [{}] $message", path, *args)
