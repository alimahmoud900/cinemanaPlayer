package com.cinemana.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemana.player.ui.AppViewModel
import com.cinemana.player.ui.CinemanaApp
import com.cinemana.player.ui.CinemanaTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Let fullscreen video run under the notch / camera cut-out.
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        addOnPictureInPictureModeChangedListener(Consumer<PictureInPictureModeChangedInfo> { info ->
            vm.setPip(info.isInPictureInPictureMode)
            // PiP window dismissed with the X: the activity is no longer visible -> stop playback.
            if (!info.isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
                vm.onHostStopped()
            }
        })

        setContent {
            CinemanaTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val fullscreen = ui.fullscreen && ui.playerVisible
                LaunchedEffect(fullscreen) { applyFullscreen(fullscreen) }
                CinemanaApp(vm, onEnterPip = { enterPip() })
            }
        }
    }

    private fun applyFullscreen(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun enterPip() {
        if (!vm.ui.value.playerVisible) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        try {
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            // Device/OEM without PiP support: ignore.
        }
    }

    /** Home button / gesture while a video is playing -> floating window, like YouTube. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.pb.value.playing && vm.ui.value.playerVisible) enterPip()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) vm.onHostStopped()
    }
}
