# codemeta-plugin (com.mreil.easy.codemeta)

Easy plugin that will generate a `codemeta.json` file.

It is a *contributor plugin*: discovered via `EasyPluginContributor` SPI (`EasyCodemetaContributor`) and applied through the shared easy plugin infrastructure. It only activates on projects that enable the `easy.codemeta` extension.

## Current state

Basic plugin structure only - no file generation yet. The plugin is gated behind `easy.codemeta` (`EasyCodemetaExtension` / `DefaultEasyCodemetaExtension`).

## Structure

* `codemeta-plugin-api` — public `EasyCodemetaExtension` interface
* `codemeta-plugin` — `DefaultEasyCodemetaExtension` (`@PublicType`), `EasyCodemetaPlugin` (`@EnabledBy`), `EasyCodemetaContributor` + `META-INF/services`
* `codemeta-test-plugin` — harness `com.mreil.easy.test.codemeta` (`CodemetaTestHarnessPlugin` applying `ProjectPlugin`) + functional test `CodemetaFuncTest`

## Usage

```kotlin
plugins {
    `java-library`
    id("com.mreil.easy.project")
}

easy {
    codemeta {}
}
```
