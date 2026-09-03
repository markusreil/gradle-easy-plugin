# gradle-plugin-testutils

Test helpers for Gradle functional tests (TestKit). The project helpers live in
`com.mreil.easy.test.project`, the AssertJ assertions in
`com.mreil.easy.test.project.assertj`. Together they let each test declare a throwaway Gradle build,
run it with `GradleRunner`, and assert on the result — without manual temp-dir handling.

## Setup

Annotate the test class and declare a field; the extension creates a fresh project
per test and deletes its directory afterwards:

```kotlin
@ExtendWith(GradleTestProjectExtension::class)
class MyFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `something works`() {
        // ...
    }
}
```

## Declaring the build

Declare everything in one `configure { }` block, then run `build(...)` (success)
or `buildAndFail(...)` (expected failure):

```kotlin
project.configure {
    buildGradle(
        """
        plugins {
            `java-library`
            id("com.mreil.easy.test.publish")
        }
        group = "com.example"
        version = "1.0.0"
        """.trimIndent(),
    )
    javaSource()
}

val result = project.build("publish", "--info")
assertThat(result.output).contains("mavenStaging")
```

Available declarations (all relative to the project directory):

| Helper | Writes |
|---|---|
| `settings(content)` | `settings.gradle.kts` (empty by default) |
| `group` / `version` | `gradle.properties` coordinates (`com.example` / `1.0.0` by default, applied to all projects; `null` omits the key) |
| `buildGradle(content)` | `build.gradle.kts` |
| `file(path, content)` | arbitrary file, creating parent dirs |
| `file(path)` | handle only — no I/O, for reads and assertions |
| `createDir(path)` | empty directory, eagerly |
| `javaSource(packageName = "com.example", className = "Hello")` | `src/main/java/.../<Class>.java` with empty class body |
| `kotlinSource(packageName = "com.example", className, body = "class ...")` | `src/main/kotlin/.../<Class>.kt` |

## Lazy files

File contents are **staged, not written**: declaring the same path twice keeps the
last content, and nothing touches disk until the build runs (`runner()` flushes
everything first) or a resolving `file(path)` read happens. Staged entries are
[templates](src/main/kotlin/com/mreil/easy/test/project/template) rendered at flush
time — raw strings via `file(path, content)`, and a `GradlePropertiesTemplate`
behind the `group` / `version` properties. Tests that assert on
setup files without running a build can call `flushPendingFiles()` explicitly
(same-module use).

## Multi-project builds

```kotlin
project.configure {
    settings(
        """
        rootProject.name = "root"
        include(":child")
        """.trimIndent(),
    )
    buildGradle("""...""")
    createChild {
        buildGradle("""...""")
        javaSource("com.example", "Child")
    }
}
```

`createChild(name = "child") { ... }` creates (or reuses) the child and configures
it in one go. Builds always run from the root project.

## System properties for the TestKit JVM

The build runs in a separate TestKit process, so `System.setProperty` in the test
does not reach it. Stage properties instead — they are forwarded as `-D` arguments:

```kotlin
project.configure {
    systemProperty("easy.disableAllPlugins", "true")
    buildGradle("""...""")
}
```

For the `easy.disableAllPlugins` isolation mode specifically, the property must
additionally be set on the test JVM via JUnit Pioneer's
`@SetSystemProperty(key = "easy.disableAllPlugins", value = "true")` on the test
method — the child-side `systemProperty(...)` alone is not enough.

## Probe tasks

Probe tasks observe build state (extensions, tasks, publications) by printing
`KEY=value` lines. Declare them with `probeTask`, interpolate the generated
script into the build file, and assert the output afterwards:

```kotlin
val publishProbe = probeTask("verifyPublish") {
    expect("HAS_PUBLISH", "tasks.findByName(\"publish\") != null", "true")
}
project.configure {
    buildGradle(
        """
        plugins {
            `java-library`
            id("com.mreil.easy.test.publish")
        }
        easy { publish { enabled.set(true) } }
        ${publishProbe.script()}
        """.trimIndent(),
    )
}

val result = project.build("verifyPublish")
assertSoftly { softly ->
    publishProbe.assertOutput(softly, result.output)
}
```

Every `expect` needs a matching assertion — `assertOutput` checks all of them,
collecting failures. Probe expressions are opaque build-script strings; keep them
read-only (no file writes, no model mutation).

Common shapes have typed shortcuts that compose with `expect` in one task:

```kotlin
val probe = probeTask("verifyJreleaserTask") {
    prelude("val task = tasks.findByName(\"generateJreleaserConfig\") as? GenerateJreleaserConfigTask")
    taskExists("ROOT_HAS_TASK", "generateJreleaserConfig")
    taskExists("CHILD_HAS_TASK", "generateJreleaserConfig", expected = false, inProject = ":child")
    extensionExists("HAS_EASY", "easy")
    extensionExistsByType("HAS_EASY", "com.mreil.easy.EasyExtension")
    expect("TASK_ENABLED", "task?.enabled ?: false", "true")
}
```

- `taskExists(key, task, expected = true, inProject = null)` — `tasks.findByName(...) != null`, optionally in a subproject
- `extensionExists(key, name, expected = true)` — `project.extensions.findByName(...) != null`
- `extensionExistsByType(key, type, expected = true)` — `findByType(...::class.java) != null` from a fully qualified name
- `prelude(vararg lines)` — shared `val` lookups emitted before the probes
- `expectAbsent(key, expression, value)` — asserts a `KEY=value` line is missing

Expected values are captured eagerly, so compute derived paths (e.g. from
`project.file(...).invariantSeparatorsPath`) *before* declaring the probe.
Tasks with conditional output (`if (...) println ...`) stay handwritten —
a single generated `tasks.register` block can't express branches.

## Asserting published artifacts
The package-level `assertSoftly { ... }` (soft, same call shape as AssertJ's) adds Maven-aware
assertions. Replace the `SoftAssertions.assertSoftly` import with
`com.mreil.easy.test.project.assertj.assertSoftly`; all standard assertions keep working:

```kotlin
val coordinates = MavenCoordinates(name = project.projectDir.name)

assertSoftly { softly ->
    softly.assertThat(result.output).contains("publish")
    softly.assertThat(project).hasArtifact(repoDir, coordinates)
    softly.assertThat(project).doesNotHaveArtifact(snapshotDir, coordinates)
    softly.assertThat(project).hasMavenMetadata(snapshotDir, coordinates)
    softly.assertThat(project).doesNotHaveMavenMetadata(releaseDir, coordinates)
}
```

`hasArtifact` resolves release versions to their exact repository path
(`<repo>/<group>/<name>/<version>/<name>-<version>[-<classifier>].<extension>`,
see `mavenArtifact(...)`); snapshot versions (`*-SNAPSHOT`) are resolved by
scanning the version directory for a timestamped jar, so no build can pass with
a wrong timestamp. `MavenCoordinates` also supports `classifier` and custom
`extension` (e.g. `coordinates.copy(extension = "pom")`).

## Asserting pom contents

`hasPom(repo, coordinates)` asserts the pom exists and returns a `PomAssert`
for XML-based content checks (chained checks participate in soft collection):

```kotlin
softly.assertThat(project)
    .hasPom(repoDir, markerCoordinates)
    .hasGroupId("com.example.myplugin")
    .hasArtifactId("com.example.myplugin.gradle.plugin")
    .hasVersion("1.0.0")
    .hasDependency("com.example", project.projectDir.name, "1.0.0")
```

`hasGroupId` / `hasArtifactId` / `hasVersion` check the `/project` coordinates,
`hasDependency(group, artifact, version)` checks a `/project/dependencies`
entry, and `hasText(fragment)` is the raw-substring escape hatch.
