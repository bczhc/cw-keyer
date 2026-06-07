package pers.zhc.android.morseime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView

class MorseIME : InputMethodService() {
    private var keyerPtr: Long = 0
    private var statusText: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pattern = StringBuilder()
    private var lastChar = ""
    private var autoSpace = true
    private var wpm: Double = 20.0
    private var mode: Int = KeyerMode.ULTIMATIC
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
        createKeyer()
    }

    private fun createKeyer() {
        keyerPtr = KeyerJNI.createKeyer(wpm, mode)
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

        root.findViewById<View>(R.id.settings_btn).setOnClickListener {
            keyboardArea.visibility = View.GONE
            settingsArea.visibility = View.VISIBLE
        }

        val speedValue = root.findViewById<TextView>(R.id.speed_value)
        val modeSpinner = root.findViewById<Spinner>(R.id.mode_spinner)
        val autoSpaceSwitch = root.findViewById<android.widget.Switch>(R.id.auto_space_switch)
        val modeNames = arrayOf("Iambic A", "Iambic B", "Ultimatic", "Straight")
        val modeValues = arrayOf(KeyerMode.IAMBIC_A, KeyerMode.IAMBIC_B, KeyerMode.ULTIMATIC, KeyerMode.STRAIGHT)
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeNames).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        fun resetSettingsUI() {
            speedValue.text = wpm.toInt().toString()
            modeSpinner.setSelection(modeValues.indexOf(mode))
            autoSpaceSwitch.isChecked = autoSpace
        }

        root.findViewById<View>(R.id.settings_cancel_btn).setOnClickListener {
            resetSettingsUI()
            keyboardArea.visibility = View.VISIBLE
            settingsArea.visibility = View.GONE
        }

        root.findViewById<View>(R.id.speed_inc_btn).setOnClickListener {
            val cur = speedValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            speedValue.text = (cur + 1).coerceAtMost(99).toString()
        }
        root.findViewById<View>(R.id.speed_dec_btn).setOnClickListener {
            val cur = speedValue.text.toString().toIntOrNull() ?: return@setOnClickListener
            speedValue.text = (cur - 1).coerceAtLeast(1).toString()
        }

        var pendingMode = mode
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                pendingMode = modeValues[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        root.findViewById<View>(R.id.switch_ime_btn).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showInputMethodPicker()
        }

        root.findViewById<View>(R.id.settings_apply_btn).setOnClickListener {
            val newWpm = speedValue.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            val newMode = pendingMode
            val newAutoSpace = autoSpaceSwitch.isChecked
            if (newWpm != wpm || newMode != mode) {
                wpm = newWpm
                mode = newMode
                destroyAndRecreateKeyer()
            }
            autoSpace = newAutoSpace
            keyboardArea.visibility = View.VISIBLE
            settingsArea.visibility = View.GONE
        }

        val ditBtn = root.findViewById<Button>(R.id.dit_btn)
        val dahBtn = root.findViewById<Button>(R.id.dah_btn)

        ditBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> KeyerJNI.setDit(keyerPtr, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> KeyerJNI.setDit(keyerPtr, false)
            }
            true
        }
        dahBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> KeyerJNI.setDah(keyerPtr, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> KeyerJNI.setDah(keyerPtr, false)
            }
            true
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
