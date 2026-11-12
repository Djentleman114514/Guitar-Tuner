package com.xianzhun.tuner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xianzhun.tuner.audio.AndroidAudioInput
import com.xianzhun.tuner.core.GuitarString
import com.xianzhun.tuner.core.SignalState
import com.xianzhun.tuner.core.TuningEngine
import com.xianzhun.tuner.core.TuningReading
import com.xianzhun.tuner.core.YinPitchDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TunerUiState(
    val isListening: Boolean = false,
    val isAuto: Boolean = true,
    val manualString: GuitarString = GuitarString.standard.first(),
    val reading: TuningReading? = null,
    val signal: SignalState = SignalState.WAITING,
    val level: Double = 0.0,
    val error: String? = null,
    val permissionBlocked: Boolean = false,
)

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(TunerUiState())
    val state = mutableState.asStateFlow()
    private val input = AndroidAudioInput(application)
    private val recordingMutex = Mutex()
    private var recordingJob: Job? = null
    @Volatile private var wantsToListen = false

    fun start() {
        if (recordingJob?.isActive == true) return
        wantsToListen = true
        mutableState.update { it.copy(isListening = true, error = null, permissionBlocked = false) }
        recordingJob = viewModelScope.launch(Dispatchers.Default) {
            // A canceled reader must release its microphone before a replacement opens it.
            recordingMutex.withLock {
                val engine = TuningEngine()
                var detector: YinPitchDetector? = null
                var currentRate = 0
                var previousMode: Pair<Boolean, Int>? = null
                try {
                    input.frames().collect { frame ->
                        if (currentRate != frame.sampleRate) {
                            currentRate = frame.sampleRate
                            detector = YinPitchDetector(currentRate)
                        }
                        val before = mutableState.value
                        val mode = before.isAuto to before.manualString.number
                        if (mode != previousMode) {
                            engine.reset()
                            previousMode = mode
                        }
                        val analysis = checkNotNull(detector).analyze(frame.samples)
                        val result = engine.process(
                            analysis, before.manualString.takeUnless { before.isAuto },
                            SystemClock.elapsedRealtime(),
                        )
                        mutableState.update {
                            if (it.isListening && (it.isAuto to it.manualString.number) == mode) {
                                it.copy(reading = result.reading, signal = result.signal, level = result.level)
                            } else it
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    wantsToListen = false
                    mutableState.update {
                        it.copy(
                            isListening = false, reading = null, level = 0.0,
                            error = if (error is SecurityException) "麦克风权限已关闭，请重新授权" else
                                error.message ?: "麦克风暂时不可用，请稍后重试",
                        )
                    }
                }
            }
        }
    }

    fun pause() {
        wantsToListen = false
        stopRecording()
    }

    fun onBackground() = stopRecording()

    fun onForeground(hasPermission: Boolean) {
        if (!hasPermission) {
            stopRecording()
            wantsToListen = false
        } else if (wantsToListen) start()
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        mutableState.update {
            it.copy(isListening = false, reading = null, signal = SignalState.WAITING, level = 0.0)
        }
    }

    fun permissionDenied(permanently: Boolean) {
        pause()
        mutableState.update {
            it.copy(permissionBlocked = permanently, error = "调音需要麦克风权限，声音只在本机处理")
        }
    }

    fun setAuto(automatic: Boolean) {
        mutableState.update {
            it.copy(
                isAuto = automatic,
                manualString = if (!automatic) it.reading?.target ?: it.manualString else it.manualString,
                reading = null, signal = SignalState.WAITING,
            )
        }
    }

    fun selectString(string: GuitarString) {
        mutableState.update {
            it.copy(isAuto = false, manualString = string, reading = null, signal = SignalState.WAITING)
        }
    }
}
