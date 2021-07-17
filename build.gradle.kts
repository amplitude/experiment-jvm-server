plugins {
    kotlin("jvm")
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version Versions.gradleNexusPublishPlugin
    id("org.jlleitschuh.gradle.ktlint") version Versions.ktlintVersion
    id("org.jetbrains.dokka") version Versions.dokkaVersion
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))

    implementation("com.squareup.okhttp3:okhttp:${Versions.okhttpVersion}")
    implementation("org.json:json:${Versions.jsonVersion}")
}

// Publishing

group = "com.amplitude"
version = "0.0.1"

java {
    withSourcesJar()
    withJavadocJar()
}

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(properties["sonatypeStagingProfileId"].toString())
            username.set(properties["sonatypeUsername"].toString())
            password.set(properties["sonatypePassword"].toString())
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {

                from(components["java"])

                pom {
                    name.set("Experiment JVM Server SDK")
                    description.set("Amplitude Experiment server-side SDK for JVM (Java, Kotlin)")
                    url.set("https://github.com/amplitude/experiment-java-server")

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
                        url.set("https://github.com/amplitude/experiment-java-server")
                    }
                }
            }
        }
    }

    signing {
        val publishing = extensions.findByType<PublishingExtension>()
        val signingKeyId = properties["signingKeyId"]?.toString()
        val signingKey = properties["signingKey"]?.toString()
        val signingPassword = properties["signingPassword"]?.toString()
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing?.publications)
    }

    tasks.withType<Sign>().configureEach {
        onlyIf { isReleaseBuild }
    }
}

val isReleaseBuild: Boolean
    get() = properties.containsKey("signingKey")
