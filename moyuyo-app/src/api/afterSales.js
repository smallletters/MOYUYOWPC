import { get, post } from '@/utils/request'

export function getAfterSales(params = {}) {
  return get('/api/v1/after-sales', params)
}

export function getAfterSaleDetail(id) {
  return get(`/api/v1/after-sales/${id}`)
}

/**
 * 申请售后（退货退款 / 仅退款 / 换货）
 * @param {object} data { orderId, orderItemId, type, reason, amount, quantity, description, images }
 */
export function createAfterSale(data) {
  return post('/api/v1/after-sales', data)
}

/** 用户填写退货物流（审核通过后） */
export function submitReturnLogistics(id, data) {
  return post(`/api/v1/after-sales/${id}/return-logistics`, data)
}

/** 取消售后申请（待审核/待寄回可取消） */
export function cancelAfterSale(id) {
  return post(`/api/v1/after-sales/${id}/cancel`)
}

export default {
  getAfterSales,
  getAfterSaleDetail,
  createAfterSale,
  submitReturnLogistics,
  cancelAfterSale,
}
