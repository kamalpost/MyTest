package app.focusphone.phone

/** Every physical key of the feature phone. */
enum class Key(val char: Char? = null, val letters: String = "") {
    SOFT_LEFT, SOFT_RIGHT, UP, DOWN, LEFT, RIGHT, OK, CALL, END,
    D1('1', ".,?!"), D2('2', "abc"), D3('3', "def"),
    D4('4', "ghi"), D5('5', "jkl"), D6('6', "mno"),
    D7('7', "pqrs"), D8('8', "tuv"), D9('9', "wxyz"),
    STAR('*', "+"), D0('0', " "), HASH('#', "⇧");

    val isDigit: Boolean get() = char?.isDigit() == true
    val isDialChar: Boolean get() = char != null

    companion object {
        fun forChar(c: Char): Key? = entries.firstOrNull { it.char == c }
    }
}
