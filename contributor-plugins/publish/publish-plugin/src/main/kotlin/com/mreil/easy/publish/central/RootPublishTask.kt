package com.mreil.easy.publish.central

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Returns the root `publish` lifecycle task, registering it on first use.
 *
 * Single source of truth for the `findByName("publish") == null -> register`
 * guard previously duplicated across wiring units. Race-free: task registration
 * is idempotent per name within one project, and callers only ever call this on
 * the root project.
 */
internal fun Project.ensureRootPublishTask(): TaskProvider<Task> =
    if (tasks.findByName("publish") == null) {
        tasks.register("publish") { it.group = "publishing" }
    } else {
        tasks.named("publish")
    }
