package com.applock.numberlock.util

import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import com.applock.numberlock.R

/** Fills in as many PIN indicator dots as there are digits entered so far. */
fun updatePinDots(dots: List<ImageView>, filledCount: Int) {
    dots.forEachIndexed { index, dot ->
        dot.setImageResource(if (index < filledCount) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
    }
}

/** A short horizontal shake, used to signal a wrong PIN. */
fun shake(view: View) {
    val animation = TranslateAnimation(0f, 24f, 0f, 0f).apply {
        duration = 60
        repeatCount = 5
        repeatMode = Animation.REVERSE
    }
    view.startAnimation(animation)
}
