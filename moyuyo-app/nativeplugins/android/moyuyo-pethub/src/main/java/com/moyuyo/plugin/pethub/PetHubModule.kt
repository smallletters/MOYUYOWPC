package com.moyuyo.plugin.pethub

import android.app.Activity
import android.content.Intent
import com.alibaba.fastjson.JSONObject
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.bridge.UniJSCallback
import io.dcloud.feature.uniapp.common.UniModule

/**
 * 3D Pet Hub 原生插件
 * - 基于 Filament 引擎渲染 3D 宠物模型
 * - 支持手势交互、IP 动画、换装
 */
class PetHubModule : UniModule() {

  override fun onActivityCreate() {
    super.onActivityCreate()
  }

  override fun onActivityDestroy() {
    super.onActivityDestroy()
  }

  /**
   * 打开 3D 宠物场景
   */
  @UniJSMethod(uiThread = true)
  fun openScene(params: JSONObject, callback: UniJSCallback) {
    val petId = params.getString("petId") ?: ""
    val petType = params.getString("petType") ?: "cat"

    if (petId.isEmpty()) {
      callback.invoke(JSONObject().apply {
        put("success", false)
        put("message", "petId 不能为空")
      })
      return
    }

    val intent = Intent(requireContext(), PetHubActivity::class.java).apply {
      putExtra("petId", petId)
      putExtra("petType", petType)
      params.getString("breed")?.let { putExtra("breed", it) }
      params.getJSONObject("customization")?.let { putExtra("customization", it.toJSONString()) }
    }
    startActivity(intent)

    callback.invoke(JSONObject().apply {
      put("success", true)
      put("data", JSONObject().apply {
        put("message", "场景已启动")
      })
    })
  }

  /**
   * 宠物换装
   */
  @UniJSMethod(uiThread = true)
  fun applyCosmetic(params: JSONObject, callback: UniJSCallback) {
    val petId = params.getString("petId") ?: ""
    val slot = params.getString("slot") ?: ""
    val itemId = params.getString("itemId") ?: ""

    if (petId.isEmpty() || itemId.isEmpty()) {
      callback.invoke(JSONObject().apply {
        put("success", false)
        put("message", "参数不完整")
      })
      return
    }

    callback.invoke(JSONObject().apply {
      put("success", true)
      put("data", JSONObject().apply {
        put("petId", petId)
        put("slot", slot)
        put("itemId", itemId)
        put("status", "applied")
      })
    })
  }

  /**
   * 播放宠物动画
   */
  @UniJSMethod(uiThread = false)
  fun playAnimation(params: JSONObject, callback: UniJSCallback) {
    val result = JSONObject().apply {
      put("success", true)
    }
    callback.invoke(result)
  }

  /**
   * 截取 3D 场景截图
   */
  @UniJSMethod(uiThread = true)
  fun captureSnapshot(params: JSONObject, callback: UniJSCallback) {
    callback.invoke(JSONObject().apply {
      put("success", false)
      put("message", "当前版本暂不支持截图")
    })
  }

  /**
   * 关闭 3D 场景
   */
  @UniJSMethod(uiThread = false)
  fun closeScene(params: JSONObject, callback: UniJSCallback) {
    callback.invoke(JSONObject().apply {
      put("success", true)
    })
  }

  /** UniModule 未暴露 mContext，统一从 mUniSDKInstance 取宿主 Context */
  private fun requireContext(): android.content.Context {
    return mUniSDKInstance?.context
      ?: throw IllegalStateException("UniSDKInstance context is unavailable")
  }

  private fun startActivity(intent: Intent) {
    val ctx = requireContext()
    if (ctx !is Activity) {
      // 非 Activity 上下文（Application）必须加 NEW_TASK，否则 Android 会抛 RuntimeException
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
  }
}
