package com.auracam.ui.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val REQUIRED_PERMISSIONS = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraGranted by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    var microphoneGranted by remember {
        mutableStateOf(context.isGranted(Manifest.permission.RECORD_AUDIO))
    }
    var locationGranted by remember {
        mutableStateOf(context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    var hasRequested by remember { mutableStateOf(false) }
    var shouldShowRationale by remember {
        mutableStateOf(activity.shouldShowRationale(Manifest.permission.CAMERA))
    }

    fun refresh() {
        cameraGranted = context.isGranted(Manifest.permission.CAMERA)
        microphoneGranted = context.isGranted(Manifest.permission.RECORD_AUDIO)
        locationGranted = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        shouldShowRationale = activity.shouldShowRationale(Manifest.permission.CAMERA)
    }

    val refreshState = rememberUpdatedState(::refresh)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState.value.invoke()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequested = true
        refresh()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    return remember(
        activity, cameraGranted, microphoneGranted, locationGranted, hasRequested, shouldShowRationale
    ) {
        object : CameraPermissionState {
            override val cameraGranted = cameraGranted
            override val microphoneGranted = microphoneGranted
            override val locationGranted = locationGranted
            override val hasRequested = hasRequested
            override val shouldShowRationale = shouldShowRationale

            override fun request() {
                val missing = REQUIRED_PERMISSIONS.filterNot { context.isGranted(it) }
                if (missing.isEmpty()) {
                    hasRequested = true
                    refresh()
                    return
                }
                launcher.launch(missing.toTypedArray())
            }

            override fun requestLocation() {
                if (context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    refresh()
                    return
                }
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            override fun openAppSettings() {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Activity?.shouldShowRationale(permission: String) =
    this != null && ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
