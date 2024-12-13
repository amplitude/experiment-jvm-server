plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version Versions.serializationPlugin
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version Versions.gradleNexusPublishPlugin
    id("org.jlleitschuh.gradle.ktlint") version Versions.kotlinLint
    id("org.jetbrains.dokka") version Versions.dokkaVersion
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:${Versions.mockito}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Versions.mockwebserver}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serializationRuntime}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okhttp}")
    implementation("com.squareup.okhttp3:okhttp-sse:${Versions.okhttpSse}")
    implementation("com.amplitude:evaluation-core:${Versions.evaluationCore}")
    implementation("com.amplitude:java-sdk:${Versions.amplitudeAnalytics}")
    implementation("org.json:json:${Versions.json}")
}

// Publishing

group = "com.amplitude"
version = "1.6.1"

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(System.getenv("SONATYPE_STAGING_PROFILE_ID"))
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    @Suppress("LocalVariableName")
    publications {
        create<MavenPublication>("sdk") {
            from(components["java"])
            pom {
                name.set("Experiment JVM Server SDK")
                description.set("Amplitude Experiment server-side SDK for JVM (Java, Kotlin)")
                url.set("https://github.com/amplitude/experiment-jvm-server")

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("amplitude")
                        name.set("Amplitude")
                        email.set("dev@amplitude.com")
                    }
                }
                scm {
                    url.set("https://github.com/amplitude/experiment-jvm-server")
                }
            }
        }
    }
}

signing {
    val publishing = extensions.findByType<PublishingExtension>()
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing?.publications)
}

tasks.withType<Sign>().configureEach {
    onlyIf { isReleaseBuild }
}

val isReleaseBuild: Boolean
    get() = System.getenv("SIGNING_KEY") != null
