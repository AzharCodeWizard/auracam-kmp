package com.auracam.processing

import com.auracam.camera.domain.ColorProfile

/**
 * Mathematical Color Matrices for AuraCam Live Tone Filters.
 * Follows standard 4x5 ColorMatrix representation:
 * [ a, b, c, d, e,
 *   f, g, h, i, j,
 *   k, l, m, n, o,
 *   p, q, r, s, t ]
 * Where output RGB values are:
 * R' = a*R + b*G + c*B + d*A + e
 * G' = f*R + g*G + h*B + i*A + j
 * B' = k*R + l*G + m*B + n*A + o
 * A' = p*R + q*G + r*B + s*A + t
 */
object ToneFilterMatrix {

    val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    /**
     * Google Real Tone: Skin tone melanin depth preservation, highlight rolloff protection,
     * and warm neutral spectral balance.
     */
    val REAL_TONE_MATRIX = floatArrayOf(
        1.05f, 0.02f, 0.00f, 0f, 4f,
        0.02f, 0.98f, 0.00f, 0f, 2f,
        0.00f, 0.00f, 0.92f, 0f, -2f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Vibrant: Punchy color saturation (1.35x saturation boost) with rich skies and foliage.
     */
    val VIBRANT_MATRIX = floatArrayOf(
        1.28f, -0.25f, -0.03f, 0f, 0f,
        -0.07f, 1.25f, -0.03f, 0f, 0f,
        -0.07f, -0.25f, 1.32f, 0f, 0f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Cinematic Warm: Golden hour 35mm film grade with amber midtones, teal shadow split-tone,
     * and warm highlight glow.
     */
    val CINEMATIC_WARM_MATRIX = floatArrayOf(
        1.14f, 0.04f, -0.04f, 0f, 10f,
        0.02f, 1.02f, -0.02f, 0f, 4f,
        -0.05f, -0.02f, 0.88f, 0f, -8f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * High Contrast Monochrome: Pure luminance conversion with 1.35x contrast expansion.
     * Rec.709 Luminance coefficients: 0.2126 R + 0.7152 G + 0.0722 B adjusted for contrast.
     */
    val MONOCHROME_MATRIX = floatArrayOf(
        0.35f, 0.70f, 0.15f, 0f, -15f,
        0.35f, 0.70f, 0.15f, 0f, -15f,
        0.35f, 0.70f, 0.15f, 0f, -15f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Vintage Film: Analog retro warm fade with lifted shadow floor and sepia warmth.
     */
    val VINTAGE_FILM_MATRIX = floatArrayOf(
        0.90f, 0.10f, 0.05f, 0f, 15f,
        0.05f, 0.85f, 0.05f, 0f, 10f,
        0.02f, 0.08f, 0.75f, 0f, 20f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Cool Breeze: Modern crisp Nordic blue grade with cyan midtones and deep navy shadows.
     */
    val COOL_BREEZE_MATRIX = floatArrayOf(
        0.92f, 0.00f, 0.02f, 0f, -4f,
        0.00f, 0.98f, 0.04f, 0f, 0f,
        0.02f, 0.04f, 1.15f, 0f, 8f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Astro Boost: Deep night sky dark level compression and nebular spectrum enhancement.
     */
    val ASTRO_BOOST_MATRIX = floatArrayOf(
        1.20f, 0.00f, 0.10f, 0f, -12f,
        0.00f, 1.10f, 0.05f, 0f, -12f,
        0.05f, 0.05f, 1.30f, 0f, -8f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Clean Doc: Document text contrast enhancement with black clipping and paper white expansion.
     */
    val CLEAN_DOC_MATRIX = floatArrayOf(
        1.30f, 0.00f, 0.00f, 0f, -25f,
        0.00f, 1.30f, 0.00f, 0f, -25f,
        0.00f, 0.00f, 1.30f, 0f, -25f,
        0.00f, 0.00f, 0.00f, 1f, 0f
    )

    /**
     * Retrieve the 4x5 Color Matrix corresponding to a [ColorProfile].
     */
    fun colorMatrixFor(profile: ColorProfile): FloatArray = when (profile) {
        ColorProfile.NATURAL -> IDENTITY_MATRIX
        ColorProfile.REAL_TONE -> REAL_TONE_MATRIX
        ColorProfile.VIBRANT -> VIBRANT_MATRIX
        ColorProfile.CINEMATIC_WARM -> CINEMATIC_WARM_MATRIX
        ColorProfile.HIGH_CONTRAST_MONO -> MONOCHROME_MATRIX
        ColorProfile.VINTAGE_FILM -> VINTAGE_FILM_MATRIX
        ColorProfile.COOL_BREEZE -> COOL_BREEZE_MATRIX
        ColorProfile.ASTRO_BOOST -> ASTRO_BOOST_MATRIX
        ColorProfile.CLEAN_DOC -> CLEAN_DOC_MATRIX
    }
}
