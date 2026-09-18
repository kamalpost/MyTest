package app.focusphone.phone.screens

import app.focusphone.focus.FocusModeController
import app.focusphone.focus.FocusScheduler
import app.focusphone.phone.ScreenHost

class AboutScreen(host: ScreenHost) : InfoScreen(host, "About", {
    val ctx = host.activity
    val prefs = ctx.prefs
    val owner = FocusModeController.isDeviceOwner(ctx)
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    } catch (e: Exception) {
        "?"
    }
    buildString {
        append("FocusPhone $version\n\n")
        append("Focus since ${FocusScheduler.fmt(prefs.sessionStartedAt)}\n")
        append("Focus until ${FocusScheduler.fmt(prefs.sessionEndAt)}\n\n")
        append("Kiosk: ").append(if (owner) "device owner (locked)" else if (prefs.kioskEnabled) "screen pinning" else "off").append('\n')
        append("Silence: ").append(if (prefs.dndApplied) "on" else "off").append("\n\n")
        append("Press Break on the home screen to return to your smartphone.")
    }
})
