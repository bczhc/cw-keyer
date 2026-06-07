package pers.zhc.android.morseime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView

class MorseIME : InputMethodService() {
    private var keyerPtr: Long = 0
    private var statusText: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pattern = StringBuilder()
    private var lastChar = ""

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
        keyerPtr = KeyerJNI.createKeyer(20.0, KeyerMode.ULTIMATIC)
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
                        if (lastChar != "\n") {
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
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.ime_input, null) as View

        statusText = root.findViewById(R.id.status_text)
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
        KeyerJNI.startKeyer(keyerPtr)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        KeyerJNI.stopKeyer(keyerPtr)
    }

    override fun onDestroy() {
        KeyerJNI.stopKeyer(keyerPtr)
        KeyerJNI.destroyKeyer(keyerPtr)
        super.onDestroy()
    }
}
