package com.jupiter.filemanager.data.index

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jupiter.filemanager.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationDeliveryStatus {
    DELIVERED,
    BLOCKED,
    FAILED,
}

data class NotificationDeliveryResult(
    val status: NotificationDeliveryStatus,
    val detail: String? = null,
)

data class DuplicateNotificationHealth(
    val runtimePermissionGranted: Boolean,
    val applicationNotificationsEnabled: Boolean,
    val channelEnabled: Boolean,
) {
    val canDeliver: Boolean
        get() = runtimePermissionGranted && applicationNotificationsEnabled && channelEnabled
}

/** Testable boundary around the Android notification subsystem. */
interface DuplicateNotificationPublisher {
    fun health(): DuplicateNotificationHealth
    fun publish(alert: DuplicateAlert): NotificationDeliveryResult
    fun publishPendingSummary(decisions: List<DedupDecision>): NotificationDeliveryResult
}

/**
 * Android delivery implementation that distinguishes a real publish attempt from every common
 * system-level block. A denied runtime permission, disabled application notifications, or a
 * user-disabled duplicate channel remains retryable instead of being treated as delivered.
 */
@Singleton
class AndroidDuplicateNotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) : DuplicateNotificationPublisher {

    override fun health(): DuplicateNotificationHealth {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }
        return DuplicateNotificationHealth(
            runtimePermissionGranted = permissionGranted,
            applicationNotificationsEnabled = notificationsEnabled,
            channelEnabled = channelEnabled,
        )
    }

    override fun publish(alert: DuplicateAlert): NotificationDeliveryResult {
        val count = alert.existing.size
        val (title, text, idSalt) = when (alert.kind) {
            DuplicateKind.EXACT -> Triple(
                "New download already on your device",
                "${alert.newFile.name} — you already have " +
                    if (count == 1) "1 copy" else "$count copies",
                0,
            )
            DuplicateKind.SIMILAR -> Triple(
                "Similar file detected",
                "${alert.newFile.name} — ${alert.explanation}",
                SIMILAR_ID_SALT,
            )
        }
        return publishNotification(
            notificationId = alert.newFile.path.hashCode() xor idSalt,
            title = title,
            text = text,
        )
    }

    override fun publishPendingSummary(decisions: List<DedupDecision>): NotificationDeliveryResult {
        if (decisions.isEmpty()) return NotificationDeliveryResult(NotificationDeliveryStatus.DELIVERED)
        val exact = decisions.count { it.kind == DuplicateKind.EXACT.name }
        val similar = decisions.size - exact
        val detail = buildList {
            if (exact > 0) add("$exact exact")
            if (similar > 0) add("$similar similar")
        }.joinToString(" and ")
        val latestName = decisions.last().newPath.substringAfterLast('/').substringAfterLast('\\')
        return publishNotification(
            notificationId = PENDING_SUMMARY_ID,
            title = "Duplicate files detected",
            text = "${decisions.size} pending duplicate ${if (decisions.size == 1) "alert" else "alerts"} " +
                "($detail). Latest: $latestName",
        )
    }

    @SuppressLint("MissingPermission") // checked by health() immediately before NotificationManagerCompat.notify
    private fun publishNotification(
        notificationId: Int,
        title: String,
        text: String,
    ): NotificationDeliveryResult {
        val health = health()
        if (!health.runtimePermissionGranted) {
            return NotificationDeliveryResult(
                NotificationDeliveryStatus.BLOCKED,
                "POST_NOTIFICATIONS permission is not granted",
            )
        }
        if (!health.applicationNotificationsEnabled) {
            return NotificationDeliveryResult(
                NotificationDeliveryStatus.BLOCKED,
                "Application notifications are disabled",
            )
        }
        if (!health.channelEnabled) {
            return NotificationDeliveryResult(
                NotificationDeliveryStatus.BLOCKED,
                "Duplicate alert channel is disabled",
            )
        }

        return runCatching {
            val managerCompat = NotificationManagerCompat.from(context)
            val platformManager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                platformManager?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Duplicate alerts",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Alerts when a newly added file duplicates existing content"
                    },
                )
                if (platformManager?.getNotificationChannel(CHANNEL_ID)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
                ) {
                    return NotificationDeliveryResult(
                        NotificationDeliveryStatus.BLOCKED,
                        "Duplicate alert channel is disabled",
                    )
                }
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(reviewPendingIntent(notificationId))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            managerCompat.notify(notificationId, notification)
            NotificationDeliveryResult(NotificationDeliveryStatus.DELIVERED)
        }.getOrElse { error ->
            NotificationDeliveryResult(
                if (error is SecurityException) {
                    NotificationDeliveryStatus.BLOCKED
                } else {
                    NotificationDeliveryStatus.FAILED
                },
                error.message ?: error::class.java.simpleName,
            )
        }
    }

    /** Opens the actual duplicate-review surface, never a dead-end notification. */
    private fun reviewPendingIntent(notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.jupiter.filemanager.action.REVIEW_DUPLICATES"
            putExtra(MainActivity.DUPLICATE_REVIEW_EXTRA, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "jupiter_duplicate_alerts"
        const val SIMILAR_ID_SALT = 0x5A5A5A5A
        const val PENDING_SUMMARY_ID = 0x4A555049
    }
}
