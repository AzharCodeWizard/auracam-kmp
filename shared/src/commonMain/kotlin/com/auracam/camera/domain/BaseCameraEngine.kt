package com.auracam.camera.domain

import com.auracam.processing.ComputationalPipeline
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.math.sin
import kotlin.random.Random

abstract class BaseCameraEngine(
    protected val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val simulateSensors: Boolean = true
) : CameraEngine {
    protected val _cameraMode = MutableStateFlow(CameraMode.PHOTO)
    override val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    protected val _currentLens = MutableStateFlow(LensFacing.BACK_WIDE)
    override val currentLens: StateFlow<LensFacing> = _currentLens.asStateFlow()

    protected val _zoomRatio = MutableStateFlow(1.0f)
    override val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    protected val _availableZoomPresets = MutableStateFlow(listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f))
    override val availableZoomPresets: StateFlow<List<Float>> = _availableZoomPresets.asStateFlow()

    protected val _flashMode = MutableStateFlow(FlashMode.OFF)
    override val flashMode: StateFlow<FlashMode> = _flashMode.asStateFlow()

    protected val _aspectRatio = MutableStateFlow(AspectRatio.RATIO_4_3)
    override val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    protected val _colorProfile = MutableStateFlow(ColorProfile.REAL_TONE)
    override val colorProfile: StateFlow<ColorProfile> = _colorProfile.asStateFlow()

    protected val _captureFormat = MutableStateFlow(CaptureFormat.JPEG)
    override val captureFormat: StateFlow<CaptureFormat> = _captureFormat.asStateFlow()

    protected val _photoResolution = MutableStateFlow(PhotoResolution.STANDARD_12MP)
    override val photoResolution: StateFlow<PhotoResolution> = _photoResolution.asStateFlow()

    protected val _videoResolution = MutableStateFlow(VideoResolution.FHD_1080P_30)
    override val videoResolution: StateFlow<VideoResolution> = _videoResolution.asStateFlow()

    protected val _slowMotionSpeed = MutableStateFlow(SlowMotionSpeed.SPEED_1_4X)
    override val slowMotionSpeed: StateFlow<SlowMotionSpeed> = _slowMotionSpeed.asStateFlow()

    protected val _timelapseInterval = MutableStateFlow(TimelapseInterval.SPEED_10X)
    override val timelapseInterval: StateFlow<TimelapseInterval> = _timelapseInterval.asStateFlow()

    protected val _timerDuration = MutableStateFlow(TimerDuration.OFF)
    override val timerDuration: StateFlow<TimerDuration> = _timerDuration.asStateFlow()

    protected val _gridType = MutableStateFlow(GridType.RULE_OF_THIRDS)
    override val gridType: StateFlow<GridType> = _gridType.asStateFlow()

    protected val _proSettings = MutableStateFlow(ProSettings())
    override val proSettings: StateFlow<ProSettings> = _proSettings.asStateFlow()

    protected val _hardwareQualityStatus = MutableStateFlow(HardwareQualityStatus())
    override val hardwareQualityStatus: StateFlow<HardwareQualityStatus> = _hardwareQualityStatus.asStateFlow()

    protected val _manualControlsSupported = MutableStateFlow(true)
    override val manualControlsSupported: StateFlow<Boolean> = _manualControlsSupported.asStateFlow()

    protected val _videoStabilizationSupported = MutableStateFlow(false)
    override val videoStabilizationSupported: StateFlow<Boolean> = _videoStabilizationSupported.asStateFlow()

    protected val _videoStabilizationEnabled = MutableStateFlow(true)
    override val videoStabilizationEnabled: StateFlow<Boolean> = _videoStabilizationEnabled.asStateFlow()

    protected val _exposureMask = MutableStateFlow(ExposureMask())
    override val exposureMask: StateFlow<ExposureMask> = _exposureMask.asStateFlow()

    protected val _liveHistogram = MutableStateFlow(HistogramData())
    override val liveHistogram: StateFlow<HistogramData> = _liveHistogram.asStateFlow()

    protected val _horizonLeveler = MutableStateFlow(HorizonLeveler())
    override val horizonLeveler: StateFlow<HorizonLeveler> = _horizonLeveler.asStateFlow()

    protected val _captureProgress = MutableStateFlow(CaptureProgress())
    override val captureProgress: StateFlow<CaptureProgress> = _captureProgress.asStateFlow()

    protected val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    protected val _recordingDurationSeconds = MutableStateFlow(0)
    override val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    protected val _focusPoint = MutableStateFlow<FocusPoint?>(null)
    override val focusPoint: StateFlow<FocusPoint?> = _focusPoint.asStateFlow()

    protected val _watermarkEnabled = MutableStateFlow(true)
    override val watermarkEnabled: StateFlow<Boolean> = _watermarkEnabled.asStateFlow()

    protected val _ultraHdrEnabled = MutableStateFlow(true)
    override val ultraHdrEnabled: StateFlow<Boolean> = _ultraHdrEnabled.asStateFlow()

    protected val _geotaggingEnabled = MutableStateFlow(false)
    override val geotaggingEnabled: StateFlow<Boolean> = _geotaggingEnabled.asStateFlow()

    protected val _recentMedia = MutableStateFlow<CapturedMedia?>(null)
    override val recentMedia: StateFlow<CapturedMedia?> = _recentMedia.asStateFlow()

    protected val _galleryList = MutableStateFlow<List<CapturedMedia>>(emptyList())
    override val galleryList: StateFlow<List<CapturedMedia>> = _galleryList.asStateFlow()

    private var recordingJob: Job? = null
    private var sensorSimulationJob: Job? = null

    init {
        if (simulateSensors) startSensorSimulation()
    }

    private fun startSensorSimulation() {
        sensorSimulationJob = coroutineScope.launch {
            var tick = 0f
            while (isActive) {
                tick += 0.05f
                val pro = _proSettings.value
                val evShift = pro.evBias * 3
                val rBins = List(32) { i ->
                    val peak = 14 + (sin(tick + i * 0.2f) * 6).toInt() + evShift.toInt()
                    val dist = kotlin.math.abs(i - peak.coerceIn(2, 29))
                    (100 - dist * 8 + Random.nextInt(15)).coerceIn(10, 100)
                }
                val gBins = List(32) { i ->
                    val peak = 16 + (sin(tick * 1.2f + i * 0.18f) * 5).toInt() + evShift.toInt()
                    val dist = kotlin.math.abs(i - peak.coerceIn(2, 29))
                    (110 - dist * 9 + Random.nextInt(15)).coerceIn(10, 100)
                }
                val bBins = List(32) { i ->
                    val peak = 18 + (sin(tick * 0.8f + i * 0.22f) * 6).toInt() + evShift.toInt()
                    val dist = kotlin.math.abs(i - peak.coerceIn(2, 29))
                    (95 - dist * 7 + Random.nextInt(15)).coerceIn(10, 100)
                }
                val lumBins = List(32) { i ->
                    (rBins[i] * 0.299f + gBins[i] * 0.587f + bBins[i] * 0.114f).toInt()
                }

                _liveHistogram.value = HistogramData(rBins, gBins, bBins, lumBins)

                val roll = sin(tick * 0.3f) * 1.8f
                val isLevel = kotlin.math.abs(roll) < 0.6f
                _horizonLeveler.value = HorizonLeveler(
                    rollDegrees = roll,
                    pitchDegrees = sin(tick * 0.2f) * 0.8f,
                    isLevel = isLevel
                )

                delay(100)
            }
        }
    }

    override fun setMode(mode: CameraMode) {
        _cameraMode.value = mode
        if (mode == CameraMode.PRO) {
            _proSettings.value = _proSettings.value.copy(focusPeakingEnabled = true)
        }
    }

    override fun setLens(lens: LensFacing) {
        _currentLens.value = lens
        _zoomRatio.value = lens.zoomBase
        _availableZoomPresets.value = if (lens == LensFacing.FRONT) {
            listOf(1.0f, 1.4f, 2.0f)
        } else {
            listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f)
        }
    }

    override fun setZoom(zoom: Float) {
        _zoomRatio.value = zoom.coerceIn(0.5f, 20.0f)
        if (_currentLens.value != LensFacing.FRONT) {
            if (zoom < 0.8f) _currentLens.value = LensFacing.BACK_ULTRA_WIDE
            else if (zoom < 1.8f) _currentLens.value = LensFacing.BACK_WIDE
            else if (zoom < 4.0f) _currentLens.value = LensFacing.BACK_TELEPHOTO
            else _currentLens.value = LensFacing.BACK_SUPER_TELE
        }
    }

    override fun setFlash(flash: FlashMode) {
        _flashMode.value = flash
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio.value = ratio
    }

    override fun setColorProfile(profile: ColorProfile) {
        _colorProfile.value = profile
    }

    override fun setCaptureFormat(format: CaptureFormat) {
        _captureFormat.value = format
    }

    override fun setPhotoResolution(resolution: PhotoResolution) {
        _photoResolution.value = resolution
    }

    override fun setVideoResolution(resolution: VideoResolution) {
        _videoResolution.value = resolution
    }

    override fun setSlowMotionSpeed(speed: SlowMotionSpeed) {
        _slowMotionSpeed.value = speed
    }

    override fun setTimelapseInterval(interval: TimelapseInterval) {
        _timelapseInterval.value = interval
    }

    override fun setTimer(timer: TimerDuration) {
        _timerDuration.value = timer
    }

    override fun setGrid(grid: GridType) {
        _gridType.value = grid
    }

    override fun updateProSettings(transform: (ProSettings) -> ProSettings) {
        _proSettings.value = transform(_proSettings.value)
    }

    override fun setFocusPoint(x: Float, y: Float) {
        val now = Clock.System.now().toEpochMilliseconds()
        _focusPoint.value = FocusPoint(x, y, timestamp = now)
    }

    override fun clearFocusPoint() {
        _focusPoint.value = null
    }

    override fun toggleWatermark(enabled: Boolean) {
        _watermarkEnabled.value = enabled
    }

    override fun toggleUltraHdr(enabled: Boolean) {
        _ultraHdrEnabled.value = enabled
    }

    override fun setGeotaggingEnabled(enabled: Boolean) {
        _geotaggingEnabled.value = enabled
    }

    override fun setVideoStabilizationEnabled(enabled: Boolean) {
        _videoStabilizationEnabled.value = enabled
    }

    override suspend fun refreshGallery() = Unit

    override suspend fun loadExif(media: CapturedMedia): CapturedMedia = media

    override suspend fun deleteMedia(media: CapturedMedia): Boolean {
        val remaining = _galleryList.value.filterNot { it.id == media.id }
        val changed = remaining.size != _galleryList.value.size
        _galleryList.value = remaining
        if (_recentMedia.value?.id == media.id) _recentMedia.value = remaining.firstOrNull()
        return changed
    }

    override suspend fun capturePhoto(): CapturedMedia {
        val timerSec = _timerDuration.value.seconds
        if (timerSec > 0) {
            for (s in timerSec downTo 1) {
                _captureProgress.value = CaptureProgress(CaptureState.ALIGNING_FRAMES, (timerSec - s + 1f) / timerSec, "Timer: ${s}s")
                delay(1000)
            }
        }

        ComputationalPipeline.processCapture(
            mode = _cameraMode.value,
            lens = _currentLens.value,
            zoom = _zoomRatio.value,
            proSettings = _proSettings.value,
            colorProfile = _colorProfile.value,
            captureFormat = _captureFormat.value,
            watermarkEnabled = _watermarkEnabled.value,
            ultraHdr = _ultraHdrEnabled.value
        ).collect { progress ->
            _captureProgress.value = progress
        }

        val timestamp = Clock.System.now().toEpochMilliseconds()
        val exif = ComputationalPipeline.generateExif(
            mode = _cameraMode.value,
            lens = _currentLens.value,
            zoom = _zoomRatio.value,
            proSettings = _proSettings.value,
            captureFormat = _captureFormat.value,
            ultraHdr = _ultraHdrEnabled.value,
            capturedAtEpochMillis = timestamp
        )
        val media = CapturedMedia(
            id = "IMG_${timestamp}",
            uri = "content://media/external/images/media/${timestamp}",
            fileName = "PXL_${timestamp}.${_captureFormat.value.extension}",
            timestamp = timestamp,
            width = if (_aspectRatio.value == AspectRatio.RATIO_1_1) 4080 else if (_aspectRatio.value == AspectRatio.RATIO_16_9) 4080 else 4080,
            height = if (_aspectRatio.value == AspectRatio.RATIO_1_1) 4080 else if (_aspectRatio.value == AspectRatio.RATIO_16_9) 2295 else 3072,
            format = _captureFormat.value,
            mode = _cameraMode.value,
            exif = exif,
            simulatedPreviewSeed = (timestamp % 1000).toInt()
        )

        _recentMedia.value = media
        _galleryList.value = listOf(media) + _galleryList.value

        delay(500)
        _captureProgress.value = CaptureProgress(CaptureState.IDLE, 0f, "")
        return media
    }

    override fun release() {
        recordingJob?.cancel()
        recordingJob = null
        sensorSimulationJob?.cancel()
        sensorSimulationJob = null
        _isRecording.value = false
        _recordingDurationSeconds.value = 0
        coroutineScope.cancel()
    }

    override suspend fun toggleVideoRecording() {
        if (_isRecording.value) {
            recordingJob?.cancel()
            _isRecording.value = false
            _recordingDurationSeconds.value = 0
        } else {
            _isRecording.value = true
            _recordingDurationSeconds.value = 0
            recordingJob = coroutineScope.launch {
                while (isActive) {
                    delay(1000)
                    _recordingDurationSeconds.value += 1
                }
            }
        }
    }
}
