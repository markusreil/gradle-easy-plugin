package com.mreil.easy.vcs

import org.gradle.api.file.DirectoryProperty

internal object VcsGit {
    fun remoteUrl(rootDir: DirectoryProperty): String? {
        val url = upstreamRemoteUrl(rootDir) ?: originRemoteUrl(rootDir) ?: firstRemoteUrl(rootDir)
        return url?.let(::normalizeRemoteUrl)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun run(
        rootDir: DirectoryProperty,
        vararg args: String,
    ): ProcessResult =
        try {
            val command = listOf("git", *args)
            val process = ProcessBuilder(command).directory(rootDir.get().asFile).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            ProcessResult(exit, output.lines().filter { it.isNotBlank() })
        } catch (e: Exception) {
            ProcessResult(-1, emptyList())
        }

    private fun upstreamRemoteUrl(rootDir: DirectoryProperty): String? {
        val upstream =
            run(rootDir, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}").output.firstOrNull() ?: return null
        return run(rootDir, "remote", "get-url", upstream.substringBefore('/')).output.firstOrNull()
    }

    private fun originRemoteUrl(rootDir: DirectoryProperty): String? = run(rootDir, "remote", "get-url", "origin").output.firstOrNull()

    private fun firstRemoteUrl(rootDir: DirectoryProperty): String? {
        val remote = run(rootDir, "remote").output.firstOrNull() ?: return null
        return run(rootDir, "remote", "get-url", remote).output.firstOrNull()
    }

    private fun normalizeRemoteUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.replace(Regex("^git@([^:]+):"), "https://$1/")
        return normalized.removeSuffix(".git").ifBlank { null }
    }
}

internal data class ProcessResult(
    val exit: Int,
    val output: List<String>,
)
