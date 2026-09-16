// 设备管理 API
import { get, post, del } from '@/utils/request'

export function listDevices(params = {}) {
  return get('/api/v1/devices', params)
}

export function removeDevice(id) {
  return del(`/api/v1/devices/${id}`)
}

/**
 * 按 deviceId 删除当前用户的设备记录
 * 服务端按 (user_id, device_id) 复合条件删除,严格限制为本人设备
 * 用于登出场景:无需依赖 store.deviceList(可能来自别的账号),直接传设备指纹即可
 */
export function removeByDeviceId(deviceId) {
  return del('/api/v1/devices/by-device-id', { deviceId })
}

/**
 * 通用可信标记接口
 * @param {number|string} id 设备主键 id
 * @param {boolean} trusted true=设为可信,false=取消可信
 */
export function setTrust(id, trusted) {
  return post(`/api/v1/devices/${id}/trust`, { trusted: !!trusted })
}

/** 便捷方法:设为可信(等价 setTrust(id, true)) */
export function trustDevice(id) {
  return setTrust(id, true)
}

export function upsertDevice(body) {
  return post('/api/v1/devices/upsert', body)
}

export default { listDevices, removeDevice, removeByDeviceId, trustDevice, setTrust, upsertDevice }
