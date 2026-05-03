plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.techrifter.fluxvoice.tts.cartesia"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":fluxvoice-core"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}

val javadocStubJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId    = "com.techrifter.fluxvoice"
            artifactId = "fluxvoice-tts-cartesia"
            version    = libs.versions.fluxvoice.get()
            artifact(javadocStubJar)
            afterEvaluate { from(components["release"]) }

            pom {
                name = "FluxVoice TTS — Cartesia"
                description = "Cartesia Sonic text-to-speech provider for FluxVoice — low-latency streaming audio synthesis."
                url = "https://github.com/techrifter/fluxvoice"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "techrifter"
                        name = "Sahil"
                        email = "ms.sahilmemon@gmail.com"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/techrifter/fluxvoice.git"
                    developerConnection = "scm:git:ssh://git@github.com/techrifter/fluxvoice.git"
                    url = "https://github.com/techrifter/fluxvoice"
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey")?.toString()
    val signingPassword = findProperty("signingPassword")?.toString()
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
