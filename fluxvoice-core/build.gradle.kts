plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.techrifter.fluxvoice.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24   // broader SDK support
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
    explicitApi()   // enforces public/internal visibility on all SDK declarations
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

// AGP's bundled Dokka uses ASM8 which cannot read sealed classes (PermittedSubclasses).
// A stub javadoc JAR satisfies Maven Central's requirement without triggering the broken task.
val javadocStubJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId    = "com.techrifter.fluxvoice"
            artifactId = "fluxvoice-core"
            version    = libs.versions.fluxvoice.get()
            artifact(javadocStubJar)
            afterEvaluate { from(components["release"]) }

            pom {
                name = "FluxVoice Core"
                description = "Core interfaces, state types, and provider contracts for the FluxVoice voice AI SDK."
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
