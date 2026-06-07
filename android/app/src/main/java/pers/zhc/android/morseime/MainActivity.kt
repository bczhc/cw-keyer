package pers.zhc.android.morseime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.PI
import kotlin.math.sin
import pers.zhc.android.morseime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var keyerPtr = 0L
    private var startTime = 0L
    private var running = false
    private var tickThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        keyerPtr = KeyerJNI.createKeyer(20.0, KeyerMode.ULTIMATIC)
        startTime = System.nanoTime()

        binding.ditBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> KeyerJNI.setDit(keyerPtr, true, elapsed())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    KeyerJNI.setDit(keyerPtr, false, elapsed())
            }
            true
        }
        binding.dahBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> KeyerJNI.setDah(keyerPtr, true, elapsed())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    KeyerJNI.setDah(keyerPtr, false, elapsed())
            }
            true
        }

        running = true
        tickThread = Thread { tickLoop() }.apply { start() }
    }

    private fun elapsed(): Double = (System.nanoTime() - startTime) / 1_000_000_000.0

    private fun tickLoop() {
        val sampleRate = 48000
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.play()

        val freq = 700.0
        var phase = 0.0
        var keyDown = false
        val writeBuf = FloatArray(256)

        while (running) {
            val now = elapsed()
            val events = KeyerJNI.tick(keyerPtr, now)
            for (e in events) {
                when (e) {
                    KeyerEvent.KEY_DOWN -> {
                        keyDown = true
                        runOnUiThread { binding.statusText.text = "KEY DOWN" }
                    }

                    KeyerEvent.KEY_UP -> {
                        keyDown = false
                        runOnUiThread { binding.statusText.text = "KEY UP" }
                    }

                    KeyerEvent.DIT -> runOnUiThread { binding.statusText.text = "dit" }
                    KeyerEvent.DAH -> runOnUiThread { binding.statusText.text = "dah" }
                    KeyerEvent.CHAR_SPACE -> runOnUiThread { binding.statusText.text = "char" }
                    KeyerEvent.WORD_SPACE -> runOnUiThread { binding.statusText.text = "word" }
                }
            }

            val targetAmp = if (keyDown) 0.5f else 0.0f
            val dt = 1.0 / sampleRate
            for (i in writeBuf.indices) {
                writeBuf[i] = targetAmp * sin(2.0 * PI * freq * phase).toFloat()
                phase = (phase + dt) % 1.0
            }
            audioTrack.write(writeBuf, 0, writeBuf.size, AudioTrack.WRITE_BLOCKING)

            Thread.sleep(1)
        }

        audioTrack.stop()
        audioTrack.release()
    }

    override fun onDestroy() {
        running = false
        tickThread?.join(200)
        KeyerJNI.destroyKeyer(keyerPtr)
        super.onDestroy()
    }
}
