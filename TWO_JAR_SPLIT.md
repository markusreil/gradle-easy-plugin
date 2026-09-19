# Two-Jar Plugin Split — Settings & Project

Status: **Complete** — shipped in `0.0.116` (Gradle Plugin Portal + Maven Central).

> This document is a durable, tracked record. An earlier untracked working copy was lost after the
> split merged; the decisions, verification strategy and open risks are reproduced here.

## Problem / root cause

One fat jar (`:easy-plugin`) registered **both** plugin IDs. Applying the settings plugin put the
whole jar — including project contributors and `KotlinTargetWiring`, which links KGP — on the
settings classpath. KGP is loaded by the project buildscript classloader (a child), so the settings
loader could not see it:

```
java.lang.NoClassDefFoundError: org/jetbrains/kotlin/gradle/dsl/KotlinJvmExtension
    at com.mreil.easy.jvm.kotlin.KotlinTargetWiring.configure(KotlinTargetWiring.kt:30)
```

TestKit (`withPluginClasspath()`) flattens the classpath and **masked** the bug; it reproduced only
in a real published consumer.

## Target model

| Artifact | Plugin ID | Contains |
|---|---|---|
| `easy-plugin` | `com.mreil.easy.project` | core/api/support/utils + all project contributors (incl. KGP wiring) |
| `easy-plugin-settings` | `com.mreil.easy.settings` | core/api/support/utils + the `jvm-defaults` settings slice (Dokka opt-in) |

Shared core (`easy-plugin-core`/api/support/utils) is bundled into **both** jars; scope membership
is enforced mechanically by per-jar `verifyShadowPackaging`.

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Two `java-gradle-plugin` + Shadow marker modules: `:easy-plugin` (project) and `:easy-plugin-settings` (settings). | `java-gradle-plugin` derives marker POMs/descriptors/`pluginUnderTestMetadata` from the module's single main component. Renaming `easy-plugin` → `easy-plugin-project` is deferred. |
| D2 | `ProjectPluginEntryPoint` / `SettingsPluginEntryPoint` bases in core; concrete `ProjectPlugin` / `SettingsPlugin` in their marker modules; `PluginRegistrar` triggers on `withType(ProjectPluginEntryPoint)`. | `loadFromServiceLoader(javaClass.classLoader)` uses the **caller's** loader. If the entry point lived in core (bundled in both jars), parent-first would load it from the settings classpath and the project plugin would discover only settings contributors. |
| D3 | Shared core stays scope-neutral and is bundled into both jars; per-jar `verifyShadowPackaging` asserts membership. | A shared Maven dependency would still land on both classpaths and adds release plumbing. |
| D4 | New `contributor-plugins/jvm-defaults/jvm-defaults-settings-plugin` (settings-only); `jvm-defaults-plugin` is project/KGP-only. | Shadow include/exclude can't split a contributor returning both `projectPlugins()` and `settingsPlugins()` — `loadFromServiceLoader` eagerly invokes all accessors. |
| D5 | Each module registers exactly one ID; publishing/markers derive from root group/version + project name. | Bundled-only modules set `easy.publish.enabled = false`. |
| D6 | Version each ID where it is applied: settings ID in `settings.gradle.kts`, project ID in the root `build.gradle.kts`. The `apply false` / versionless workaround is **unsupported**. | Separate artifacts make each ID a normal versioned request. The old idiom re-puts the project artifact on the settings classpath, where KGP is not visible. |
| D7 | Do **not** adopt the reflective `KotlinTargetWiring` workaround. | The split is the root-cause fix. Reflection would mask the E2E's raw `NoClassDefFoundError`, lose compile-time type safety and need brittle string reflection + test stubs. Failing on the unsupported `apply false` idiom is documented and acceptable. |

## Steps (all complete)

1. Extract entry-point bases in core; move concrete markers to their modules; update `PluginRegistrar`.
2. Add `jvm-defaults-settings-plugin`; split contributor + service files; move the Dokka settings slice and shared constants (`jvm-defaults-plugin-api`).
3. Add `:easy-plugin-settings`; strip settings registration from `:easy-plugin`; exclude `-settings-plugin` from its auto-collect; per-jar `verifyShadowPackaging` assertions.
4. Migrate docs and the repo dogfood (see below). The planned harness dep swap (`:easy-plugin-core` → `:easy-plugin`) was **reverted** — it pulls the whole project contributor set and breaks harness isolation.
5. Codify the published-consumer E2E guard (`:e2e-published`).
6. Port reflective `6d6b77ef` — implemented, then **reverted** (see D7).

## Verification strategy

- **Published-consumer E2E** `:e2e-published:test` is the only test that reproduces the settings/project classloader boundary (TestKit flattens it). It stages both markers to local Maven repos, applies settings in `settings.gradle.kts` and project in the root build with KGP + `java.targetVersion=11`, and asserts `jvmTarget=11` / `-Xjdk-release=11`. Green on Java 17 and 21; runs in the normal `check`/`build`.
- **Per-jar `verifyShadowPackaging`** enforces scope membership (settings jar: no `KotlinTargetWiring` / `EasyJvmDefaultsPlugin` / project contributors; project jar: no settings classes) and third-party publication.
- **Dokka** covered in both integration modes: settings opt-in (`easy { jvmDefaults { dokkaJavadoc() } }`) and consumer-applied `dokka-javadoc` with only the project plugin.
- The published plugin is a **Java-17 variant** (`org.gradle.jvm.version=17`, bytecode 61), so no Java-21 CI job is needed.
- The repo dogfoods the released split: settings ID in `settings.gradle.kts`, project ID in the root build, with the project-scope `easy { publish { … } }` config moved to the root build.

## Risk register

| # | Risk | Status / disposition |
|---|------|----------------------|
| 1 | Two-jar classloader assumption unproven | **Closed** — proven by `:e2e-published` against snapshot and Portal releases. |
| 2 | Entry-point classloader fragility (D2): moving `apply` logic into core would silently restore the bug | **Mitigated** — marker modules hold the concrete classes; comment + E2E guard. |
| 3 | Harness dependency mechanics | **Closed** — harnesses keep `:easy-plugin-core` + `ProjectPluginEntryPoint`; E2E covers the real topology. |
| 4 | `verifyShadowPackaging` duplication / POM scoping false confidence | **Hardened** — per-jar assertions extended to forbid serialization/semver4j on the settings marker and require them on the project marker. |
| 5 | Dogfood breakage on version bump | **Resolved** — merged to `main` as #30: root applies the project plugin; `easy { publish.enabled = false }` module blocks resolve again. |
| 6 | Settings POM declares unused `commons-configuration2` (bundled `gradle-plugin-utils`) | **Accepted** — `easy-plugin-core`/support use `com.mreil.utils` (`isRoot`, `PropertyResolver`); fix is the deferred `PropertyResolver`/utils extraction. |
| 7 | Disabled `release` registers a semver-coupled service → configuration-cache store failure on invalid/absent version | **Fixed** — release's internal `ReleaseStateService` registration moved out of `init()` into the enabled-gated `afterEnabled()`; regression test covers a disabled release + enabled semver + invalid version under CC. |
| 8 | Planning/risk doc was untracked and lost | **Resolved** — this tracked document. |

## Deferred (not in this effort)

- Publish a shared `easy-plugin-core` Maven dependency instead of bundling it in both jars (D3 option b).
- Drop the redundant `-api` publishes (bundled *and* published today).
- Rename `easy-plugin` → `easy-plugin-project`.
- Extract `PropertyResolver` (would drop `commons-configuration2` from the settings POM).
- Reconcile the other unmerged branches (`fix-KGP-classloader-issues`, `split-settings-and-project-plugin`, `central-kgp-application`, `fix-double-action`, `add-central-publishing`).
