package app.focusphone.focus

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Device-admin hook. Making the app device owner
 * (`adb shell dpm set-device-owner app.focusphone/.focus.FocusAdminReceiver`)
 * turns screen pinning into a real kiosk that cannot be escaped with Back+Recents.
 */
class FocusAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, FocusAdminReceiver::class.java)
    }
}
