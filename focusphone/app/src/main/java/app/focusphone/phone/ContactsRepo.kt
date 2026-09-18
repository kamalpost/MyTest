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

    fun all(): List<Contact> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<Contact>()
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { c ->
            val seen = HashSet<String>()
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1)?.trim() ?: continue
                if (number.isEmpty()) continue
                if (seen.add(name + "|" + normalize(number))) out.add(Contact(name, number))
            }
        }
        return out
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
    }
}
