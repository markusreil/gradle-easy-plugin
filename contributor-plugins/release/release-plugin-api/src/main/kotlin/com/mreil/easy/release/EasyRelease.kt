package com.mreil.easy.release

import com.mreil.easy.easyService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Public entry point for release lifecycle integration.
 *
 * Consumers only need to pass a [Project] instance. The shared service lookup is lazy and gated on
 * the release extension being enabled, so calls are safe regardless of contributor apply order.
 */
object EasyRelease {
    /**
     * Shared service registration name.
     *
     * The release plugin and this API must agree on this value.
     */
    const val SERVICE_NAME: String = "releaseLifecycle"

    /**
     * Lazily resolves the [ReleaseLifecycleService] registered by the release plugin.
     *
     * Never throws at call time; the returned [Provider] is absent when the release plugin is
     * disabled or not applied (`easy { release {} }`).
     */
    fun of(project: Project): Provider<ReleaseLifecycleService> = project.easyService(SERVICE_NAME, EasyReleaseExtension::class)

    /**
     * Registers a [ReleaseLifecycleListener] to run before the `preReleaseCommit` task.
     *
     * Uses lazy [Project.tasks.configureEach] configuration, so it is safe regardless of contributor
     * apply order or whether release is enabled — the action is never attached if `preReleaseCommit`
     * is never registered. The listener is passed as a [doFirst][org.gradle.api.Task.doFirst] action,
     * not stored as service state, keeping it configuration-cache compatible.
     *
     * Any files returned by the listener are added to the `preReleaseCommit` task's
     * [additionalFiles][PreReleaseCommitTaskSpec.additionalFiles] and committed together with the
     * version file.
     *
     * @param project the project whose release lifecycle should be observed.
     * @param listener the listener to invoke; must only capture CC-serializable state.
     */
    fun beforePreReleaseCommit(
        project: Project,
        listener: ReleaseLifecycleListener,
    ) {
        val releaseVersion = of(project).flatMap { it.releaseVersion() }
        project.tasks.configureEach { task ->
            if (task.name == "preReleaseCommit") {
                task.doFirst { t ->
                    val files = listener.beforePreReleaseCommit(releaseVersion)
                    if (files.isNotEmpty()) {
                        (t as? PreReleaseCommitTaskSpec)?.additionalFiles?.addAll(
                            files.map { it.absolutePath },
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns a [ReleaseLifecycleListener] that writes the resolved release version to [file].
     *
     * The listener is created in the API classloader, so it does not capture the build script object
     * and remains configuration-cache serializable. Use this helper when registering a listener from
     * a build script to avoid accidental script-reference captures.
     *
     * @param file the file to write to; must be CC-serializable (a plain [File]).
     * @param prefix text written before the release version.
     * @param suffix text written after the release version.
     * @return a listener that returns [file] so it is included in the pre-release commit.
     */
    fun writeToFileListener(
        file: File,
        prefix: String = "",
        suffix: String = "",
    ): ReleaseLifecycleListener =
        ReleaseLifecycleListener { releaseVersion ->
            val text = prefix + releaseVersion.get() + suffix
            val current = runCatching { file.readText() }.getOrElse { "" }
            if (current == text) return@ReleaseLifecycleListener emptyList()
            file.writeText(text)
            listOf(file)
        }
}
