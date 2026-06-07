package pers.zhc.android.morseime

import com.google.gson.Gson
import java.io.File

data class Configs(
    val wpm: Double? = 20.0,
    val mode: Int? = KeyerMode.ULTIMATIC,
    val pitch: Double? = 700.0,
    val autoSpace: Boolean? = true,
    val soundEnabled: Boolean? = true,
    val vibrationEnabled: Boolean? = false,
) {
    fun resolved() = copy(
        wpm = wpm ?: 20.0,
        mode = mode ?: KeyerMode.ULTIMATIC,
        pitch = pitch ?: 700.0,
        autoSpace = autoSpace ?: true,
        soundEnabled = soundEnabled ?: true,
        vibrationEnabled = vibrationEnabled ?: false,
    )

    fun save(dir: File) {
        dir.resolve("ime_config.json").writeText(Gson().toJson(this))
    }

    companion object {
        fun load(dir: File): Configs {
            return try {
                val raw = Gson().fromJson(dir.resolve("ime_config.json").readText(), Configs::class.java)
                raw.resolved()
            } catch (_: Exception) {
                Configs()
            }
        }
    }
}
