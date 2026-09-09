package com.mreil.easy.publish.central

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EnabledBy
import com.mreil.easy.publish.EasyPublishExtension
import org.gradle.api.Project

/**
 * Easy plugin that deploys staged Maven artifacts to Maven Central via JReleaser.
 *
 * It is contributed and applied via the easy plugin infrastructure:
 * - Discovered through [EasyPublishContributor] using the [java.util.ServiceLoader] SPI.
 * - Gated behind the [EasyPublishExtension] `easy.publish` extension, so it only
 *   becomes active when that extension is enabled (see [EnabledBy]).
 *
 * Deliberately NOT annotated with `ApplyToSubprojects`: all wiring owned here
 * ([JreleaserConfigWiring], [JreleaserDeployWiring]) is
 * root-only by construction (see `PluginRegistrar.targetsFor`), so the plugin is
 * applied to the root project only instead of fanning out and no-op-ing on every
 * subproject. The root guards inside the wiring units remain as safety for
 * direct application.
 *
 * Registers `generateJreleaserConfig` (YAML generation, gated on every project's
 * `checkCentralPoms`), the root `publish` aggregation over subproject `publish`
 * tasks, and `publishToMavenCentral` (JReleaser `deploy`), all disabled until
 * `toMavenCentral` is set on the shared publish extension. Per-project POM checks
 * themselves live in [EasyPublishPlugin] (see [CentralPublishingWiring]), so invalid POMs
 * fail fast at upload time with module-scoped errors.
 */
@EnabledBy(EasyPublishExtension::class)
class EasyJreleaserPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        JreleaserConfigWiring.wire(target, propertyResolver)
        PublishAggregationWiring.wire(target)
        JreleaserDeployWiring.wire(target)
    }
}
