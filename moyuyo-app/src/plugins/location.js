/**
 * 定位/地图原生插件调用封装
 * 插件标识: MOYUYO-Location
 * 功能: GPS 定位、地址选择、物流轨迹地图
 */
import { t } from '@/i18n'
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-Location'

/**
 * 获取当前定位
 * @param {Object} [options]
 * @param {Boolean} [options.highAccuracy] 是否使用高精度模式
 * @returns {Promise<Object>} { latitude, longitude, address, country, city }
 */
export function getCurrentLocation(options = {}) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.getCurrentLocation({ highAccuracy: options.highAccuracy !== false }, (result) => {
      if (result.success) {
        resolve({
          latitude: result.latitude,
          longitude: result.longitude,
          address: result.address,
          country: result.country,
          city: result.city,
        })
      } else {
        reject(new Error(result.message || t('locationPlugin.locateFailed')))
      }
    })
  })
  // #endif

  // #ifndef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'wgs84',
      success: (res) => resolve({ latitude: res.latitude, longitude: res.longitude }),
      fail: () => reject(new Error(t('locationPlugin.locatePermission'))),
    })
  })
  // #endif
}

/**
 * 打开地图地址选择器
 * @param {Object} [options]
 * @param {Number} [options.latitude] 初始纬度
 * @param {Number} [options.longitude] 初始经度
 * @returns {Promise<Object>} { latitude, longitude, address, name }
 */
export function openMapSelector(options = {}) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.openMapSelector(options, (result) => {
      if (result.success) {
        resolve({
          latitude: result.latitude,
          longitude: result.longitude,
          address: result.address,
          name: result.name,
        })
      } else {
        reject(new Error(result.message || t('locationPlugin.chooseAddressCancelled')))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error(t('locationPlugin.unsupported')))
  /* #endif */
}

/**
 * 打开物流轨迹地图
 * @param {Object} params
 * @param {String} params.trackingNumber 运单号
 * @param {String} params.carrierCode 承运商代码
 * @param {Array<Object>} [params.trackingPoints] 可选追踪点 [{ latitude, longitude, timestamp }]
 */
export function showLogisticsMap(params) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.showLogisticsMap(params, () => {})
  // #endif
}

/**
 * 地理编码：地址转坐标
 * @param {String} address 地址文本
 * @returns {Promise<Object>} { latitude, longitude }
 */
export function geocode(address) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.geocode({ address }, (result) => {
      if (result.success) {
        resolve({ latitude: result.latitude, longitude: result.longitude })
      } else {
        reject(new Error(result.message || t('locationPlugin.geocodeFailed')))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error(t('locationPlugin.unsupported')))
  /* #endif */
}

export default { getCurrentLocation, openMapSelector, showLogisticsMap, geocode }
