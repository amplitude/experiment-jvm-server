plugins {
    kotlin("jvm") version "1.5.20"
}

group = "com.amplitude"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("org.json:json:20210307")

    testImplementation(kotlin("test"))
}
