package com.mreil.easy.projectdefaults

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import com.mreil.utils.GradlePropertiesLocator
import com.mreil.utils.hasGroup
import com.mreil.utils.hasVersion
import org.gradle.api.Project
import java.io.File

/**
 * Easy plugin that enforces project conventions and applies the `base` plugin.
 *
 * Applies `base` eagerly to every project (lifecycle tasks like `clean`/`build`/`check`)
 * and fails fast when `group` or `version` are missing. The `version` value is
 * additionally traced to its declaring `gradle.properties` (project directory up
 * to the root directory, first hit wins) so a future release plugin knows exactly
 * which file to rewrite; the lookup is lenient and never fails the build.
 * Gated behind [EasyProjectDefaultsExtension], so both behaviors are skipped
 * when the extension is disabled.
 */
@EnabledBy(EasyProjectDefaultsExtension::class)
@ApplyToSubprojects
class EasyProjectDefaultsPlugin : AbstractEasyProjectPlugin() {
    override fun init(target: Project) {
        target.pluginManager.apply("base")
    }

    override fun afterEnabled(target: Project) {
        checkGroup(target)
        checkVersion(target)
    }

    private fun checkGroup(target: Project) {
        if (!target.hasGroup()) {
            error("Project group must be set (e.g. group = \"com.example\" in gradle.properties)")
        }
    }

    private fun checkVersion(target: Project) {
        val version = target.version.toString()
        if (!target.hasVersion()) {
            val searched = propertiesDirs(target).map { File(it, "gradle.properties") }
            error(
                "Project version must be set (e.g. version = \"1.0.0\" in gradle.properties; " +
                    "searched: ${searched.joinToString()})",
            )
        }
        val declaringFile = GradlePropertiesLocator.locateDeclaringFile(propertiesDirs(target), "version")
        if (declaringFile != null) {
            target.logger.debug("Project version {} declared in {}", version, declaringFile)
        } else {
            target.logger.debug(
                "Project version {} is not declared in any project gradle.properties " +
                    "(source: command line or build script)",
                version,
            )
        }
    }

    private fun propertiesDirs(target: Project): List<File> =
        generateSequence(target.projectDir) { dir ->
            dir.parentFile?.takeIf { dir != target.rootDir }
        }.toList()
}
