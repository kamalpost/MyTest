package app.focusphone.phone

/** Classic multi-tap ("ABC") text entry: press 2 once for a, twice for b … */
class MultiTap {
    enum class Mode(val label: String) { ABC("Abc"), UPPER("ABC"), LOWER("abc"), NUM("123") }

    private val sb = StringBuilder()
    var mode: Mode = Mode.ABC
        private set
    private var lastKey: Key? = null
    private var cycleIndex = 0
    private var lastTime = 0L
    private var pendingChar = false

    val text: String get() = sb.toString()
    val isEmpty: Boolean get() = sb.isEmpty()

    private val timeoutMs = 900L

    fun setText(s: String) {
        sb.setLength(0)
        sb.append(s)
        commit()
    }

    /** Ends the current multi-tap cycle so the next press starts a new character. */
    fun commit() {
        lastKey = null
        pendingChar = false
        cycleIndex = 0
    }

    fun toggleMode() {
        commit()
        mode = when (mode) {
            Mode.ABC -> Mode.UPPER
            Mode.UPPER -> Mode.LOWER
            Mode.LOWER -> Mode.NUM
            Mode.NUM -> Mode.ABC
        }
    }

    fun backspace() {
        if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
        commit()
    }

    fun space() {
        sb.append(' ')
        commit()
    }

    /** Feed a keypad key; returns true if the text changed. */
    fun press(key: Key, now: Long = System.currentTimeMillis()): Boolean {
        val c = key.char ?: return false
        if (key == Key.HASH) {
            toggleMode()
            return true
        }
        if (mode == Mode.NUM) {
            if (key == Key.STAR) sb.append('+') else sb.append(c)
            commit()
            return true
        }
        if (key == Key.STAR) {
            // symbols
            cycleOrAppend(key, "+-*/=()&%$#", now)
            return true
        }
        if (key == Key.D0) {
            cycleOrAppend(key, " 0", now)
            return true
        }
        val alphabet = key.letters + c
        cycleOrAppend(key, alphabet, now)
        return true
    }

    private fun cycleOrAppend(key: Key, alphabet: String, now: Long) {
        val sameKey = key == lastKey && pendingChar && now - lastTime < timeoutMs
        if (sameKey) {
            cycleIndex = (cycleIndex + 1) % alphabet.length
            sb.setLength(sb.length - 1)
        } else {
            cycleIndex = 0
        }
        var ch = alphabet[cycleIndex]
        if (ch.isLetter()) {
            ch = when (mode) {
                Mode.UPPER -> ch.uppercaseChar()
                Mode.LOWER -> ch
                else -> if (sentenceStart()) ch.uppercaseChar() else ch
            }
        }
        sb.append(ch)
        lastKey = key
        lastTime = now
        pendingChar = true
    }

    /** "Abc" mode capitalises the first letter of the text and after ". " */
    private fun sentenceStart(): Boolean {
        val t = sb.toString().trimEnd()
        return t.isEmpty() || t.endsWith('.') || t.endsWith('!') || t.endsWith('?')
    }
}
