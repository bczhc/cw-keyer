package pers.zhc.android.morseime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class MorseIME : InputMethodService() {
    private var keyerPtr: Long = 0
    private var statusText: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pattern = StringBuilder()
    private var lastChar = ""
    private var autoSpace = true
    private var soundEnabled = true
    private var vibrationEnabled = false
    private var wpm: Double = 20.0
    private var mode: Int = KeyerMode.ULTIMATIC
    private var pitch: Double = 700.0
    private var started = false

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
                        val decoded = morseMap[pattern.toString()] ?: "\uFFFD"
                        currentInputConnection?.setComposingText(decoded, 1)
                    }
                    KeyerEvent.DAH -> {
                        pattern.append("-")
                        val decoded = morseMap[pattern.toString()] ?: "\uFFFD"
                        currentInputConnection?.setComposingText(decoded, 1)
                    }
                    KeyerEvent.CHAR_SPACE -> {
                        val ch = morseMap[pattern.toString()]
                        if (ch != null) {
                            currentInputConnection?.commitText(ch, 1)
                            lastChar = ch
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
        val root = layoutInflater.inflate(R.layout.ime_input, null) as View

        val keyboardArea = root.findViewById<View>(R.id.keyboard_area)
        val settingsArea = root.findViewById<View>(R.id.settings_area)
        statusText = root.findViewById(R.id.status_text)
        val modeLabel = root.findViewById<TextView>(R.id.mode_label)

        root.findViewById<View>(R.id.settings_btn).setOnClickListener {
            keyboardArea.visibility = View.GONE
            modeLabel.visibility = View.GONE
            settingsArea.visibility = View.VISIBLE
            statusText?.text = getString(R.string.settings_title)
            statusText?.gravity = android.view.Gravity.START
            statusText?.setPaddingRelative((16 * resources.displayMetrics.density).toInt(), 0, 0, 0)
        }

        val speedValue = root.findViewById<TextView>(R.id.speed_value)
        val pitchValue = root.findViewById<TextView>(R.id.pitch_value)
        val modeSpinner = root.findViewById<Spinner>(R.id.mode_spinner)
        val autoSpaceSwitch = root.findViewById<android.widget.Switch>(R.id.auto_space_switch)
        val soundSwitch = root.findViewById<android.widget.Switch>(R.id.sound_switch)
        val vibrationSwitch = root.findViewById<android.widget.Switch>(R.id.vibration_switch)
        val modeNames = arrayOf("Iambic A", "Iambic B", "Ultimatic", "Straight")
        val modeValues = arrayOf(KeyerMode.IAMBIC_A, KeyerMode.IAMBIC_B, KeyerMode.ULTIMATIC, KeyerMode.STRAIGHT)
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeNames).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        var pendingMode = mode
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                pendingMode = modeValues[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        root.findViewById<View>(R.id.speed_inc_btn).setOnClickListener {
            val cur = speedValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            speedValue.text = (cur + 1).coerceAtMost(99).toString()
        }
        root.findViewById<View>(R.id.speed_dec_btn).setOnClickListener {
            val cur = speedValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            speedValue.text = (cur - 1).coerceAtLeast(1).toString()
        }

        root.findViewById<View>(R.id.pitch_inc_btn).setOnClickListener {
            val cur = pitchValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            pitchValue.text = (cur + 50).coerceAtMost(2000).toString()
        }
        root.findViewById<View>(R.id.pitch_dec_btn).setOnClickListener {
            val cur = pitchValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            pitchValue.text = (cur - 50).coerceAtLeast(100).toString()
        }

        val ditBtn = root.findViewById<Button>(R.id.dit_btn)
        val dahBtn = root.findViewById<Button>(R.id.dah_btn)
        val keyBtn = root.findViewById<Button>(R.id.key_btn)

        fun updateKeyboardMode() {
            val isStraight = mode == KeyerMode.STRAIGHT
            ditBtn.visibility = if (isStraight) View.GONE else View.VISIBLE
            dahBtn.visibility = if (isStraight) View.GONE else View.VISIBLE
            keyBtn.visibility = if (isStraight) View.VISIBLE else View.GONE
        }
        updateKeyboardMode()

        ditBtn.setOnTouchListener { v, event ->
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
        dahBtn.setOnTouchListener { v, event ->
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
        keyBtn.setOnTouchListener { v, event ->
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

        root.findViewById<View>(R.id.switch_ime_btn).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showInputMethodPicker()
        }

        fun resetSettingsUI() {
            speedValue.text = wpm.toInt().toString()
            pitchValue.text = pitch.toInt().toString()
            modeSpinner.setSelection(modeValues.indexOf(mode))
            autoSpaceSwitch.isChecked = autoSpace
            soundSwitch.isChecked = soundEnabled
            vibrationSwitch.isChecked = vibrationEnabled
        }

        root.findViewById<View>(R.id.settings_cancel_btn).setOnClickListener {
            resetSettingsUI()
            keyboardArea.visibility = View.VISIBLE
            modeLabel.visibility = View.VISIBLE
            settingsArea.visibility = View.GONE
            statusText?.text = pattern.toString()
            statusText?.gravity = android.view.Gravity.CENTER
            statusText?.setPaddingRelative(0, 0, 0, 0)
        }

        root.findViewById<View>(R.id.settings_apply_btn).setOnClickListener {
            val newWpm = speedValue.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val newPitch = pitchValue.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val newMode = pendingMode
            val newAutoSpace = autoSpaceSwitch.isChecked
            var changed = false
            if (newWpm != wpm || newMode != mode || newPitch != pitch) {
                wpm = newWpm
                mode = newMode
                pitch = newPitch
                destroyAndRecreateKeyer()
                updateKeyboardMode()
                changed = true
            }
            val newSound = soundSwitch.isChecked
            val newVibration = vibrationSwitch.isChecked
            if (newSound != soundEnabled) {
                soundEnabled = newSound
                KeyerJNI.setSoundEnabled(keyerPtr, soundEnabled)
                changed = true
            }
            if (newVibration != vibrationEnabled) {
                vibrationEnabled = newVibration
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
            ).save(filesDir)
            keyboardArea.visibility = View.VISIBLE
            modeLabel.visibility = View.VISIBLE
            settingsArea.visibility = View.GONE
            statusText?.text = pattern.toString()
            statusText?.gravity = android.view.Gravity.CENTER
            statusText?.setPaddingRelative(0, 0, 0, 0)
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
        destroyKeyer()
        super.onDestroy()
    }
}
