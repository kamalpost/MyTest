package app.focusphone.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import app.focusphone.data.Prefs
import app.focusphone.focus.SmsSentReceiver

data class SmsMessage(val address: String, val body: String, val date: Long, val outgoing: Boolean)
data class SmsThread(val address: String, val last: SmsMessage, val count: Int)

/** Reads the SMS inbox and sends texts. Sent messages are mirrored in Prefs. */
class SmsRepo(private val ctx: Context) {
    private val prefs = Prefs(ctx)

    fun canRead(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun canSend(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun allMessages(): List<SmsMessage> {
        val out = ArrayList<SmsMessage>()
        if (canRead()) {
            val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
            try {
                ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, proj, null, null, Telephony.Sms.DATE + " DESC LIMIT 500")
                    ?.use { c ->
                        while (c.moveToNext()) {
                            val addr = c.getString(0) ?: continue
                            val body = c.getString(1) ?: ""
                            val date = c.getLong(2)
                            val type = c.getInt(3)
                            out.add(SmsMessage(addr, body, date, type != Telephony.Sms.MESSAGE_TYPE_INBOX))
                        }
                    }
            } catch (e: Exception) {
                // Some OEM providers reject LIMIT in the sort order; fall back to no limit.
                ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, proj, null, null, Telephony.Sms.DATE + " DESC")
                    ?.use { c ->
                        var n = 0
                        while (c.moveToNext() && n++ < 500) {
                            val addr = c.getString(0) ?: continue
                            out.add(SmsMessage(addr, c.getString(1) ?: "", c.getLong(2), c.getInt(3) != Telephony.Sms.MESSAGE_TYPE_INBOX))
                        }
                    }
            }
        }
        prefs.sentMessages.forEach { out.add(SmsMessage(it.address, it.body, it.date, true)) }
        return out.sortedByDescending { it.date }
    }

    fun threads(): List<SmsThread> {
        val byAddr = LinkedHashMap<String, MutableList<SmsMessage>>()
        for (m in allMessages()) {
            byAddr.getOrPut(ContactsRepo.normalize(m.address)) { ArrayList() }.add(m)
        }
        return byAddr.values.map { list -> SmsThread(list.first().address, list.first(), list.size) }
            .sortedByDescending { it.last.date }
    }

    fun conversation(address: String): List<SmsMessage> {
        val key = ContactsRepo.normalize(address)
        return allMessages().filter { ContactsRepo.normalize(it.address) == key }.sortedBy { it.date }
    }

    /** Returns null on success, otherwise an error message. */
    fun send(address: String, body: String): String? {
        if (!canSend()) return "No SMS permission"
        if (address.isBlank() || body.isBlank()) return "Nothing to send"
        return try {
            val sm = smsManager()
            val parts = sm.divideMessage(body)
            val sent = sentIntent(address)
            if (parts.size <= 1) {
                sm.sendTextMessage(address, null, body, sent, null)
            } else {
                val intents = ArrayList<PendingIntent>(parts.size)
                // Only the last part reports, so the UI sees one result per message.
                for (i in 0 until parts.size - 1) intents.add(sentIntent(address, silent = true))
                intents.add(sent)
                sm.sendMultipartTextMessage(address, null, parts, intents, null)
            }
            prefs.addSentMessage(address, body)
            null
        } catch (e: Exception) {
            e.message ?: "Send failed"
        }
    }

    /**
     * Dual-SIM phones without a default SMS SIM would otherwise pop a SIM chooser, which
     * kiosk mode blocks, so the subscription is chosen explicitly.
     */
    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager {
        var subId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) subId = firstActiveSubscription()
        val system: SmsManager? = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else null
        return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            system?.createForSubscriptionId(subId) ?: SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            system ?: SmsManager.getDefault()
        }
    }

    private fun firstActiveSubscription(): Int = try {
        val subs = ctx.getSystemService(SubscriptionManager::class.java)
        subs?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    } catch (e: SecurityException) {
        SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private var requestCounter = (System.currentTimeMillis() and 0xffff).toInt()

    private fun sentIntent(address: String, silent: Boolean = false): PendingIntent {
        val i = Intent(ctx, SmsSentReceiver::class.java)
            .putExtra(SmsSentReceiver.EXTRA_ADDRESS, address)
            .putExtra("silent", silent)
        return PendingIntent.getBroadcast(
            ctx, requestCounter++, i, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
