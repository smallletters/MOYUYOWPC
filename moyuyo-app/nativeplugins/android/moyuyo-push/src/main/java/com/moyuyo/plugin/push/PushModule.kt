package com.moyuyo.plugin.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.alibaba.fastjson.JSONObject
import com.google.firebase.messaging.FirebaseMessaging
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.bridge.UniJSCallback
import io.dcloud.feature.uniapp.common.UniModule

/**
 * 推送通知原生插件（Android）
 * <p>
 * 接入 Google FCM：
 * - 将 Firebase 控制台下载的 google-services.json 放到
 *   nativeplugins/android/moyuyo-push/src/main/assets/google-services.json
 * - 本插件在运行时手动读取该文件并初始化 FirebaseApp，不依赖 google-services Gradle 插件，
 *   因此缺少配置文件时只返回失败，不会导致 HBuilderX 打包失败
 * <p>
 * 安全：不硬编码任何密钥，全部来自 google-services.json。
 */
class PushModule : UniModule() {

  @UniJSMethod(uiThread = true)
  fun register(params: JSONObject, callback: UniJSCallback) {
    val context = mUniSDKInstance?.context
    if (context == null) {
      callback.invoke(errorResult("插件上下文不可用"))
      return
    }
    requestNotificationPermission(context)
    fetchToken(context, callback)
  }

  @UniJSMethod(uiThread = true)
  fun getToken(params: JSONObject, callback: UniJSCallback) {
    val context = mUniSDKInstance?.context
    if (context == null) {
      callback.invoke(JSONObject().apply { put("token", "") })
      return
    }
    fetchToken(context, callback)
  }

  @UniJSMethod(uiThread = false)
  fun handleMessage(params: JSONObject, callback: UniJSCallback) {
    // 前台消息展示由 FcmMessagingService 统一处理，这里仅确认收到
    callback.invoke(JSONObject().apply { put("success", true) })
  }

  @UniJSMethod(uiThread = false)
  fun setBadge(params: JSONObject, callback: UniJSCallback) {
    // Android 无统一的桌面角标 API，保留 success 以兼容前端调用
    callback.invoke(JSONObject().apply { put("success", true) })
  }

  @UniJSMethod(uiThread = false)
  fun clearBadge(params: JSONObject, callback: UniJSCallback) {
    callback.invoke(JSONObject().apply { put("success", true) })
  }

  @UniJSMethod(uiThread = true)
  fun setEnabled(params: JSONObject, callback: UniJSCallback) {
    val context = mUniSDKInstance?.context
    if (context == null) {
      callback.invoke(errorResult("插件上下文不可用"))
      return
    }
    val enabled = params.getBooleanValue("enabled")
    prefs(context).edit().putBoolean(PushPrefs.KEY_ENABLED, enabled).apply()
    if (!enabled) {
      // 关闭推送时删除 FCM token，服务端将无法再推送到本设备
      try {
        FirebaseMessaging.getInstance().deleteToken()
      } catch (e: Exception) {
        // Firebase 未初始化时忽略
      }
    } else {
      requestNotificationPermission(context)
    }
    callback.invoke(JSONObject().apply { put("success", true) })
  }

  @UniJSMethod(uiThread = false)
  fun isEnabled(params: JSONObject, callback: UniJSCallback) {
    val context = mUniSDKInstance?.context
    val enabled = context?.let { prefs(it).getBoolean(PushPrefs.KEY_ENABLED, true) } ?: true
    callback.invoke(JSONObject().apply { put("enabled", enabled) })
  }

  /**
   * 获取 FCM token（异步回调切回主线程）
   */
  private fun fetchToken(context: Context, callback: UniJSCallback) {
    if (!isEnabledInternal(context)) {
      callback.invoke(JSONObject().apply {
        put("success", true)
        put("token", "")
        put("message", "推送已被用户关闭")
      })
      return
    }
    val app = FirebaseInitializer.ensureInitialized(context)
    if (app == null) {
      callback.invoke(errorResult("未找到 google-services.json 或 Firebase 初始化失败"))
      return
    }
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      val result = JSONObject()
      if (task.isSuccessful) {
        result.put("success", true)
        result.put("token", task.result ?: "")
      } else {
        result.put("success", false)
        result.put("message", task.exception?.message ?: "获取 FCM token 失败")
      }
      Handler(Looper.getMainLooper()).post { callback.invoke(result) }
    }
  }

  private fun isEnabledInternal(context: Context): Boolean =
    prefs(context).getBoolean(PushPrefs.KEY_ENABLED, true)

  private fun prefs(context: Context) =
    context.getSharedPreferences(PushPrefs.NAME, Context.MODE_PRIVATE)

  /**
   * Android 13+ 需要运行时通知权限，否则 FCM 通知不会展示
   */
  private fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return
    }
    val activity = context as? Activity ?: return
    if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
      return
    }
    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PUSH_PERMISSION_REQUEST_CODE)
  }

  private fun errorResult(message: String) = JSONObject().apply {
    put("success", false)
    put("message", message)
  }

  private companion object {
    const val PUSH_PERMISSION_REQUEST_CODE = 1001
  }
}

/**
 * 推送相关 SharedPreferences 常量
 */
internal object PushPrefs {
  const val NAME = "moyuyo_push"
  const val KEY_ENABLED = "enabled"
  const val KEY_TOKEN = "token"
}
