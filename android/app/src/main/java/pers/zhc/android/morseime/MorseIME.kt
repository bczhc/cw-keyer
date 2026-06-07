package pers.zhc.android.morseime

import android.inputmethodservice.InputMethodService
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView

class MorseIME : InputMethodService() {
    private var keyerPtr: Long = 0
    private var statusText: TextView? = null
    private var ditDown = false
    private var dahDown = false

    override fun onCreate() {
        super.onCreate()
        keyerPtr = KeyerJNI.createKeyer(20.0, KeyerMode.ULTIMATIC)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.ime_input, null) as View

        statusText = root.findViewById(R.id.status_text)
        val ditBtn = root.findViewById<Button>(R.id.dit_btn)
        val dahBtn = root.findViewById<Button>(R.id.dah_btn)

        ditBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ditDown = true
                    KeyerJNI.setDit(keyerPtr, true)
                    updateStatus()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ditDown = false
                    KeyerJNI.setDit(keyerPtr, false)
                    updateStatus()
                }
            }
            true
        }
        dahBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dahDown = true
                    KeyerJNI.setDah(keyerPtr, true)
                    updateStatus()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dahDown = false
                    KeyerJNI.setDah(keyerPtr, false)
                    updateStatus()
                }
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

    private fun updateStatus() {
        val parts = mutableListOf<String>()
        if (ditDown) parts.add("DIT")
        if (dahDown) parts.add("DAH")
        statusText?.text = if (parts.isEmpty()) "" else parts.joinToString(" + ")
    }
}
