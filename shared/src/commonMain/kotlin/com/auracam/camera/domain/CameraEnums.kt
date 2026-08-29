package com.auracam.camera.domain

import kotlinx.serialization.Serializable

@Serializable
enum class CameraMode(val displayName: String, val badgeText: String) {
    NIGHT_SIGHT("Night Sight", "NIGHT"),
    PORTRAIT("Portrait", "PORTRAIT"),
    PHOTO("Photo", "PHOTO"),
    VIDEO("Video", "VIDEO"),
    CINEMATIC("Cinematic", "CINEMA"),
    PRO("Pro / Expert", "PRO"),
    ASTRO("Astrophotography", "ASTRO"),
    LONG_EXPOSURE("Long Exposure", "LONG EXPO"),
    PANORAMA("Panorama", "PANO")
}

@Serializable
enum class LensFacing(val label: String, val zoomBase: Float) {
    BACK_ULTRA_WIDE("0.5", 0.5f),
    BACK_WIDE("1.0", 1.0f),
    BACK_TELEPHOTO("2.0", 2.0f),
    BACK_SUPER_TELE("5.0", 5.0f),
    FRONT("1.0", 1.0f)
}

@Serializable
enum class FlashMode(val title: String) {
    OFF("Off"),
    AUTO("Auto"),
    ON("On"),
    TORCH("Torch")
}

@Serializable
enum class AspectRatio(val label: String, val ratioWidth: Float, val ratioHeight: Float) {
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_FULL("Full", 19.5f, 9f)
}

@Serializable
enum class TimerDuration(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    SEC_3(3, "3s"),
    SEC_10(10, "10s")
}

@Serializable
enum class CaptureFormat(val label: String, val extension: String) {
    JPEG("JPEG", "jpg"),
    RAW_DNG("RAW (DNG)", "dng"),
    RAW_PLUS_JPEG("RAW + JPEG", "dng+jpg"),
    ULTRA_HDR("Ultra HDR", "jpg")
}

@Serializable
enum class GridType(val label: String) {
    NONE("Off"),
    RULE_OF_THIRDS("3x3"),
    GOLDEN_RATIO("Golden Ratio"),
    SQUARE("Square")
}

@Serializable
enum class ColorProfile(val label: String, val description: String) {
    NATURAL("Natural", "Pixel True Color reproduction"),
    VIBRANT("Vibrant", "Enhanced dynamic range and punchy tones"),
    REAL_TONE("Real Tone", "Google Real Tone skin-tone accuracy"),
    HIGH_CONTRAST_MONO("B&W Mono", "Deep blacks and crisp highlights"),
    CINEMATIC_WARM("Cinematic", "Warm golden-hour cinema LUT"),
    ASTRO_BOOST("Astro Boost", "Enhanced nebula and stellar contrast")
}
