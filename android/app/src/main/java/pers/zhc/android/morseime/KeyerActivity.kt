package pers.zhc.android.morseime

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.ChipGroup
import pers.zhc.android.morseime.databinding.ActivityKeyerBinding

class KeyerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKeyerBinding
    private var keyerPtr: Long = 0
    private var wpm: Double = 20.0
    private var mode: Int = KeyerMode.ULTIMATIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityKeyerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        initConfigUI()
        createAndStartKeyer()
        initTouchListeners()
    }

    private fun initConfigUI() {
        binding.wpmInput.setText(wpm.toInt().toString())

        val modeToId = mapOf(
            KeyerMode.IAMBIC_A to R.id.mode_iambic_a,
            KeyerMode.IAMBIC_B to R.id.mode_iambic_b,
            KeyerMode.ULTIMATIC to R.id.mode_ultimatic,
            KeyerMode.STRAIGHT to R.id.mode_straight,
        )
        binding.modeGroup.check(modeToId[mode]!!)

        binding.wpmInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                applyConfig()
            }
        }
        binding.wpmInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyConfig()
                true
            } else {
                false
            }
        }
        binding.modeGroup.setOnCheckedStateChangeListener { _: ChipGroup, _: MutableList<Int> ->
            applyConfig()
        }
    }

    private fun applyConfig() {
        val newWpm = binding.wpmInput.text.toString().toDoubleOrNull()
        if (newWpm == null || newWpm <= 0.0) return

        val idToMode = mapOf(
            R.id.mode_iambic_a to KeyerMode.IAMBIC_A,
            R.id.mode_iambic_b to KeyerMode.IAMBIC_B,
            R.id.mode_ultimatic to KeyerMode.ULTIMATIC,
            R.id.mode_straight to KeyerMode.STRAIGHT,
        )
        val newMode = idToMode[binding.modeGroup.checkedChipId] ?: return

        if (newWpm == wpm && newMode == mode) return

        wpm = newWpm
        mode = newMode

        destroyKeyer()
        createAndStartKeyer()
    }

    private fun createAndStartKeyer() {
        keyerPtr = KeyerJNI.createKeyer(wpm, mode)
        KeyerJNI.startKeyer(keyerPtr)
    }

    private fun destroyKeyer() {
        if (keyerPtr != 0L) {
            KeyerJNI.stopKeyer(keyerPtr)
            KeyerJNI.destroyKeyer(keyerPtr)
            keyerPtr = 0L
        }
    }

    private fun initTouchListeners() {
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
        destroyKeyer()
        super.onDestroy()
    }
}
