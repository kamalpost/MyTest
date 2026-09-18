package app.focusphone

import android.app.Activity
import android.view.View

/** findViewById that fails loudly if the layout and the code disagree. */
inline fun <reified T : View> Activity.bind(id: Int): T =
    findViewById<T>(id) ?: throw IllegalStateException("Missing view #$id in ${javaClass.simpleName}")
