package com.xianzhun.tuner.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.max

data class AudioFrame(val samples: FloatArray, val sampleRate: Int)

class AndroidAudioInput(private val context: Context) {
    fun frames(): Flow<AudioFrame> = flow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("请先允许麦克风权限")
        }
        val manager = context.getSystemService(AudioManager::class.java)
        val rawSupported = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = if (rawSupported) listOf(
            MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        ) else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        var recorder: AudioRecord? = null
        for (source in sources) {
            for (rate in listOf(48_000, 44_100)) {
                val minimum = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minimum <= 0) continue
                val candidate = try {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat.Builder().setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                        )
                        .setBufferSizeInBytes(max(minimum * 2, WINDOW_SIZE * 4))
                        .build()
                } catch (error: SecurityException) {
                    throw error
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: UnsupportedOperationException) {
                    null
                }
                if (candidate == null) continue
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    recorder = candidate
                    break
                }
                candidate.release()
            }
            if (recorder != null) break
        }
        val input = recorder ?: error("这台设备无法启动麦克风，请检查输入设备后重试")
        try {
            // This product tunes through the phone microphone, even with headphones attached.
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?.let { input.setPreferredDevice(it) }
            input.startRecording()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "麦克风未能开始采音，可能正被其他应用占用"
            }
            val chunk = ShortArray(1024)
            val ring = FloatArray(WINDOW_SIZE)
            var writeIndex = 0
            var collected = 0
            var sinceAnalysis = 0
            while (currentCoroutineContext().isActive) {
                val count = input.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (count < 0) error("音频输入中断（$count），请重新开始调音")
                if (count == 0) continue
                for (i in 0 until count) {
                    ring[writeIndex] = chunk[i] / 32768f
                    writeIndex = (writeIndex + 1) % WINDOW_SIZE
                }
                collected += count
                sinceAnalysis += count
                if (collected >= WINDOW_SIZE && sinceAnalysis >= HOP_SIZE) {
                    sinceAnalysis = 0
                    val frame = FloatArray(WINDOW_SIZE) { ring[(writeIndex + it) % WINDOW_SIZE] }
                    emit(AudioFrame(frame, input.sampleRate))
                    collected = WINDOW_SIZE
                }
            }
        } finally {
            try {
                if (input.recordingState == AudioRecord.RECORDSTATE_RECORDING) input.stop()
            } finally {
                input.release()
            }
        }
    }

    companion object {
        const val WINDOW_SIZE = 4096
        const val HOP_SIZE = 2048
    }
}
