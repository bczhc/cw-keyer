package pers.zhc.android.morseime

object KeyerMode {
    const val IAMBIC_A = 0
    const val IAMBIC_B = 1
    const val ULTIMATIC = 2
    const val STRAIGHT = 3
}

object KeyerEvent {
    const val KEY_ON = 0
    const val KEY_OFF = 1
    const val DIT = 2
    const val DAH = 3
    const val CHAR_SPACE = 4
    const val WORD_SPACE = 5
}

fun interface KeyerEventCallback {
    fun onEvent(event: Int)
}

object KeyerJNI {
    init {
        System.loadLibrary("app_jni")
        initLogger()
    }

    external fun initLogger()

    external fun createKeyer(wpm: Double, mode: Int, pitch: Double): Long
    external fun destroyKeyer(ptr: Long)
    external fun startKeyer(ptr: Long)
    external fun stopKeyer(ptr: Long)
    external fun setDit(ptr: Long, pressed: Boolean): Boolean
    external fun setDah(ptr: Long, pressed: Boolean): Boolean
    external fun isKeyOn(ptr: Long): Boolean
    external fun setEventCallback(ptr: Long, callback: KeyerEventCallback)
    external fun setSoundEnabled(ptr: Long, enabled: Boolean)
}
