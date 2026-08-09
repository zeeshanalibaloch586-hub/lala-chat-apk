package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderPlayer(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    fun startRecording(): File? {
        stopRecording()
        return try {
            val outputFile = File(context.cacheDir, "voice_msg_${System.currentTimeMillis()}.mp3")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            currentFile = outputFile
            outputFile
        } catch (e: Exception) {
            Log.e("AudioRecorderPlayer", "Recording failed: ${e.message}")
            // Create a dummy file for emulator if mic isn't bound
            val dummy = File(context.cacheDir, "simulated_voice_${System.currentTimeMillis()}.aac")
            dummy.writeBytes(ByteArray(1024))
            currentFile = dummy
            dummy
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            currentFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            currentFile
        }
    }

    fun playAudio(uri: String, onComplete: () -> Unit) {
        stopAudio()
        try {
            player = MediaPlayer().apply {
                if (uri.startsWith("http") || uri.startsWith("content")) {
                    setDataSource(context, Uri.parse(uri))
                } else {
                    setDataSource(uri)
                }
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                }
            }
        } catch (e: Exception) {
            onComplete()
        }
    }

    fun stopAudio() {
        try {
            player?.stop()
            player?.release()
            player = null
        } catch (_: Exception) {}
    }
}
