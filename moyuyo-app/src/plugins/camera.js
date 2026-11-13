/**
 * 相机/相册调用封装（uni 官方 API）
 *
 * 原 MOYUYO-Camera 原生插件已移除（桩实现），统一改用：
 * - 拍照/选图：uni.chooseImage（全平台）
 * - 压缩：uni.compressImage（App/小程序；H5 直接返回原图）
 */
import { t } from '@/i18n'
/* eslint-disable no-unreachable */

/**
 * 拍照
 * @param {Object} [options]
 * @param {Array<String>} [options.sizeType] 图片尺寸 compressed | original
 * @returns {Promise<Object>} { imagePath }
 */
export function takePhoto(options = {}) {
  const { sizeType = ['compressed'] } = options
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sourceType: ['camera'],
      sizeType,
      success: (res) => resolve({ imagePath: res.tempFilePaths[0] }),
      fail: () => reject(new Error(t('cameraPlugin.takePhotoCancelled'))),
    })
  })
}

/**
 * 从相册选择图片
 * @param {Object} [options]
 * @param {Number} [options.count] 最多选择数
 * @param {Array<String>} [options.sizeType] 图片尺寸 compressed | original
 * @returns {Promise<String[]>} 图片路径数组
 */
export function pickImage(options = {}) {
  const { count = 1, sizeType = ['compressed'] } = options
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sourceType: ['album'],
      sizeType,
      success: (res) => resolve(res.tempFilePaths),
      fail: () => reject(new Error(t('cameraPlugin.pickCancelled'))),
    })
  })
}

/**
 * 压缩图片
 * @param {String} imagePath 原图路径
 * @param {Object} [options]
 * @param {Number} [options.quality] 压缩质量 0-100
 * @returns {Promise<String>} 压缩后路径
 */
export function compressImage(imagePath, options = {}) {
  // #ifdef H5
  // H5 端不支持 uni.compressImage，直接返回原路径
  return Promise.resolve(imagePath)
  // #endif

  // #ifndef H5
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
