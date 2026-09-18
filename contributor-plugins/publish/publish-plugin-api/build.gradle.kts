plugins {
    `java-library`
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-contributor-api"))
    compileOnly(gradleApi())
}
