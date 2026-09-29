import { get, post, put, del } from '@/utils/request'
import { withRetry } from '@/utils/retry'

export function getAddressList() {
  return get('/api/v1/addresses')
}

export function getAddressDetail(id) {
  return get(`/api/v1/addresses/${id}`)
}

export function createAddress(data) {
  return post('/api/v1/addresses', data)
}

export function updateAddress(id, data) {
  return put(`/api/v1/addresses/${id}`, data)
}

export function deleteAddress(id) {
  return del(`/api/v1/addresses/${id}`)
}

export function setDefaultAddress(id) {
  return put(`/api/v1/addresses/${id}/default`)
}

export function validateAddress(id) {
  return get(`/api/v1/addresses/${id}/validate`)
}

// 批量校验：APP 进入地址列表页一次请求拿到所有地址的可发货结果，避免 N+1
export function batchValidateAddresses(ids) {
  return post('/api/v1/addresses/batch-validate', ids)
}

// 当前所有被运营配置过的国家码（去重排序），供 APP 国家下拉使用
// 弱网下重试：picker 必须有可选项，不能因一次网络抖动就让用户看不到所有国家。
export function getSupportedCountries() {
  return withRetry(() => get('/api/v1/addresses/supported-countries'), {
    retries: 2,
    delaysMs: [300, 800],
  })
}

export default {
  getAddressList,
  getAddressDetail,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
  validateAddress,
  batchValidateAddresses,
  getSupportedCountries,
}
