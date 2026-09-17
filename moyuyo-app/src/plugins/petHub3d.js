/**
 * 3D Pet Hub 原生插件调用封装
 * 插件标识: MOYUYO-PetHub3D
 * 功能: 3D 宠物渲染、手势交互、IP 动画、换装
 */
/* eslint-disable no-unreachable */
import { t } from '@/i18n'

const PLUGIN_NAME = 'MOYUYO-PetHub3D'

/**
 * 打开 3D Pet Hub 场景
 * @param {Object} params
 * @param {String} params.petId 宠物 ID
 * @param {String} params.petType cat | dog
 * @param {String} params.breed 品种标识
 * @param {Object} params.customization 自定义参数 { color, pattern }
 */
export function openPetHub3d(params) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.openScene(params, (result) => {
      if (result.success) {
        resolve(result.data)
      } else {
        reject(new Error(result.message || t('plugin.petHub3dSceneFailed')))
      }
    })
  })
  // #endif

  // #ifndef APP-PLUS
  // H5/小程序降级: 显示 2D 静态宠物卡片
  uni.showToast({ title: t('plugin.petHub3dAppOnly'), icon: 'none' })
  return Promise.reject(new Error(t('plugin.petHub3dUnsupported')))
  // #endif
}

/**
 * 为宠物更换服饰/配饰
 * @param {Object} params
 * @param {String} params.petId 宠物 ID
 * @param {String} params.slot 部位 BODY | HEAD | ACCESSORY
 * @param {String} params.itemId 装扮 ID
 */
export function applyPetCosmetic(params) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.applyCosmetic(params, (result) => {
      if (result.success) {
        resolve(result.data)
      } else {
        reject(new Error(result.message || t('plugin.petHub3dCosmeticFailed')))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持'))
  /* #endif */
}

/**
 * 播放宠物动画
 * @param {String} petId 宠物 ID
 * @param {String} animationId 动画 ID
 */
export function playPetAnimation(petId, animationId) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.playAnimation({ petId, animationId }, () => {})
  // #endif
}

/**
 * 截取 3D 场景截图
 * @returns {Promise<String>} 截图本地路径
 */
export function capturePetSnapshot() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.captureSnapshot({}, (result) => {
      if (result.success) {
        resolve(result.imagePath)
      } else {
        reject(new Error(result.message || t('plugin.petHub3dSnapshotFailed')))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持'))
  /* #endif */
}

/**
 * 关闭 3D 场景释放资源
 */
export function closePetHub3d() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.closeScene({}, () => {})
  // #endif
}

export default {
  openPetHub3d,
  applyPetCosmetic,
  playPetAnimation,
  capturePetSnapshot,
  closePetHub3d,
}
