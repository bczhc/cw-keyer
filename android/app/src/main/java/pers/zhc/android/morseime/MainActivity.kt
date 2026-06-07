package pers.zhc.android.morseime

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pers.zhc.android.morseime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var audioPtr: Long = 0

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

        audioPtr = KeyerJNI.initAudio()

        binding.toneBtn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    binding.toneBtn.text = "松开"
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_PRESS)
                    KeyerJNI.startTone(audioPtr)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.toneBtn.text = "按下发报"
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
                    KeyerJNI.stopTone(audioPtr)
                }
            }
            true
        }
    }

    override fun onDestroy() {
        KeyerJNI.destroyAudio(audioPtr)
        super.onDestroy()
    }
}
