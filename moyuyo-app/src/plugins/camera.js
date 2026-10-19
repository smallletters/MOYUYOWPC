/**
 * 相机/相册原生插件调用封装
 * 插件标识: MOYUYO-Camera
 * 功能: 拍照、选图、压缩、裁剪
 */
import { t } from '@/i18n'
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-Camera'

/**
 * 拍照
 * @param {Object} [options]
 * @param {Boolean} [options.enableCrop] 是否裁剪
 * @param {Number} [options.maxWidth] 最大宽度
 * @param {Number} [options.maxHeight] 最大高度
 * @returns {Promise<Object>} { imagePath, thumbnailPath, fileSize }
 */
export function takePhoto(options = {}) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.takePhoto(options, (result) => {
      if (result.success) {
        resolve({
          imagePath: result.imagePath,
          thumbnailPath: result.thumbnailPath,
          fileSize: result.fileSize,
        })
      } else {
        reject(new Error(result.message || t('cameraPlugin.takePhotoFailed')))
      }
    })
  })
  // #endif

  // #ifndef APP-PLUS
  // H5/小程序降级: 使用 uni 内置 API
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sourceType: ['camera'],
      success: (res) => resolve({ imagePath: res.tempFilePaths[0] }),
      fail: () => reject(new Error(t('cameraPlugin.takePhotoCancelled'))),
    })
  })
  // #endif
}

/**
 * 从相册选择图片
 * @param {Object} [options]
 * @param {Number} [options.count] 最多选择数
 * @param {Boolean} [options.enableCrop] 是否裁剪
 * @returns {Promise<String[]>} 图片路径数组
 */
export function pickImage(options = {}) {
  const { count = 1, enableCrop = false } = options

  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.pickFromGallery({ count, enableCrop }, (result) => {
      if (result.success) {
        resolve(result.images || [])
      } else {
        reject(new Error(result.message || t('cameraPlugin.pickFailed')))
      }
    })
  })
  // #endif

  // #ifndef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sourceType: ['album'],
      success: (res) => resolve(res.tempFilePaths),
      fail: () => reject(new Error(t('cameraPlugin.pickCancelled'))),
    })
  })
  // #endif
}

/**
 * 压缩图片
 * @param {String} imagePath 原图路径
 * @param {Object} [options]
 * @param {Number} [options.quality] 压缩质量 0-100
 * @param {Number} [options.maxWidth] 最大宽度
 * @param {Number} [options.maxHeight] 最大高度
 * @returns {Promise<String>} 压缩后路径
 */
export function compressImage(imagePath, options = {}) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.compress({ imagePath, ...options }, (result) => {
      if (result.success) {
        resolve(result.compressedPath)
      } else {
        reject(new Error(result.message || t('cameraPlugin.compressFailed')))
      }
    })
  })
  // #endif

  // #ifndef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.compressImage({
      src: imagePath,
      quality: options.quality || 80,
      success: (res) => resolve(res.tempFilePath),
      fail: () => reject(new Error(t('cameraPlugin.compressFailed'))),
    })
  })
  // #endif
}

export default { takePhoto, pickImage, compressImage }
