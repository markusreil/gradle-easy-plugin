# easy-contributor-support

Plain Kotlin library (no Gradle plugin) for shared utilities. Currently empty — placeholder for non-API helpers that `easy-plugin-core` and consumers can depend on. Depends on `easy-contributor-api` for `PluginRegistry`/`EasyPluginContributor` types.

## Plugin & Extension Registration

Extra plugins (e.g., `publish-plugin:EasyPublishPlugin`) and custom extensions (e.g., `EasyPublishExtension`) are discovered **without manual `apply()`** via Java ServiceLoader:

1. **Implement the SPI** (in any project that depends on `easy-contributor-api`):
   ```kotlin
   class MyContributor : EasyPluginContributor {
       override fun projectPlugins() = setOf(MyProjectPlugin::class)
       override fun settingsPlugins() = setOf(MySettingsPlugin::class)
       override fun pluginExtensions() = setOf(MyExtension::class)
   }
   ```
   - `EasyPluginContributor` lives in `easy-contributor-api/src/main/kotlin/com/mreil/easy/EasyPluginContributor.kt`.
   - Contributed extensions implement `EasyPluginExtension` and must define a companion object implementing `Named` (e.g., `companion object : Named { override val name = "myExtension" }`).
   - Contributors can also optionally be annotated with `@ApplyToSubprojects` to apply their plugins across subprojects.

2. **Declare the service** — `src/main/resources/META-INF/services/com.mreil.easy.EasyPluginContributor`:
   ```
   com.example.MyContributor
   ```
   Or use `google/auto-service` (`@AutoService(EasyPluginContributor::class)`) to generate it.

3. **Depend on the contributor** — `easy-plugin/build.gradle.kts` has `implementation(project(":publish-plugin"))` (or consumer runtime classpath), so the service file is on the runtime classpath.

4. **Discovery at apply time** — `ProjectPlugin`/`SettingsPlugin` register the shared build service:
   ```kotlin
   val registry = gradle.sharedServices.registerIfAbsent(PluginRegistry.NAME, PluginRegistryService::class.java).get()
   registry.loadFromServiceLoader(javaClass.classLoader) // ServiceLoader.load(EasyPluginContributor::class.java)
   ```
   `PluginRegistryService` (`easy-plugin-core/.../PluginRegistryService.kt`) implements `PluginRegistry` (single build-service, `NAME = "easyPluginRegistry"`).

5. **Extension Registration & Configuration Copying**:
   - `ExtensionRegistrar` creates the top-level `EasyExtension` (`"easy"`) on `Settings` and `Project` and attaches all contributed `EasyPluginExtension` classes to `easy.extensions`.
   - In `SettingsPlugin`, conventions set in `settings.gradle.kts` (e.g., `easy { extensions.configure<MyExtension> { ... } }`) are automatically copied to the root `Project` via `ExtensionCopier`.
   - `ExtensionCopier` handles deep copying of Gradle `Property<*>`, `DomainObjectCollection<*>`, nested `CanBeCopied` objects, `ExtensionAware` child extensions, and mutable properties, respecting `@CopyMode` (`DEEP`, `READ_ONLY`, `NONE`).
   - **Subproject Extension Injection**: `ExtensionRegistrar.createExtension(project: Project, registry, parent)` (used by `ProjectPlugin`) mirrors `PluginRegistrar`'s `@ApplyToSubprojects` handling — when called on the root `Project`, it also creates `EasyExtension` copies in all subprojects (`orderedAllProjects` = `root` + `subprojects.sortedBy { path }`) via `ExtensionCopier` from the parent `easy` (typically `Settings` or root). The `ExtensionAware` overload (`createExtension(target: ExtensionAware, ...)`) does **not** inject; only the `Project` overload does. Currently the injection is **unguarded** (all registered extensions are injected) so a plugin applied to subprojects via `@ApplyToSubprojects` always finds its `easy.*` extension in the target — per-extension guarding can be added later alongside the `EnabledBy` lifecycle.

6. **Plugin Application via PluginRegistrar** — encapsulates deferred application and subproject targeting:
   - Project: `PluginRegistrar.applyPlugins(project, registry)` (delegates to `project.plugins.withType(ProjectPlugin::class.java)` and applies to subprojects if annotated with `@ApplyToSubprojects`).
   - Settings: `PluginRegistrar.applyPlugins(settings, registry)` (delegates to `settings.pluginManager.withPlugin(PluginIds.SETTINGS)`).
   No `newInstance().apply()` — Gradle instantiates via `pluginManager.apply(KClass.java)`.

Plugin IDs are the single source in `gradle.properties` (`plugin.project`/`plugin.settings`), mirrored at runtime in `easy-contributor-api/.../PluginIds.kt`.
