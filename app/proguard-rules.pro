# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for Play Console crash deobfuscation
-keepattributes SourceFile,LineNumberTable

# Hide original source file names in stack traces
-renamesourcefileattribute SourceFile

# ── ElevenLabs ConvAI SDK ──────────────────────────────────────────────────────
-keep class io.elevenlabs.** { *; }
-keep interface io.elevenlabs.** { *; }
# LiveKit (WebRTC; used internally by the ElevenLabs Android SDK)
-keep class io.livekit.** { *; }
-keep interface io.livekit.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# ── Kotlinx Serialization & DTOs ──────────────────────────────────────────────
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keep class com.techrifter.fluxvoice.data.remote.provider.**.dto.** { *; }

# ── Room & Persistence ────────────────────────────────────────────────────────
-keep class androidx.room.** { *; }
-keep class com.techrifter.fluxvoice.data.local.db.** { *; }

# ── WebRTC VAD (Native JNI) ──────────────────────────────────────────────────
-keep class com.konovalov.vad.** { *; }

# ── Domain & Telemetry Models ─────────────────────────────────────────────────
-keep class com.techrifter.fluxvoice.domain.model.** { *; }
-keep class com.techrifter.fluxvoice.core.model.** { *; }

