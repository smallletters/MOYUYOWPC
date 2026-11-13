/**
 * 定位/地图调用封装（uni 官方 API）
 *
 * 原 MOYUYO-Location 原生插件已移除（桩实现），统一改用：
 * - 定位：uni.getLocation
 * - 地图选点：uni.chooseLocation（App 端需在 manifest.json 配置地图模块）
 * - 地理编码/物流地图：uni 无官方能力，明确降级
 */
import { t } from '@/i18n'

/**
 * 获取当前定位
 * @param {Object} [options]
 * @param {Boolean} [options.highAccuracy] 是否使用高精度模式
 * @returns {Promise<Object>} { latitude, longitude }
 */
export function getCurrentLocation(options = {}) {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'wgs84',
      isHighAccuracy: options.highAccuracy !== false,
      success: (res) => resolve({ latitude: res.latitude, longitude: res.longitude }),
      fail: () => reject(new Error(t('locationPlugin.locatePermission'))),
    })
  })
}

/**
 * 打开地图地址选择器
 * @param {Object} [options]
 * @param {Number} [options.latitude] 初始纬度
 * @param {Number} [options.longitude] 初始经度
 * @returns {Promise<Object>} { latitude, longitude, address, name }
 */
export function openMapSelector(options = {}) {
  return new Promise((resolve, reject) => {
    // 仅在传入坐标时设置初始位置，避免把 undefined 传给地图选点
    const params = {}
    if (options.latitude != null && options.longitude != null) {
      params.latitude = options.latitude
      params.longitude = options.longitude
    }
    uni.chooseLocation({
      ...params,
      success: (res) =>
        resolve({
          latitude: res.latitude,
          longitude: res.longitude,
          address: res.address,
          name: res.name,
        }),
      fail: () => reject(new Error(t('locationPlugin.chooseAddressCancelled'))),
    })
  })
}

/**
 * 打开物流轨迹地图
 * uni 无官方物流地图 API，保留签名并提示不支持
 */
export function showLogisticsMap() {
  uni.showToast({ title: t('locationPlugin.unsupported'), icon: 'none' })
}

/**
 * 地理编码：地址转坐标
 * uni 无官方能力，需由后端提供
 * @returns {Promise<never>}
 */
export function geocode() {
  return Promise.reject(new Error(t('locationPlugin.unsupported')))
}

export default { getCurrentLocation, openMapSelector, showLogisticsMap, geocode }
