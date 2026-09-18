plugins {
    `java-library`
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-contributor-api"))
    implementation(project(":easy-contributor-support"))
    compileOnly(gradleApi())
}
