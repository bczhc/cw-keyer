package pers.zhc.android.morseime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.PI
import kotlin.math.sin
import pers.zhc.android.morseime.databinding.ActivityMainBinding

// latency is still >10ms

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Volatile
    private var keyDown = false
    private var running = false
    private var audioThread: Thread? = null

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

        // 核心改动：把延迟压到触控层和UI层
        binding.toneBtn.setOnTouchListener { v, event ->
            // 强行禁止父布局（如果有的话）拦截这次触控，让事件第一时间送达
            v.parent?.requestDisallowInterceptTouchEvent(true)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!keyDown) {
                        keyDown = true
                        // 1. 界面文字瞬间切换
                        binding.toneBtn.text = "放开"
                        // 2. 瞬间调用底层硬件马达震动（模拟电报按键物理质感）
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (keyDown) {
                        keyDown = false
                        // 1. 恢复文字
                        binding.toneBtn.text = "按下发报"
                        // 2. 触发松开时的轻微震动
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                    }
                }
            }
            true
        }

        // 启动极致音频线程
        running = true
        audioThread = Thread { audioLoop() }.apply { start() }
    }

    private fun audioLoop() {
        val sampleRate = 48000

        // 告诉系统：这是一个要求极致延迟的提示音/音效，要求走 Fast Mixer 硬件通路
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            // 强行开启低延迟性能模式
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        track.play()

        val freq = 700.0
        val dt = 1.0 / sampleRate
        var phase = 0.0

        // 降低单次缓冲区长度至 64（约1.3毫秒数据），大幅提高对 keyDown 状态改变的嗅探速度
        val writeBuf = FloatArray(64)
        var currentAmp = 0.0f
        val rampFactor = 0.15f // 淡入淡出系数，兼顾无爆音和低延迟

        while (running) {
            val targetAmp = if (keyDown) 0.3f else 0.0f

            for (i in writeBuf.indices) {
                // 平滑过渡算法，防止突变爆音
                currentAmp += (targetAmp - currentAmp) * rampFactor
                writeBuf[i] = currentAmp * sin(2.0 * PI * freq * phase).toFloat()
                phase = (phase + dt) % 1.0
            }

            // 阻塞式写入硬件缓冲区
            track.write(writeBuf, 0, writeBuf.size, AudioTrack.WRITE_BLOCKING)
        }

        try {
            track.stop()
        } catch (_: Exception) {}
        track.release()
    }

    override fun onDestroy() {
        running = false
        audioThread?.join(200)
        super.onDestroy()
    }
}
