plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("compiler-embeddable"))
}
