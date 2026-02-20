import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version Versions.serializationPlugin
    id("com.vanniktech.maven.publish") version Versions.vanniktechMavenPublish
    id("org.jlleitschuh.gradle.ktlint") version Versions.kotlinLint
    id("org.jetbrains.dokka") version Versions.dokkaVersion
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:${Versions.mockito}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Versions.mockwebserver}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("io.github.cdimascio:dotenv-kotlin:${Versions.dotenvKotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serializationRuntime}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okhttp}")
    implementation("com.squareup.okhttp3:okhttp-sse:${Versions.okhttpSse}")
    implementation("com.amplitude:evaluation-core:${Versions.evaluationCore}")
    implementation("com.amplitude:java-sdk:${Versions.amplitudeAnalytics}")
    implementation("org.json:json:${Versions.json}")
}

// Publishing

group = "com.amplitude"
version = "1.8.0"

mavenPublishing {
    coordinates(
        group as String?,
        "experiment-jvm-server",
        version as String?
    )

    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            sourcesJar = true,
        )
    )

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

    publishToMavenCentral()
    signAllPublications()
}
