package com.auracam.camera.domain

import kotlinx.coroutines.flow.StateFlow

interface CameraEngine {
    val cameraMode: StateFlow<CameraMode>
    val currentLens: StateFlow<LensFacing>
    val zoomRatio: StateFlow<Float>
    val availableZoomPresets: StateFlow<List<Float>>
    val flashMode: StateFlow<FlashMode>
    val aspectRatio: StateFlow<AspectRatio>
    val colorProfile: StateFlow<ColorProfile>
    val captureFormat: StateFlow<CaptureFormat>
    val photoResolution: StateFlow<PhotoResolution>
    val videoResolution: StateFlow<VideoResolution>
    val slowMotionSpeed: StateFlow<SlowMotionSpeed>
    val timelapseInterval: StateFlow<TimelapseInterval>
    val timerDuration: StateFlow<TimerDuration>
    val gridType: StateFlow<GridType>
    val proSettings: StateFlow<ProSettings>
    val hardwareQualityStatus: StateFlow<HardwareQualityStatus>
    val manualControlsSupported: StateFlow<Boolean>
    val videoStabilizationSupported: StateFlow<Boolean>
    val videoStabilizationEnabled: StateFlow<Boolean>
    val exposureMask: StateFlow<ExposureMask>
    val liveHistogram: StateFlow<HistogramData>
    val horizonLeveler: StateFlow<HorizonLeveler>
    val captureProgress: StateFlow<CaptureProgress>
    val isRecording: StateFlow<Boolean>
    val recordingDurationSeconds: StateFlow<Int>
    val focusPoint: StateFlow<FocusPoint?>
    val watermarkEnabled: StateFlow<Boolean>
    val ultraHdrEnabled: StateFlow<Boolean>
    val geotaggingEnabled: StateFlow<Boolean>
    val recentMedia: StateFlow<CapturedMedia?>
    val galleryList: StateFlow<List<CapturedMedia>>
    val dualVlogLayout: StateFlow<DualVlogLayout>
    val isDualStreamSwapped: StateFlow<Boolean>
    val dualVlogPipRect: StateFlow<NormalizedRect>
    val trackedSubjects: StateFlow<List<TrackedSubject>>
    val subjectTrackingEnabled: StateFlow<Boolean>

    fun setMode(mode: CameraMode)
    fun setLens(lens: LensFacing)
    fun setZoom(zoom: Float)
    fun setFlash(flash: FlashMode)
    fun setAspectRatio(ratio: AspectRatio)
    fun setColorProfile(profile: ColorProfile)
    fun setCaptureFormat(format: CaptureFormat)
    fun setPhotoResolution(resolution: PhotoResolution)
    fun setVideoResolution(resolution: VideoResolution)
    fun setSlowMotionSpeed(speed: SlowMotionSpeed)
    fun setTimelapseInterval(interval: TimelapseInterval)
    fun setTimer(timer: TimerDuration)
    fun setGrid(grid: GridType)
    fun updateProSettings(transform: (ProSettings) -> ProSettings)
    fun setFocusPoint(x: Float, y: Float)
    fun clearFocusPoint()
    fun toggleWatermark(enabled: Boolean)
    fun toggleUltraHdr(enabled: Boolean)
    fun setGeotaggingEnabled(enabled: Boolean)
    fun setVideoStabilizationEnabled(enabled: Boolean)
    fun setDualVlogLayout(layout: DualVlogLayout)
    fun swapDualStreams()
    fun setDualVlogPipRect(rect: NormalizedRect)
    fun setSubjectTrackingEnabled(enabled: Boolean)
    suspend fun refreshGallery()
    suspend fun loadExif(media: CapturedMedia): CapturedMedia
    suspend fun deleteMedia(media: CapturedMedia): Boolean
    suspend fun capturePhoto(): CapturedMedia
    suspend fun toggleVideoRecording()

    fun release()
}

expect class PlatformCameraEngine() : BaseCameraEngine
