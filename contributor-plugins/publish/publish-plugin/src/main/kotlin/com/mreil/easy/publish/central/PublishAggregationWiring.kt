package com.mreil.easy.publish.central

import com.mreil.easy.isRoot
import org.gradle.api.Project

/**
 * Root-only aggregation of the `publish` lifecycle task.
 *
 * The root `publish` task aggregates all subproject `publish` tasks so a single
 * invocation stages every module. Picks up `maven-publish` adopters added later,
 * regardless of evaluation order.
 */
internal object PublishAggregationWiring {
    fun wire(target: Project) {
        if (!target.isRoot()) return

        target.allprojects { project ->
            project.plugins.withId("maven-publish") {
                if (project != target) {
                    target.ensureRootPublishTask().configure { publish -> publish.dependsOn(project.tasks.named("publish")) }
                }
            }
        }
    }
}
