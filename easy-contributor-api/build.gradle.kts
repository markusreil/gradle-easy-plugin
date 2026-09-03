import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    jacoco
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "mreilComGradlePluginsSnapshots"
            url = uri("https://repo.mreil.com/gradle-plugins-snapshots")
            credentials(PasswordCredentials::class)
        }
    }
}
