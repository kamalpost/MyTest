package com.applock.numberlock.data

/**
 * In-memory record of which locked apps the user has already unlocked in the
 * current "session", so switching briefly to another app and back (or opening
 * a system dialog) doesn't force re-entry of the PIN every time. Cleared
 * whenever the screen turns off or the user returns to the home launcher.
 */
object LockSessionManager {

    private val unlockedPackages = mutableSetOf<String>()

    @Synchronized
    fun markUnlocked(packageName: String) {
        unlockedPackages.add(packageName)
    }

    @Synchronized
    fun isUnlocked(packageName: String): Boolean = packageName in unlockedPackages

    @Synchronized
    fun clearAll() {
        unlockedPackages.clear()
    }

    @Synchronized
    fun clear(packageName: String) {
        unlockedPackages.remove(packageName)
    }
}
