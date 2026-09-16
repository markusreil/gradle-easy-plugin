@file:Suppress("TooManyFunctions")

package com.mreil.gradletest.project

import java.io.File
import java.nio.file.Files

/** Runs `git` with [args] in the directory at [workDir], failing the test when git exits non-zero. */
fun runGit(
    workDir: String,
    vararg args: String,
) {
    val process = gitProcess(File(workDir), args)
    val exitCode = process.waitFor()
    check(exitCode == 0) { "git ${args.joinToString(" ")} failed with exit code $exitCode" }
}

/** Runs `git` with [args] in [workDir]; returns trimmed stdout, failing on a non-zero exit. */
fun gitOutput(
    workDir: String,
    vararg args: String,
): String {
    val process = gitProcess(File(workDir), args)
    val output =
        process.inputStream
            .bufferedReader()
            .readText()
            .trim()
    check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed with exit code ${process.exitValue()}" }
    return output
}

/** Returns true when [ref] resolves to a commit in [workDir]. */
fun gitRefExists(
    workDir: String,
    ref: String,
): Boolean = gitProcess(File(workDir), arrayOf("rev-parse", "--verify", "--quiet", "$ref^{commit}")).waitFor() == 0

/** Initializes [project] as a git repository. */
fun gitInit(project: GradleTestProject) {
    runGit(project.projectDir.absolutePath, "init")
}

/** Initializes [project] on `main` with a bare `origin` remote; returns the remote directory. */
fun initGitWithRemote(project: GradleTestProject): File {
    val projectDir = project.projectDir.absolutePath
    project.file("build.gradle.kts")
    runGit(projectDir, "init", "-b", "main")
    runGit(projectDir, "config", "user.email", "test@example.com")
    runGit(projectDir, "config", "user.name", "Test")
    runGit(projectDir, "add", "-A")
    runGit(projectDir, "commit", "-m", "initial")
    val remoteDir = Files.createTempDirectory("git-remote-").toFile()
    runGit(remoteDir.absolutePath, "init", "--bare", "-b", "main")
    runGit(projectDir, "remote", "add", "origin", remoteDir.absolutePath)
    runGit(projectDir, "push", "-u", "origin", "main")
    return remoteDir
}

/** Last commit subject of [project]. */
fun gitLastMessage(project: GradleTestProject): String = gitOutput(project.projectDir.absolutePath, "log", "-1", "--format=%s")

/** `git rev-parse [ref]` in [project]. */
fun gitRevParse(
    project: GradleTestProject,
    ref: String,
): String = gitOutput(project.projectDir.absolutePath, "rev-parse", ref)

/** Files changed by `HEAD` in [project]. */
fun gitChangedFiles(project: GradleTestProject): List<String> =
    gitOutput(project.projectDir.absolutePath, "show", "--name-only", "--format=", "HEAD")
        .lines()
        .filter { it.isNotBlank() }

/** Returns true when [tag] exists in [project]. */
fun gitTagExists(
    project: GradleTestProject,
    tag: String,
): Boolean = gitRefExists(project.projectDir.absolutePath, tag)

/**
 * Advances the bare [remote] with a commit the local repo does not know about (via a
 * throwaway clone), so the next `git push` from the project is a non-fast-forward that
 * gets rejected. Returns the remote `main` sha.
 */
fun advanceRemote(remote: File): String {
    val cloneDir = Files.createTempDirectory("rollback-clone-").toFile()
    runGit(remote.parentFile.absolutePath, "clone", remote.absolutePath, cloneDir.absolutePath)
    runGit(cloneDir.absolutePath, "config", "user.email", "test@example.com")
    runGit(cloneDir.absolutePath, "config", "user.name", "Test")
    runGit(cloneDir.absolutePath, "commit", "--allow-empty", "-m", "remote-only commit")
    runGit(cloneDir.absolutePath, "push", "origin", "main")
    return gitOutput(remote.absolutePath, "rev-parse", "refs/heads/main")
}

private fun gitProcess(
    workDir: File,
    args: Array<out String>,
): Process =
    ProcessBuilder(listOf("git") + args)
        .directory(workDir)
        .redirectErrorStream(true)
        .start()
