package com.auracam.camera.domain

import kotlinx.serialization.Serializable

@Serializable
enum class CameraMode(
    val displayName: String,
    val badgeText: String,
    val hasDedicatedPipeline: Boolean
) {
    NIGHT_SIGHT("Night Sight", "NIGHT", true),
    PORTRAIT("Portrait", "PORTRAIT", false),
    PHOTO("Photo", "PHOTO", true),
    VIDEO("Video", "VIDEO", true),
    SLOW_MOTION("Slow Motion", "SLOW MO", true),
    TIME_LAPSE("Time Lapse", "TIMELAPSE", true),
    DUAL_VLOG("Dual Vlog", "DUAL", true),
    CINEMATIC("Cinematic", "CINEMA", false),
    PRO("Pro / Expert", "PRO", true),
    ASTRO("Astrophotography", "ASTRO", false),
    LONG_EXPOSURE("Long Exposure", "LONG EXPO", false),
    PANORAMA("Panorama", "PANO", false);

    val singleFrameNotice: String?
        get() = if (hasDedicatedPipeline) null else "$displayName saves a single exposure in this build"
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
    ULTRA_HDR("Ultra HDR", "jpg");

    val isRaw: Boolean
        get() = this == RAW_DNG || this == RAW_PLUS_JPEG
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
    NATURAL("Natural", "True to life neutral tones"),
    REAL_TONE("Real Tone", "Google Real Tone skin tone accuracy"),
    VIBRANT("Vibrant", "Punchy rich color saturation"),
    CINEMATIC_WARM("Cinematic", "Warm golden hour film grade"),
    HIGH_CONTRAST_MONO("B&W Mono", "Deep high-contrast monochrome"),
    VINTAGE_FILM("Vintage", "Analog retro warm fade"),
    COOL_BREEZE("Cool Tone", "Crisp modern cool blues"),
    ASTRO_BOOST("Astro Boost", "Deep night sky star boost"),
    CLEAN_DOC("Doc Clean", "High contrast document text enhancement")
}

@Serializable
enum class PhotoResolution(
    val label: String,
    val shortBadge: String,
    val description: String,
    val width: Int,
    val height: Int
) {
    HIGH_50MP("50MP (Full)", "50MP", "8160 x 6120 • Full Sensor Detail", 8160, 6120),
    STANDARD_12MP("12MP (Quad)", "12MP", "4080 x 3060 • 4-in-1 Pixel Binned", 4080, 3060),
    SAVER_8MP("8MP (Saver)", "8MP", "3264 x 2448 • Fast Storage Saver", 3264, 2448)
}

@Serializable
enum class VideoResolution(
    val label: String,
    val shortBadge: String,
    val fps: Int,
    val width: Int,
    val height: Int
) {
    UHD_4K_60("4K 60fps", "4K 60", 60, 3840, 2160),
    UHD_4K_30("4K 30fps", "4K 30", 30, 3840, 2160),
    FHD_1080P_60("1080p 60fps", "1080p 60", 60, 1920, 1080),
    FHD_1080P_30("1080p 30fps", "1080p 30", 30, 1920, 1080),
    HD_720P_30("720p 30fps", "720p", 30, 1280, 720)
}

@Serializable
enum class SlowMotionSpeed(
    val label: String,
    val shortLabel: String,
    val fps: Int,
    val multiplier: Float,
    val description: String
) {
    SPEED_1_4X("1/4x (120 fps)", "1/4x", 120, 0.25f, "Action & Sports • Standard HFR"),
    SPEED_1_8X("1/8x (240 fps)", "1/8x", 240, 0.125f, "Water & Fast Motion • High HFR"),
    SPEED_1_16X("1/16x (480 fps)", "1/16x", 480, 0.0625f, "Super Slow-Mo • Ultra High Precision")
}

@Serializable
enum class TimelapseInterval(
    val label: String,
    val shortLabel: String,
    val speedMultiplier: Int,
    val intervalSeconds: Float,
    val idealFor: String
) {
    SPEED_5X("5x (0.5s)", "5x", 5, 0.5f, "Walking & Street Scenes"),
    SPEED_10X("10x (1s)", "10x", 10, 1.0f, "Traffic & Crowds"),
    SPEED_30X("30x (3s)", "30x", 30, 3.0f, "Clouds & Sunsets"),
    SPEED_120X("120x (12s)", "120x", 120, 12.0f, "Sun Tracking & Shadows"),
    NIGHT_LAPSE("Night (30s)", "Night", 300, 30.0f, "Star Trails & Dark Sky")
}

@Serializable
enum class DualVlogLayout(val label: String) {
    PIP_RECT("PiP Rect"),
    PIP_CIRCLE("PiP Circle"),
    SPLIT_50_50("50/50 Split"),
    SIDE_BY_SIDE("Side by Side")
}
