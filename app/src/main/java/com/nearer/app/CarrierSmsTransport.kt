package com.nearer.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

sealed class SmsDispatchResult {
    data class Dispatched(val segmentCount: Int) : SmsDispatchResult()
    data class Rejected(val reason: String) : SmsDispatchResult()
}

/**
 * Carrier-SMS fallback for NeaRer-to-NeaRer free-form chat.
 *
 * This intentionally does not report delivery: SmsManager dispatch is not a
 * delivery receipt. The receiving NeaRer device must parse and persist the
 * matching SmsEnvelope before the UI can show a received message.
 */
object CarrierSmsTransport {
    fun send(
        context: Context,
        destination: String,
        message: SmsEnvelope
    ): SmsDispatchResult {
        val phone = destination.filter { it.isDigit() || it == '+' }
        if (phone.count { it.isDigit() } < 7) {
            return SmsDispatchResult.Rejected("Enter a valid recipient phone number.")
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            return SmsDispatchResult.Rejected("This device does not support carrier SMS.")
        }
        if (!isDefaultSmsHandler(context)) {
            return SmsDispatchResult.Rejected("Set NeaRer as the default SMS app before using carrier chat.")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SmsDispatchResult.Rejected("SMS permission has not been granted.")
        }

        return try {
            val manager = SmsManager.getDefault()
            val parts = manager.divideMessage(SmsEnvelope.encode(message))
            if (parts.isNullOrEmpty()) {
                SmsDispatchResult.Rejected("The message could not be split for SMS.")
            } else {
                if (parts.size == 1) {
                    manager.sendTextMessage(phone, null, parts[0], null, null)
                } else {
                    manager.sendMultipartTextMessage(phone, null, parts, null, null)
                }
                SmsDispatchResult.Dispatched(parts.size)
            }
        } catch (error: SecurityException) {
            SmsDispatchResult.Rejected("Android blocked SMS sending. Check NeaRer's SMS role and permission.")
        } catch (error: IllegalArgumentException) {
            SmsDispatchResult.Rejected(error.message ?: "The phone number or message is invalid.")
        } catch (error: UnsupportedOperationException) {
            SmsDispatchResult.Rejected("Carrier SMS is not supported by this device.")
        } catch (error: Exception) {
            SmsDispatchResult.Rejected("SMS could not be queued: " + (error.message ?: "unknown error"))
        }
    }

    fun isDefaultSmsHandler(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            roles?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }
}
