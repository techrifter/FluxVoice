package com.techrifter.fluxvoice.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── FluxVoice Signature Theme Foundation (Deep Obsidian Space) ───────────────
val ObsidianVoid          = Color(0xFF080B16) // Midnight carbon void with warm violet undertone
val ObsidianSurface       = Color(0xFF0F0E17) // Studio desk & container canvas
val ObsidianCard          = Color(0xFF151322) // Elevated interactive component card
val ObsidianFloat         = Color(0xFF1C1A2E) // Floating action controls & docked pills
val ObsidianGlass         = Color(0xD90E0D16) // Frosted acrylic overlay

// ── Studio Precision Hairlines & Specular Sheen ──────────────────────────────
val HairlineBorder        = Color(0x1AFFFFFF) // 10% platinum hairline for crisp audio-hardware edges
val HairlineHighlight     = Color(0x4000B4D8) // 25% signature radiant azure edge
val HairlineFlame         = Color(0x40F97316) // 25% sunset flame active edge
val GlassShine            = Color(0x0FFFFFFF) // Subtle specular surface reflection

// ── FluxVoice Iconic Spectral Gradient System (From Brand Logo) ─────────────
// Derived directly from the official FluxVoice ribbon emblem & audio wave wings
val FluxAzure             = Color(0xFF00B4D8) // Left audio waveform & high-frequency acoustic wave
val FluxFuchsia           = Color(0xFFD946EF) // Primary brand accent & "Voice" gradient start
val FluxMagenta           = Color(0xFFC026D3) // Deep vocal resonance
val FluxRose              = Color(0xFFF43F5E) // Neon rose ("Voice" gradient center & interruption)
val FluxSunsetOrange      = Color(0xFFF97316) // Radiant flame ("Voice" gradient end & right audio wave)
val FluxAmber             = Color(0xFFFB923C) // Warm solar amber (VAD activity & attention)
val FluxViolet            = Color(0xFF8B5CF6) // Harmonic reasoning / neural synthesis
val FluxEmerald           = Color(0xFF10B981) // Hardware Keystore verified & sub-250ms green

// Backward-compatible semantic bindings
val FluxCyan              = FluxAzure
val FluxBlue              = Color(0xFF3B82F6)
val FluxIndigo            = FluxViolet
val FluxCyanGlow          = Color(0x3300B4D8)

// ── Primary Brand Gradients ──────────────────────────────────────────────────
val FluxGradient = Brush.horizontalGradient(
    listOf(FluxFuchsia, FluxRose, Color(0xFFFB7185))
)

val FluxFullSpectrum = Brush.linearGradient(
    listOf(FluxAzure, FluxViolet, FluxFuchsia, FluxRose, Color(0xFFFB7185))
)

val FluxOrbCore = Brush.radialGradient(
    colors = listOf(
        Color(0xFFE879F9), // Luminous fuchsia core
        FluxMagenta,       // Rich deep magenta
        Color(0xFF4C1D95), // Deep midnight violet
        Color.Transparent
    )
)

// ── Core Typography Hierarchy ────────────────────────────────────────────────
val TextPureWhite         = Color(0xFFFFFFFF) // Primary text, headings & prominent metrics
val TextSlateMedium       = Color(0xFFA1A1AA) // Secondary prose, descriptions & unit labels
val TextSlateMuted        = Color(0xFF71717A) // Monospace tokens, timestamps & captions
val TextSlateDark         = Color(0xFF52525B) // Subtle hints, dividers & quiet markers

// ── Master Accents ───────────────────────────────────────────────────────────
val AccentBlue            = FluxAzure         // Primary high-energy accent
val AccentAzure           = FluxAzure         // High-speed acoustic indicator
val AccentViolet          = FluxViolet        // Thinking / reasoning state
val AccentGreen           = FluxEmerald       // Connected / Verified
val AccentAmber           = FluxAmber         // Warning / Setup required
val AccentRose            = FluxRose          // Interruption / Speech cut-off
