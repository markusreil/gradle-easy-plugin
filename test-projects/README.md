# test-projects

Standalone manual smoke-test projects for dogfooding snapshots published to `mreilComGradlePluginsSnapshots` (`https://repo.mreil.com/gradle-plugins-snapshots`). They are **not** part of the main multi-project build (`settings.gradle.kts` does not `include` them) and must be built in isolation.

## Projects

* `simple/` — minimal single-module project that applies the **project** plugin from the latest snapshot:
  ```kotlin
  // settings.gradle.kts
  pluginManagement {
    repositories {
      gradlePluginPortal()
      maven { url = uri("https://repo.mreil.com/gradle-plugins-snapshots") } // no credentials
      mavenCentral()
    }
  }
  // build.gradle.kts
  plugins {
    id("com.mreil.easy.project") version "latest.integration"
  }
  ```
* `simple-settings/` — same as `simple` but for the **settings** plugin (`id("com.mreil.easy.settings") version "latest.integration"` in `settings.gradle.kts`; `build.gradle.kts` only has `java`).

  Both have their own Gradle wrapper (`gradle/wrapper/` + `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=21`) copied from the root so `./gradlew` works inside the folder. No source is required — empty build proves plugin resolution and application.

## Usage

Prerequisite: a snapshot has been published (plugin publications are created by `java-gradle-plugin`; `easy-plugin/build.gradle.kts` only adds the `publishing.repositories.maven` for snapshots). Publish with credentials:

```bash
./gradlew :easy-plugin:publish
# or locally for offline testing:
./gradlew :easy-plugin:publishToMavenLocal  # then add mavenLocal() to simple/settings.gradle.kts temporarily
```

Run the smoke tests (daemon provisioning via `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=21` auto-detects/provisions JDK 21, so Java 17 client works):

```bash
cd test-projects/simple
./gradlew help --info | grep easy
./gradlew build

cd ../simple-settings
./gradlew help --info | grep easy
./gradlew build
```

The root build is not affected:

```bash
./gradlew build  # from repo root, ignores test-projects/
```

## Adding more

* Keep projects committed (small `build`/`.gradle` are ignored via `.gitignore`). Do not add them to the root `settings.gradle.kts`.

## Troubleshooting

* `latest.integration` requires `maven-metadata.xml` in the snapshot repo — it fails with “could not resolve …:latest.integration” until a snapshot is deployed. Use `mavenLocal()` as a temporary fallback after `publishToMavenLocal`.
* Plugin variant requires JVM 21; thanks to `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`) the daemon auto-detects/provisions JDK 21, so a Java 17 client still works if a JDK 21 is installed (or auto-downloaded). Without that file you’d need `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
