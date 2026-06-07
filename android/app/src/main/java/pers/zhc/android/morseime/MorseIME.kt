package pers.zhc.android.morseime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import pers.zhc.android.morseime.databinding.ImeInputBinding

class MorseIME : InputMethodService() {
    private var keyerPtr: Long = 0
    private var statusText: TextView? = null
    private var _binding: ImeInputBinding? = null
    private val binding get() = _binding!!
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pattern = StringBuilder()
    private var lastChar = ""
    private var autoSpace = true
    private var soundEnabled = true
    private var vibrationEnabled = false
    private var showComposing = true
    private var wpm: Double = 20.0
    private var mode: Int = KeyerMode.ULTIMATIC
    private var pitch: Double = 700.0
    private var started = false

    companion object {
        private const val BACKSPACE = "\u0000"
    }

    private val morseMap = mapOf(
        ".-" to "a",
        "-..." to "b",
        "-.-." to "c",
        "-.." to "d",
        "." to "e",
        "..-." to "f",
        "--." to "g",
        "...." to "h",
        ".." to "i",
        ".---" to "j",
        "-.-" to "k",
        ".-.." to "l",
        "--" to "m",
        "-." to "n",
        "---" to "o",
        ".--." to "p",
        "--.-" to "q",
        ".-." to "r",
        "..." to "s",
        "-" to "t",
        "..-" to "u",
        "...-" to "v",
        ".--" to "w",
        "-..-" to "x",
        "-.--" to "y",
        "--.." to "z",
        ".----" to "1",
        "..---" to "2",
        "...--" to "3",
        "....-" to "4",
        "....." to "5",
        "-...." to "6",
        "--..." to "7",
        "---.." to "8",
        "----." to "9",
        "-----" to "0",
        ".-.-.-" to ".",
        "--..--" to ",",
        "..--.." to "?",
        ".----." to "'",
        "-.-.--" to "!",
        "-..-." to "/",
        "-.--." to "(",
        "-.--.-" to ")",
        ".-..." to "&",
        "---..." to ":",
        "-.-.-." to ";",
        "-...-" to "=",
        ".-.-." to "+",
        "-....-" to "-",
        "..--.-" to "_",
        ".-..-." to "\"",
        "...-..-" to "$",
        ".--.-." to "@",
        "..--" to " ",
        "-...-" to "\n",
        "......." to BACKSPACE,
        "........" to BACKSPACE,
        "........." to BACKSPACE,
        ".........." to BACKSPACE,
        "..........." to BACKSPACE,
        "............" to BACKSPACE,
    )

    override fun onCreate() {
        super.onCreate()
        Configs.load(filesDir).let {
            wpm = it.wpm ?: 20.0
            mode = it.mode ?: KeyerMode.ULTIMATIC
            pitch = it.pitch ?: 700.0
            autoSpace = it.autoSpace ?: true
            soundEnabled = it.soundEnabled ?: true
            vibrationEnabled = it.vibrationEnabled ?: false
            showComposing = it.showComposing ?: true
        }
        createKeyer()
    }

    private fun createKeyer() {
        keyerPtr = KeyerJNI.createKeyer(wpm, mode, pitch)
        KeyerJNI.setEventCallback(keyerPtr) { event ->
            mainHandler.post {
                when (event) {
                    KeyerEvent.DIT -> {
                        pattern.append(".")
                        val decoded = morseMap[pattern.toString()]
                        if (showComposing) {
                            val text = if (decoded == BACKSPACE) "" else (decoded ?: "\uFFFD")
                            currentInputConnection?.setComposingText(text, 1)
                        }
                    }
                    KeyerEvent.DAH -> {
                        pattern.append("-")
                        val decoded = morseMap[pattern.toString()]
                        if (showComposing) {
                            val text = if (decoded == BACKSPACE) "" else (decoded ?: "\uFFFD")
                            currentInputConnection?.setComposingText(text, 1)
                        }
                    }
                    KeyerEvent.CHAR_SPACE -> {
                        val ch = morseMap[pattern.toString()]
                        if (ch != null) {
                            if (ch == BACKSPACE) {
                                currentInputConnection?.commitText("", 1)
                            } else {
                                currentInputConnection?.commitText(ch, 1)
                                lastChar = ch
                            }
                        } else {
                            currentInputConnection?.finishComposingText()
                        }
                        pattern.clear()
                    }
                    KeyerEvent.WORD_SPACE -> {
                        if (autoSpace && lastChar != "\n") {
                            currentInputConnection?.commitText(" ", 1)
                        } else {
                            currentInputConnection?.finishComposingText()
                        }
                        pattern.clear()
                    }
                }
                statusText?.text = pattern.toString()
            }
        }
        KeyerJNI.setSoundEnabled(keyerPtr, soundEnabled)
        if (started) {
            KeyerJNI.startKeyer(keyerPtr)
        }
    }

    private fun destroyKeyer() {
        KeyerJNI.stopKeyer(keyerPtr)
        KeyerJNI.destroyKeyer(keyerPtr)
        keyerPtr = 0
    }

    private fun destroyAndRecreateKeyer() {
        destroyKeyer()
        createKeyer()
    }

    override fun onCreateInputView(): View {
        val themedInflater = layoutInflater.cloneInContext(ContextThemeWrapper(this, R.style.Theme_MorseIME))
        _binding = ImeInputBinding.inflate(themedInflater)
        val root = binding.root
        statusText = binding.statusText

        val modeNames = arrayOf("Iambic A", "Iambic B", "Ultimatic", "Straight")
        val modeValues = arrayOf(KeyerMode.IAMBIC_A, KeyerMode.IAMBIC_B, KeyerMode.ULTIMATIC, KeyerMode.STRAIGHT)
        binding.modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeNames).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        var pendingMode = mode
        binding.modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                pendingMode = modeValues[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        binding.speedIncBtn.setOnClickListener {
            val cur = binding.speedValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            binding.speedValue.text = (cur + 1).coerceAtMost(99).toString()
        }
        binding.speedDecBtn.setOnClickListener {
            val cur = binding.speedValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            binding.speedValue.text = (cur - 1).coerceAtLeast(1).toString()
        }

        binding.pitchIncBtn.setOnClickListener {
            val cur = binding.pitchValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            binding.pitchValue.text = (cur + 50).coerceAtMost(2000).toString()
        }
        binding.pitchDecBtn.setOnClickListener {
            val cur = binding.pitchValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            binding.pitchValue.text = (cur - 50).coerceAtLeast(100).toString()
        }

        fun updateKeyboardMode() {
            val isStraight = mode == KeyerMode.STRAIGHT
            binding.ditBtn.visibility = if (isStraight) View.GONE else View.VISIBLE
            binding.dahBtn.visibility = if (isStraight) View.GONE else View.VISIBLE
            binding.keyBtn.visibility = if (isStraight) View.VISIBLE else View.GONE
        }
        updateKeyboardMode()

        binding.ditBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    if (vibrationEnabled) v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    KeyerJNI.setDit(keyerPtr, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    KeyerJNI.setDit(keyerPtr, false)
                }
            }
            true
        }
        binding.dahBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    if (vibrationEnabled) v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    KeyerJNI.setDah(keyerPtr, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    KeyerJNI.setDah(keyerPtr, false)
                }
            }
            true
        }
        binding.keyBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    if (vibrationEnabled) v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    KeyerJNI.setDit(keyerPtr, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    KeyerJNI.setDit(keyerPtr, false)
                }
            }
            true
        }

        binding.switchImeBtn.setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showInputMethodPicker()
        }

        fun resetSettingsUI() {
            binding.speedValue.text = wpm.toInt().toString()
            binding.pitchValue.text = pitch.toInt().toString()
            binding.modeSpinner.setSelection(modeValues.indexOf(mode))
            binding.autoSpaceSwitch.isChecked = autoSpace
            binding.soundSwitch.isChecked = soundEnabled
            binding.vibrationSwitch.isChecked = vibrationEnabled
            binding.composingSwitch.isChecked = showComposing
        }

        binding.settingsBtn.setOnClickListener {
            binding.keyboardArea.visibility = View.GONE
            binding.modeLabel.visibility = View.GONE
            binding.settingsArea.visibility = View.VISIBLE
            binding.statusText.visibility = View.GONE
            binding.settingsTitle.visibility = View.VISIBLE
            resetSettingsUI()
        }

        binding.settingsCancelBtn.setOnClickListener {
            resetSettingsUI()
            binding.keyboardArea.visibility = View.VISIBLE
            binding.modeLabel.visibility = View.VISIBLE
            binding.settingsArea.visibility = View.GONE
            binding.statusText.visibility = View.VISIBLE
            binding.settingsTitle.visibility = View.GONE
        }

        binding.settingsApplyBtn.setOnClickListener {
            val newWpm = binding.speedValue.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val newPitch = binding.pitchValue.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val newMode = pendingMode
            val newAutoSpace = binding.autoSpaceSwitch.isChecked
            var changed = false
            if (newWpm != wpm || newMode != mode || newPitch != pitch) {
                wpm = newWpm
                mode = newMode
                pitch = newPitch
                destroyAndRecreateKeyer()
                updateKeyboardMode()
                changed = true
            }
            val newSound = binding.soundSwitch.isChecked
            val newVibration = binding.vibrationSwitch.isChecked
            if (newSound != soundEnabled) {
                soundEnabled = newSound
                KeyerJNI.setSoundEnabled(keyerPtr, soundEnabled)
                changed = true
            }
            if (newVibration != vibrationEnabled) {
                vibrationEnabled = newVibration
                changed = true
            }
            val newComposing = binding.composingSwitch.isChecked
            if (newComposing != showComposing) {
                showComposing = newComposing
                changed = true
            }
            if (newAutoSpace != autoSpace) {
                autoSpace = newAutoSpace
                changed = true
            }
            Configs(
                wpm = wpm,
                mode = mode,
                pitch = pitch,
                autoSpace = autoSpace,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                showComposing = showComposing,
            ).save(filesDir)
            binding.keyboardArea.visibility = View.VISIBLE
            binding.modeLabel.visibility = View.VISIBLE
            binding.settingsArea.visibility = View.GONE
            binding.statusText.visibility = View.VISIBLE
            binding.settingsTitle.visibility = View.GONE
        }

        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        started = true
        KeyerJNI.startKeyer(keyerPtr)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        started = false
        KeyerJNI.stopKeyer(keyerPtr)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        destroyKeyer()
    }
}
