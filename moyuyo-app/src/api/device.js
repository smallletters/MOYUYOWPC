// 设备管理 API
import { get, post, del } from '@/utils/request'

export function listDevices(params = {}) {
  return get('/api/v1/devices', params)
}

export function removeDevice(id) {
  return del(`/api/v1/devices/${id}`)
}

export function trustDevice(id) {
  return post(`/api/v1/devices/${id}/trust`)
}

export function upsertDevice(body) {
  return post('/api/v1/devices/upsert', body)
}

export default { listDevices, removeDevice, trustDevice, upsertDevice }