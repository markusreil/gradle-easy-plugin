plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    api(gradleTestKit())
    api(libs.junit.jupiter.api)
    api(libs.assertj.core)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
