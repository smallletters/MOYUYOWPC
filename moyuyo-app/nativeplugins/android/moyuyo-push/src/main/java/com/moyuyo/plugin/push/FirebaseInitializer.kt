package com.moyuyo.plugin.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

/**
 * Firebase 手动初始化器（Android）
 * <p>
 * 不依赖 google-services Gradle 插件：运行时从 assets/google-services.json 读取项目配置并初始化默认
 * FirebaseApp。这样在生产包中只要放入配置文件即可工作；缺少文件时返回 null，由调用方降级提示，
 * 不会阻塞应用启动或打包。
 */
object FirebaseInitializer {

  private const val CONFIG_ASSET = "google-services.json"

  /**
   * 确保默认 FirebaseApp 已初始化。
   *
   * @return 初始化成功返回 FirebaseApp；配置缺失/格式错误返回 null
   */
  fun ensureInitialized(context: Context): FirebaseApp? {
    // 已被 FirebaseInitProvider 自动初始化（配置了 google-services 插件）时直接复用
    try {
      return FirebaseApp.getInstance()
    } catch (ignored: IllegalStateException) {
      // 未初始化，继续手动初始化
    }

    val raw = readAsset(context, CONFIG_ASSET) ?: return null
    return try {
      val root = JSONObject(raw)
      val project = root.optJSONObject("project_info") ?: return null
      val client = root.optJSONArray("client")?.optJSONObject(0) ?: return null
      val clientInfo = client.optJSONObject("client_info") ?: return null

      val applicationId = clientInfo.optString("mobilesdk_app_id")
      if (applicationId.isEmpty()) {
        return null
      }
      val apiKey = client.optJSONArray("api_key")
        ?.optJSONObject(0)
        ?.optString("current_key")
        ?: ""

      val options = FirebaseOptions.Builder()
        .setApplicationId(applicationId)
        .setApiKey(apiKey)
        .setProjectId(project.optString("project_id"))
        .setGcmSenderId(project.optString("project_number"))
        .setStorageBucket(project.optString("storage_bucket"))
        .build()

      FirebaseApp.initializeApp(context, options)
    } catch (e: Exception) {
      null
    }
  }

  private fun readAsset(context: Context, name: String): String? =
    try {
      context.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
      null
    }
}
