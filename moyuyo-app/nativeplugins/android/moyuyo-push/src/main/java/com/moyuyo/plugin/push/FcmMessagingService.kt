package com.moyuyo.plugin.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 消息接收服务（Android）
 * <p>
 * - onNewToken：token 刷新时写入本地，前端下次启动会通过 getToken/register 读取并上报服务端
 * - onMessageReceived：前台收到消息时主动构建系统通知（后台由 FCM 自动展示，不会重复）
 * <p>
 * 需要在 AndroidManifest.xml 注册 action=com.google.firebase.MESSAGING_EVENT。
 */
class FcmMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    getSharedPreferences(PushPrefs.NAME, MODE_PRIVATE)
      .edit()
      .putString(PushPrefs.KEY_TOKEN, token)
      .apply()
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    showNotification(message)
  }

  /**
   * 构建并展示通知栏消息（Android 8+ 需要通知渠道）
   */
  private fun showNotification(message: RemoteMessage) {
    val title = message.notification?.title
      ?: message.data["title"]
      ?: "MOYUYO"
    val body = message.notification?.body
      ?: message.data["body"]
      ?: ""

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureChannel(manager)

    // 点击通知回到 App，并把推送 data 透传过去（前端可自行读取 plus.runtime.arguments）
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      message.data.forEach { (key, value) -> putExtra(key, value) }
    }
    val pendingIntent = launchIntent?.let {
      PendingIntent.getActivity(
        this,
        1,
        it,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }

    val icon = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH)
    }
    builder
      .setSmallIcon(icon)
      .setContentTitle(title)
      .setContentText(body)
      .setAutoCancel(true)
    if (pendingIntent != null) {
      builder.setContentIntent(pendingIntent)
    }

    manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
  }

  private fun ensureChannel(manager: NotificationManager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    if (manager.getNotificationChannel(CHANNEL_ID) != null) {
      return
    }
    val channel = NotificationChannel(
      CHANNEL_ID,
      "MOYUYO 通知",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "订单、活动与系统通知"
      enableVibration(true)
    }
    manager.createNotificationChannel(channel)
  }

  private companion object {
    const val CHANNEL_ID = "moyuyo_push_default"
  }
}
