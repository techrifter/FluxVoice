# FluxVoice SDK consumer ProGuard rules
# Keep all public SDK interfaces so apps using R8 don't strip them.
-keep public interface com.techrifter.fluxvoice.core.** { *; }
-keep public class com.techrifter.fluxvoice.core.** { *; }
-keepclassmembers class com.techrifter.fluxvoice.core.** { *; }
