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
    private var ditDown = false
    private var dahDown = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val eventLabels = mapOf(
        KeyerEvent.KEY_ON to "KEY ON",
        KeyerEvent.KEY_OFF to "KEY OFF",
        KeyerEvent.DIT to "dit",
        KeyerEvent.DAH to "dah",
        KeyerEvent.CHAR_SPACE to "char",
        KeyerEvent.WORD_SPACE to "word",
    )

    override fun onCreate() {
        super.onCreate()
        keyerPtr = KeyerJNI.createKeyer(20.0, KeyerMode.ULTIMATIC)
        KeyerJNI.setEventCallback(keyerPtr) { event ->
            mainHandler.post { statusText?.text = eventLabels[event] ?: "" }
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
