package pers.zhc.android.morseime

object KeyerMode {
    const val IAMBIC_A = 0
    const val IAMBIC_B = 1
    const val ULTIMATIC = 2
    const val STRAIGHT = 3
}

object KeyerEvent {
    const val KEY_DOWN = 0
    const val KEY_UP = 1
    const val DIT = 2
    const val DAH = 3
    const val CHAR_SPACE = 4
    const val WORD_SPACE = 5
}

object KeyerJNI {
    init {
        System.loadLibrary("app_jni")
    }

    external fun createKeyer(wpm: Double, mode: Int): Long
    external fun destroyKeyer(ptr: Long)
    external fun setDit(ptr: Long, pressed: Boolean, now: Double): Boolean
    external fun setDah(ptr: Long, pressed: Boolean, now: Double): Boolean
    external fun isKeyDown(ptr: Long): Boolean
    external fun tick(ptr: Long, now: Double): IntArray
}
