package pers.zhc.android.morseime

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import pers.zhc.android.morseime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var keyerPtr: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keyerPtr = KeyerJNI.createKeyer(20.0, KeyerMode.ULTIMATIC)
        KeyerJNI.startKeyer(keyerPtr)

        binding.ditBtn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    KeyerJNI.setDit(keyerPtr, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                    KeyerJNI.setDit(keyerPtr, false)
                }
            }
            true
        }

        binding.dahBtn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    KeyerJNI.setDah(keyerPtr, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                    KeyerJNI.setDah(keyerPtr, false)
                }
            }
            true
        }
    }

    override fun onDestroy() {
        KeyerJNI.stopKeyer(keyerPtr)
        KeyerJNI.destroyKeyer(keyerPtr)
        super.onDestroy()
    }
}
