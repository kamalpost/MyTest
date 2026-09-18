package app.focusphone.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

data class Contact(val name: String, val number: String)

/** Read-only view of the device address book. */
class ContactsRepo(private val ctx: Context) {
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Every phone number, real names first; entries whose "name" is just a number come last. */
    fun all(): List<Contact> = query(null)

    /** Contacts starred as favourites in the phone's Contacts app. */
    fun favourites(): List<Contact> = query(ContactsContract.CommonDataKinds.Phone.STARRED + " = 1")

    private fun query(selection: String?): List<Contact> {
        if (!hasPermission()) return emptyList()
        val named = ArrayList<Contact>()
        val unnamed = ArrayList<Contact>()
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, selection, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim() ?: continue
                    val number = c.getString(1)?.trim() ?: continue
                    if (number.isEmpty() || name.isEmpty()) continue
                    if (!seen.add(name + "|" + normalize(number))) continue
                    if (looksLikeNumber(name)) unnamed.add(Contact(name, number)) else named.add(Contact(name, number))
                }
            }
        } catch (e: Exception) {
            // provider unavailable
        }
        return named + unnamed
    }

    fun nameFor(number: String): String? {
        if (!hasPermission() || number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun normalize(n: String): String = n.filter { it.isDigit() || it == '+' }.takeLast(10)

        /** "+91 98765 43210", "#3" or "(555) 123" — a display name with no letters in it. */
        fun looksLikeNumber(name: String): Boolean = name.none { it.isLetter() }
    }
}
