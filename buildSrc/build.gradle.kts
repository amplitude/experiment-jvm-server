plugins {
    kotlin("jvm") version "1.5.20"
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