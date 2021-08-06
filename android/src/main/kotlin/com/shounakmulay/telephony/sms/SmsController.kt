package com.shounakmulay.telephony.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.shounakmulay.telephony.utils.Constants.ACTION_SMS_DELIVERED
import com.shounakmulay.telephony.utils.Constants.ACTION_SMS_SENT
import com.shounakmulay.telephony.utils.Constants.SMS_BODY
import com.shounakmulay.telephony.utils.Constants.SMS_DELIVERED_BROADCAST_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.SMS_SENT_BROADCAST_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.SMS_TO
import com.shounakmulay.telephony.utils.ContentUri


class SmsController(private val context: Context) {

    // FETCH SMS
    fun getMessages(
        contentUri: ContentUri,
        projection: List<String>,
        selection: String?,
        selectionArgs: List<String>?,
        sortOrder: String?
    ): List<HashMap<String, String?>> {
        val messages = mutableListOf<HashMap<String, String?>>()

        val cursor = context.contentResolver.query(
            contentUri.uri,
            projection.toTypedArray(),
            selection,
            selectionArgs?.toTypedArray(),
            sortOrder
        )

        while (cursor != null && cursor.moveToNext()) {
            val dataObject = HashMap<String, String?>(projection.size)
            for (columnName in cursor.columnNames) {
                val value = cursor.getString(cursor.getColumnIndex(columnName))
                dataObject[columnName] = value
            }
            messages.add(dataObject)
        }

        cursor?.close()

        return messages

    }

    // SEND SMS
    fun sendSms(destinationAddress: String, messageBody: String, simSlot: Int, listenStatus: Boolean) {
        val smsManager = getSmsManager()
        if (listenStatus) {
            val pendingIntents = getPendingIntents()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SmsManager.getSmsManagerForSubscriptionId(simSlot).sendTextMessage(destinationAddress, null, messageBody, pendingIntents.first, pendingIntents.second)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SmsManager.getSmsManagerForSubscriptionId(simSlot).sendTextMessage(destinationAddress, null, messageBody, null, null)
            }
        }
    }

    fun sendMultipartSms(destinationAddress: String, messageBody: String, listenStatus: Boolean) {
        val smsManager = getSmsManager()
        val messageParts = smsManager.divideMessage(messageBody)
        if (listenStatus) {
            val pendingIntents = getMultiplePendingIntents(messageParts.size)
            smsManager.sendMultipartTextMessage(destinationAddress, null, messageParts, pendingIntents.first, pendingIntents.second)
        } else {
            smsManager.sendMultipartTextMessage(destinationAddress, null, messageParts, null, null)
        }
    }

    private fun getMultiplePendingIntents(size: Int): Pair<ArrayList<PendingIntent>, ArrayList<PendingIntent>> {
        val sentPendingIntents = arrayListOf<PendingIntent>()
        val deliveredPendingIntents = arrayListOf<PendingIntent>()
        for (i in 1..size) {
            val pendingIntents = getPendingIntents()
            sentPendingIntents.add(pendingIntents.first)
            deliveredPendingIntents.add(pendingIntents.second)
        }
        return Pair(sentPendingIntents, deliveredPendingIntents)
    }

    fun sendSmsIntent(destinationAddress: String, messageBody: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(SMS_TO + destinationAddress)
            putExtra(SMS_BODY, messageBody)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.applicationContext.startActivity(intent)
    }

    private fun getPendingIntents(): Pair<PendingIntent, PendingIntent> {
        val sentIntent = Intent(ACTION_SMS_SENT).apply {
            `package` = context.applicationContext.packageName
            flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY
        }
        val sentPendingIntent = PendingIntent.getBroadcast(context, SMS_SENT_BROADCAST_REQUEST_CODE, sentIntent, PendingIntent.FLAG_ONE_SHOT)

        val deliveredIntent = Intent(ACTION_SMS_DELIVERED).apply {
            `package` = context.applicationContext.packageName
            flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY
        }
        val deliveredPendingIntent = PendingIntent.getBroadcast(context, SMS_DELIVERED_BROADCAST_REQUEST_CODE, deliveredIntent, PendingIntent.FLAG_ONE_SHOT)

        return Pair(sentPendingIntent, deliveredPendingIntent)
    }

    private fun getSmsManager(): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionId = SmsManager.getDefaultSmsSubscriptionId()
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        }
        return SmsManager.getDefault()
    }

    // PHONE
    fun openDialer(phoneNumber: String) {
        val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(dialerIntent)
    }

    @RequiresPermission(allOf = [Manifest.permission.CALL_PHONE])
    fun dialPhoneNumber(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (callIntent.resolveActivity(context.packageManager) != null) {
            context.applicationContext.startActivity(callIntent)
        }
    }

    // STATUS
    fun isSmsCapable(sim: Int): Boolean {
        val telephonyManager = getTelephonyManager(sim)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            telephonyManager.isSmsCapable
        } else {
            val packageManager = context.packageManager
            if (packageManager != null) {
                return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            }
            return false
        }
    }

    fun getCellularDataState(sim: Int): Int {
        return getTelephonyManager(sim).dataState
    }

    fun getCallState(sim: Int): Int {
        return getTelephonyManager(sim).callState
    }

    fun getDataActivity(sim: Int): Int {
        return getTelephonyManager(sim).dataActivity
    }

    fun getNetworkOperator(sim: Int): String {
        return getTelephonyManager(sim).networkOperator
    }

    fun getNetworkOperatorName(sim: Int): String {
        return getTelephonyManager(sim).networkOperatorName
    }

    @SuppressLint("MissingPermission")
    fun getDataNetworkType(sim: Int): Int {
        val telephonyManager = getTelephonyManager(sim)
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            telephonyManager.dataNetworkType
        } else {
            telephonyManager.networkType
        }
    }

    fun getPhoneType(sim: Int): Int {
        return getTelephonyManager(sim).phoneType
    }

    fun getSimOperator(sim: Int): String {
        return getTelephonyManager(sim).simOperator
    }

    fun getSimOperatorName(sim: Int): String {
        return getTelephonyManager(sim).simOperatorName
    }

    fun getSimState(sim: Int): Int {
        return getTelephonyManager(sim).simState
    }

    fun isNetworkRoaming(sim: Int): Boolean {
        return getTelephonyManager(sim).isNetworkRoaming
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    fun getServiceState(sim: Int): Int? {
        val serviceState = getTelephonyManager(sim).serviceState
        return serviceState?.state
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getSignalStrength(sim: Int): List<Int>? {
        val signalStrength = getTelephonyManager(sim).signalStrength
        return signalStrength?.cellSignalStrengths?.map {
            return@map it.level
        }
    }

    private fun getTelephonyManager(sim: Int): TelephonyManager {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        Log.d("testdi", "${sim}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephonyManager.createForSubscriptionId(sim)
        } else {
            return context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }
    }
}