package pers.zhc.android.morseime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pers.zhc.android.morseime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar as Toolbar)

        binding.enableImeBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.selectImeBtn.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showHelpDialog() {
        val letters = listOf(
            "A .-", "B -...", "C -.-.", "D -..", "E .", "F ..-.", "G --.", "H ....",
            "I ..", "J .---", "K -.-", "L .-..", "M --", "N -.", "O ---", "P .--.",
            "Q --.-", "R .-.", "S ...", "T -", "U ..-", "V ...-", "W .--",
            "X -..-", "Y -.--", "Z --..",
        )
        val digits = listOf(
            "1 .----", "2 ..---", "3 ...--", "4 ....-", "5 .....", "6 -....",
            "7 --...", "8 ---..", "9 ----.", "0 -----",
        )
        val punct = listOf(
            ". .-.-.-", ", --..--", "? ..--..", "' .----.", "! -.-.--",
            "/ -..-.", "( -.--.", ") -.--.-", "& .-...", ": ---...",
            "; -.-.-.", "= -...-", "+ .-.-.", "- -....-", "_ ..--.-",
            "\" .-..-.", "$ ...-..-", "@ .--.-.",
        )

        val sb = StringBuilder()

        // letters + digits, 3 columns
        val rows = maxOf(letters.size, digits.size)
        for (i in 0 until rows) {
            val l = if (i < letters.size) letters[i] else ""
            val d = if (i < digits.size) digits[i] else ""
            sb.append(l.padEnd(20))
            sb.append(d)
            if (i < rows - 1) sb.append("\n")
        }

        sb.append("\n\n")

        // punctuation
        sb.append(punct.joinToString("     ") + "\n")

        // space, newline
        sb.append(getString(R.string.label_space) + "  ..--" + "     ")
        sb.append(getString(R.string.label_newline) + "  -...-" + "\n")

        sb.append("\n" + getString(R.string.null_char_desc))
        sb.append("\n" + getString(R.string.help_delete_hint))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.help_title))
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
