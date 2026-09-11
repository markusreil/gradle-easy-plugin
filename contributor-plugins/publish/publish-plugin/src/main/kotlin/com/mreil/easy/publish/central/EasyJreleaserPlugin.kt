package com.mreil.easy.publish.central

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EnabledBy
import com.mreil.easy.codemeta.EasyCodemetaExtension
import com.mreil.easy.isEasyChildEnabled
import com.mreil.easy.publish.EasyPublishExtension
import com.mreil.easy.publish.RepoRouting
import com.mreil.easy.publish.isCentralEnabled
import com.mreil.easy.publish.publishExtension
import com.mreil.easy.semver.EasySemver
import com.mreil.easy.semver.EasySemverExtension
import org.gradle.api.Project

/**
 * Easy plugin that deploys staged Maven artifacts to Maven Central via JReleaser.
 *
 * It is contributed and applied via the easy plugin infrastructure:
 * - Discovered through [EasyPublishContributor] using the [java.util.ServiceLoader] SPI.
 * - Gated behind the [EasyPublishExtension] `easy.publish` extension, so it only
 *   becomes active when that extension is enabled (see [EnabledBy]).
 *
 * Deliberately NOT annotated with `ApplyToSubprojects`: the plugin stays root-only
 * (see `PluginRegistrar.targetsFor`), so it is applied to the root project only instead
 * of fanning out and no-op-ing on every subproject. The JReleaser wiring it owns
 * ([JreleaserConfigWiring], [JreleaserDeployWiring], [PublishAggregationWiring]) is
 * root-only by construction. The per-project Central wiring ([CentralPublishingWiring]:
 * `checkCentralPoms` / `stripSignatureChecksums`) is instead registered from the root via
 * a live `withId("maven-publish")` callback over `allprojects`, so every child that gets
 * `maven-publish` through the [EasyPublishPlugin] fan-out is wired without nested
 * `afterEvaluate` blocks — the enabled-gate is simply `maven-publish` being applied.
 *
 * Registers `generateJreleaserConfig` (YAML generation, gated on every central-enabled
 * project's `checkCentralPoms`), the root `publish` aggregation over subproject `publish`
 * tasks, and `publishToMavenCentral` (JReleaser `deploy`). The gate is ANY: wiring becomes
 * active when any project opts into Maven Central — a root `easy { publish { toMavenCentral() } }`
 * (inherited by every subproject) or a single subproject setting `toMavenCentral()` from its
 * own script. Unless that ANY condition holds, the plugin wires nothing. Versions with a
 * `-SNAPSHOT` pre-release are detected via the semver
 * API and skip JReleaser entirely; other pre-releases like `1.0.0-RC1` are treated as
 * deployable releases. The codemeta extension is required so the published POMs
 * carry the metadata Maven Central validates (url, scm, license, developers).
 */
@EnabledBy(EasyPublishExtension::class)
class EasyJreleaserPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        // toMavenCentral on the root is the fast path: it is inherited by every subproject
        // (DEEP convention), so wiring can proceed immediately (backwards compatible). When
        // the root is unset but a single subproject opts in from its own script, that value
        // is only settled once the subproject is evaluated — which happens after root's
        // afterEnabled — so the ANY check + wiring must be deferred until all projects are
        // evaluated. The remaining skip reasons (snapshot, missing codemeta) share the same
        // wiring skip + lifecycle-log behavior, factored into [skipReason] so this function
        // stays within detekt's ReturnCount limit.
        if (target.publishExtension()?.toMavenCentral?.get() == true) {
            wireIfCentral(target)
        } else {
            target.gradle.projectsEvaluated { wireIfCentral(target) }
        }
    }

    private fun wireIfCentral(target: Project) {
        if (target.allprojects.none { it.isCentralEnabled() }) return
        skipReason(target)?.let { reason ->
            target.logger.lifecycle(reason)
        } ?: run {
            // Per-project Central wiring (checkCentralPoms / stripSignatureChecksums) lives on
            // maven-publish adopters, not on this root-only plugin: iterate all projects and
            // wire each central-enabled one live as soon as maven-publish is applied
            // (EasyPublishPlugin's fan-out covers subprojects regardless of evaluation order).
            // No nested afterEvaluate.
            target.allprojects { project ->
                project.plugins.withId("maven-publish") { CentralPublishingWiring.wire(project) }
            }
            JreleaserConfigWiring.wire(target, propertyResolver)
            PublishAggregationWiring.wire(target)
            JreleaserDeployWiring.wire(target)
        }
    }

    /**
     * Returns a lifecycle message explaining why Maven Central wiring is being skipped,
     * or `null` when there is no reason to skip and wiring should proceed.
     *
     * Reasons, in order:
     * 1. **SNAPSHOT version** — JReleaser deploys only releases; snapshots go directly
     *    via `maven-publish` (`toSonatypeSnapshots`). The semver extension decides when
     *    enabled (only `-SNAPSHOT` pre-releases are skipped; other pre-releases like
     *    `1.0.0-RC1` are deployable releases); when semver is off, a `-SNAPSHOT` suffix
     *    fallback decides.
     * 2. **Missing codemeta extension** — Maven Central validates the POM's
     *    url/scm/license/developers fields; the codemeta overlay fills them from
     *    `codemeta.json`. Without it, wiring would produce POMs Central rejects at
     *    upload time — refuse early with an actionable hint.
     */
    private fun skipReason(target: Project): String? =
        when {
            isSnapshot(target) ->
                "Skipping Maven Central deploy: snapshot versions do not deploy to Maven Central. " +
                    "The semver extension is required for snapshot detection."
            !target.isEasyChildEnabled<EasyCodemetaExtension>() ->
                "Skipping Maven Central deploy: the codemeta extension is required to fill " +
                    "POM metadata that Maven Central validates (url, scm, license, developers). " +
                    "Enable it with `easy { codemeta { enabled.set(true) } }` and provide a codemeta.json."
            else -> null
        }

    private fun isSnapshot(target: Project): Boolean =
        RepoRouting.isSnapshot(
            runCatching {
                if (!target.isEasyChildEnabled<EasySemverExtension>()) null else EasySemver.of(target).orNull
            }.getOrNull(),
        ) ?: target.version.toString().endsWith("-SNAPSHOT")
}
