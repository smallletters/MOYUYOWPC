/**
 * AR 试穿原生插件调用封装
 * 插件标识: MOYUYO-AR
 * 功能: AR 宠物服饰试穿、AR 追踪、拍照
 */
/* eslint-disable no-unreachable */
import { t } from '@/i18n'

const PLUGIN_NAME = 'MOYUYO-AR'

/**
 * 启动 AR 试穿
 * @param {Object} params
 * @param {String} params.productId 商品 ID
 * @param {String} params.modelUrl 3D 模型 URL
 * @param {String} params.petType cat | dog
 */
export function startArTryOn(params) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.startSession(params, (result) => {
      if (result.success) {
        resolve(result.data)
      } else {
        reject(new Error(result.message || t('plugin.arStartFailed')))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  uni.showToast({ title: t('plugin.arAppOnly'), icon: 'none' })
  return Promise.reject(new Error(t('plugin.arUnsupported')))
  /* #endif */
}

/**
 * AR 拍照
 * @returns {Promise<Object>} { imagePath, thumbnailPath }
 */
export function captureArPhoto() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.capturePhoto({}, (result) => {
      if (result.success) {
        resolve({
          imagePath: result.imagePath,
          thumbnailPath: result.thumbnailPath,
        })
      } else {
        reject(new Error(result.message || t('plugin.arCaptureFailed')))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持'))
  /* #endif */
}

/**
 * 切换试穿商品
 * @param {String} productId 新商品 ID
 */
export function switchArProduct(productId) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.switchProduct({ productId }, () => {})
  // #endif
}

/**
 * 结束 AR 会话释放资源
 */
export function stopArTryOn() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.stopSession({}, () => {})
  // #endif
}

export default { startArTryOn, captureArPhoto, switchArProduct, stopArTryOn }
