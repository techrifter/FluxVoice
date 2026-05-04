pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.5.0"
}

// ── Maven Central credentials ──────────────────────────────────────────────────
nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("sonatypeUsername")
        password = providers.gradleProperty("sonatypePassword")
        publishingType = "USER_MANAGED"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required by:
        //   • fluxvoice-android  → com.github.gkonovalov.android-vad:webrtc (VAD)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FluxVoice"
include(":app")
include(":fluxvoice-core")
include(":fluxvoice-android")
include(":fluxvoice-compose")
include(":fluxvoice-provider-llm")
include(":fluxvoice-stt-deepgram")
include(":fluxvoice-tts-cartesia")
