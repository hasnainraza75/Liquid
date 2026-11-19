package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.system.ScreenRecordingState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Production Android 15 Screen Recorder using MediaProjection + MediaRecorder + MediaStore.
 * Declares foregroundServiceType="mediaProjection", registers MediaProjection.Callback prior
 * to createVirtualDisplay (required on Android 14/15), and saves .mp4 videos to Movies/LuxRecordings
 * without broad storage permissions.
 */
class ScreenRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var currentDisplayName: String = ""

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecordingAndFinish(saved = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecordingAndFinish(saved = true)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA_INTENT)
                }
                val withAudio = intent.getBooleanExtra(EXTRA_WITH_MIC, false)

                if (resultCode != Activity.RESULT_OK || dataIntent == null) {
                    _state.value = ScreenRecordingState.Error("Screen capture permission was not granted.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                ensureChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                beginCapture(resultCode, dataIntent, withAudio)
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun beginCapture(resultCode: Int, dataIntent: Intent, withMic: Boolean) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, dataIntent)
                ?: throw IllegalStateException("MediaProjection session returned null.")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

            val metrics = resources.displayMetrics
            // Even dimensions required by hardware H.264 encoders (including Unisoc/Mali-G57)
            val width = (metrics.widthPixels.coerceAtMost(1080) / 2) * 2
            val height = (metrics.heightPixels.coerceAtMost(2400) / 2) * 2
            val dpi = metrics.densityDpi

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            currentDisplayName = "Lux_$timestamp.mp4"

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            if (withMic) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, currentDisplayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/LuxRecordings"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Unable to allocate MediaStore output video.")
                outputUri = uri
                val pfd = contentResolver.openFileDescriptor(uri, "w")
                    ?: throw IllegalStateException("Unable to open MediaStore file descriptor.")
                outputPfd = pfd
                recorder.setOutputFile(pfd.fileDescriptor)
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
                val file = File(dir, currentDisplayName)
                recorder.setOutputFile(file.absolutePath)
            }

            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withMic) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
            }
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(6_000_000)
            recorder.prepare()

            mediaRecorder = recorder
            virtualDisplay = projection.createVirtualDisplay(
                "LuxScreenRecorder",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            startElapsedTimer(withMic)
        } catch (t: Throwable) {
            releaseResources(markSaved = false)
            _state.value = ScreenRecordingState.Error(
                t.localizedMessage ?: "Screen recording failed to initialize."
            )
            stopSelf()
        }
    }

    private fun startElapsedTimer(withMic: Boolean) {
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                _state.value = ScreenRecordingState.Recording(seconds, withMic)
                delay(1000L)
                seconds++
            }
        }
    }

    private fun stopRecordingAndFinish(saved: Boolean) {
        timerJob?.cancel()
        val hadActiveRecorder = mediaRecorder != null
        releaseResources(markSaved = saved && hadActiveRecorder)
        if (saved && hadActiveRecorder && currentDisplayName.isNotBlank()) {
            _state.value = ScreenRecordingState.Saved(currentDisplayName)
        } else if (_state.value is ScreenRecordingState.Recording) {
            _state.value = ScreenRecordingState.Idle
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources(markSaved: Boolean) {
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null

        runCatching { outputPfd?.close() }
        outputPfd = null

        val uri = outputUri
        outputUri = null
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (markSaved) {
                val completed = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                runCatching { contentResolver.update(uri, completed, null, null) }
            } else {
                runCatching { contentResolver.delete(uri, null, null) }
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_recording_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_recording_desc)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            201,
            Intent(this, ScreenRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppIntent = PendingIntent.getActivity(
            this,
            202,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_body))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.action_stop_recording), stopIntent)
            .build()
    }

    override fun onDestroy() {
        timerJob?.cancel()
        releaseResources(markSaved = false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "lux_screen_recording_channel"
        private const val NOTIFICATION_ID = 4202
        const val ACTION_START = "com.example.action.START_SCREEN_RECORDING"
        const val ACTION_STOP = "com.example.action.STOP_SCREEN_RECORDING"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
        const val EXTRA_WITH_MIC = "extra_with_mic"

        private val _state = MutableStateFlow<ScreenRecordingState>(ScreenRecordingState.Idle)
        val state: StateFlow<ScreenRecordingState> = _state.asStateFlow()

        fun clearTransientResult() {
            if (_state.value is ScreenRecordingState.Saved || _state.value is ScreenRecordingState.Error) {
                _state.value = ScreenRecordingState.Idle
            }
        }

        fun start(context: Context, resultCode: Int, data: Intent, withMic: Boolean) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
                putExtra(EXTRA_WITH_MIC, withMic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
