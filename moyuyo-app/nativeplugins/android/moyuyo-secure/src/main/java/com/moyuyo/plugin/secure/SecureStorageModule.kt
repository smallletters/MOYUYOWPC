package com.moyuyo.plugin.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alibaba.fastjson.JSONObject
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.bridge.UniJSCallback
import io.dcloud.feature.uniapp.common.UniModule

/**
 * 加密存储原生插件（MOYUYO-SecureStorage）
 *
 * 仅用于存放长效凭证（refreshToken）。底层使用 androidx.security 的
 * EncryptedSharedPreferences：密钥由 Android Keystore 托管（AES256-GCM），
 * 落盘内容为密文，设备被 root / 备份导出后无法直接读取明文。
 *
 * 设计约束：
 * 1. 所有方法声明 uiThread = false，加解密在子线程执行，避免阻塞 UI
 * 2. 任何加密异常（密钥失效 / 数据损坏）都先清空存储再返回失败，
 *    让前端降级为"未登录"，而不是让调用方崩溃
 * 3. 不申请任何权限，不写外部存储
 */
class SecureStorageModule : UniModule() {

  private var prefs: SharedPreferences? = null

  companion object {
    /** 独立 SP 文件名：与业务其它 SharedPreferences 隔离，便于整体清理 */
    private const val PREFS_NAME = "moyuyo_secure_prefs"
  }

  /** 懒加载 EncryptedSharedPreferences（首次调用时创建 Keystore 主密钥） */
  @Synchronized
  private fun obtainPrefs(): SharedPreferences {
    prefs?.let { return it }
    val ctx = requireContext()
    val masterKey = MasterKey.Builder(ctx)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    val created = EncryptedSharedPreferences.create(
      ctx,
      PREFS_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    prefs = created
    return created
  }

  private fun requireContext(): Context {
    val ctx = mUniSDKInstance?.context?.applicationContext
    if (ctx == null) {
      throw IllegalStateException("UniSDKInstance context is unavailable")
    }
    return ctx
  }

  /** 密钥失效/数据损坏时整体清空，避免后续每次调用都抛同一个异常 */
  private fun resetPrefs() {
    prefs = null
    try {
      requireContext().deleteSharedPreferences(PREFS_NAME)
    } catch (ignore: Exception) {
      // 清理失败不阻断：上层已拿到失败结果，下次调用会重新建库
    }
  }

  @UniJSMethod(uiThread = false)
  fun setItem(params: JSONObject?, callback: UniJSCallback?) {
    val key = params?.getString("key")
    val value = params?.getString("value")
    if (key.isNullOrBlank() || value == null) {
      callback?.invoke(fail("key/value 不能为空"))
      return
    }
    try {
      val ok = obtainPrefs().edit().putString(key, value).commit()
      callback?.invoke(if (ok) ok("") else fail("写入失败"))
    } catch (e: Exception) {
      resetPrefs()
      callback?.invoke(fail(e.message))
    }
  }

  @UniJSMethod(uiThread = false)
  fun getItem(params: JSONObject?, callback: UniJSCallback?) {
    val key = params?.getString("key")
    if (key.isNullOrBlank()) {
      callback?.invoke(fail("key 不能为空"))
      return
    }
    try {
      val value = obtainPrefs().getString(key, null)
      callback?.invoke(ok(value ?: ""))
    } catch (e: Exception) {
      // 典型场景：用户清除数据 / 恢复备份导致 Keystore 主密钥失配
      resetPrefs()
      callback?.invoke(fail(e.message))
    }
  }

  @UniJSMethod(uiThread = false)
  fun removeItem(params: JSONObject?, callback: UniJSCallback?) {
    val key = params?.getString("key")
    if (key.isNullOrBlank()) {
      callback?.invoke(fail("key 不能为空"))
      return
    }
    try {
      val ok = obtainPrefs().edit().remove(key).commit()
      callback?.invoke(if (ok) ok("") else fail("删除失败"))
    } catch (e: Exception) {
      resetPrefs()
      callback?.invoke(fail(e.message))
    }
  }

  @UniJSMethod(uiThread = false)
  fun clear(params: JSONObject?, callback: UniJSCallback?) {
    try {
      val ok = obtainPrefs().edit().clear().commit()
      callback?.invoke(if (ok) ok("") else fail("清空失败"))
    } catch (e: Exception) {
      resetPrefs()
      // 存储已损坏时清空本身必然失败，但对业务而言"数据已不可用"等价于清空成功
      callback?.invoke(ok(""))
    }
  }

  /** 能力探测：前端据此决定是否启用加密链路 */
  @UniJSMethod(uiThread = false)
  fun isAvailable(params: JSONObject?, callback: UniJSCallback?) {
    try {
      obtainPrefs()
      callback?.invoke(JSONObject().apply {
        put("available", true)
        put("backend", "EncryptedSharedPreferences")
      })
    } catch (e: Exception) {
      resetPrefs()
      callback?.invoke(JSONObject().apply {
        put("available", false)
        put("backend", "none")
        put("message", e.message ?: "unknown")
      })
    }
  }

  private fun ok(value: String): JSONObject = JSONObject().apply {
    put("success", true)
    put("value", value)
  }

  private fun fail(message: String?): JSONObject = JSONObject().apply {
    put("success", false)
    put("message", message ?: "unknown")
  }
}
