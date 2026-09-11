package com.mreil.easy.vcs

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.process.ExecOutput

@Suppress("TooManyFunctions")
internal class VcsGit(
    private val providers: ProviderFactory,
    private val rootDir: DirectoryProperty,
) : VcsOperations {
    override fun remoteUrl(): Provider<String> =
        providers.provider {
            sequenceOf(
                upstreamRemoteUrl(),
                originRemoteUrl(),
                firstRemoteUrl(),
            ).map { it.get() }
                .firstOrNull { it.isNotEmpty() }
                ?.let(::normalizeRemoteUrl)
                .orEmpty()
        }

    override fun branch(): Provider<String> = firstLine("rev-parse", "--abbrev-ref", "HEAD")

    override fun currentSha(): Provider<String> = firstLine("rev-parse", "HEAD")

    override fun isClean(): Provider<Boolean> = firstLine("status", "--porcelain").map { it.isEmpty() }

    override fun isUpToDateWithRemote(): Provider<Boolean> =
        providers.provider {
            val output = output("rev-list", "--count", "HEAD..@{u}")
            output.result.get().exitValue == 0 &&
                output.standardOutput.asText
                    .get()
                    .trim() == "0"
        }

    override fun push(): Provider<Boolean> = success("push")

    override fun push(tag: String): Provider<Boolean> = success("push", "origin", "HEAD", tag)

    override fun fetch(): Provider<Boolean> = success("fetch")

    @Suppress("SpreadOperator")
    override fun addAndCommit(
        paths: List<String>,
        message: String,
    ): Provider<Boolean> =
        providers.provider {
            val addSuccess = output("add", "--", *paths.toTypedArray()).result.get().exitValue == 0
            addSuccess && output("commit", "-m", message).result.get().exitValue == 0
        }

    override fun tag(name: String): Provider<Boolean> = success("tag", name)

    private fun firstLine(vararg args: String): Provider<String> =
        output(*args)
            .standardOutput
            .asText
            .map { text -> text.lines().firstOrNull { it.isNotBlank() }.orEmpty() }

    private fun success(vararg args: String): Provider<Boolean> = output(*args).result.map { it.exitValue == 0 }

    private fun output(vararg args: String): ExecOutput =
        providers.exec { spec ->
            spec.commandLine("git", *args)
            spec.workingDir = rootDir.get().asFile
            spec.isIgnoreExitValue = true
        }

    private fun upstreamRemoteUrl(): Provider<String> =
        providers.provider {
            val upstream =
                firstLine("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
                    .get()
                    .takeIf { it.isNotEmpty() } ?: return@provider ""
            firstLine("remote", "get-url", upstream.substringBefore('/')).get()
        }

    private fun originRemoteUrl(): Provider<String> = firstLine("remote", "get-url", "origin")

    private fun firstRemoteUrl(): Provider<String> =
        providers.provider {
            val remote = firstLine("remote").get().takeIf { it.isNotEmpty() } ?: return@provider ""
            firstLine("remote", "get-url", remote).get()
        }

    private fun normalizeRemoteUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val normalized = trimmed.replace(Regex("^git@([^:]+):"), "https://$1/")
        return normalized.removeSuffix(".git").ifBlank { "" }
    }
}
