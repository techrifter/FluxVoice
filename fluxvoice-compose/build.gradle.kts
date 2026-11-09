plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.techrifter.fluxvoice.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    api(project(":fluxvoice-android"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.compose.material:material-icons-core")
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
            artifactId = "fluxvoice-compose"
            version    = libs.versions.fluxvoice.get()
            artifact(javadocStubJar)
            afterEvaluate { from(components["release"]) }

            pom {
                name = "FluxVoice Compose"
                description = "Jetpack Compose UI components for FluxVoice — drop-in animated voice interface with transcript display."
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
