plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.techrifter.fluxvoice.stt.deepgram"
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
    implementation(libs.kotlinx.coroutines.android)
    // OkHttp for WebSocket streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val javadocStubJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId    = "com.techrifter.fluxvoice"
            artifactId = "fluxvoice-stt-deepgram"
            version    = libs.versions.fluxvoice.get()
            artifact(javadocStubJar)
            afterEvaluate { from(components["release"]) }

            pom {
                name = "FluxVoice STT — Deepgram"
                description = "Deepgram real-time speech-to-text provider for FluxVoice — streaming transcription via WebSocket."
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
