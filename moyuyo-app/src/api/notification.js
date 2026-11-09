import { get, post, put, del } from '@/utils/request'

export function getNotifications(params = {}) {
  return get('/api/v1/notifications', params)
}
export function getNotificationDetail(id) {
  return get(`/api/v1/notifications/${id}`)
}
export function readNotification(id) {
  return put(`/api/v1/notifications/${id}/read`)
}
export function readAllNotifications() {
  return post('/api/v1/notifications/read-all')
}
export function deleteNotification(id) {
  return del(`/api/v1/notifications/${id}`)
}

/**
 * 批量删除通知
 * @param {number[]} [ids]  传空/不传表示按分类清空
 * @param {string}   [type] 分类（order/activity/system），不传表示全部
 */
export function deleteNotifications(ids, type) {
  const params = {}
  if (ids && ids.length) params.ids = ids.join(',')
  if (type) params.type = type
  return del('/api/v1/notifications', params)
}

export default {
  getNotifications,
  getNotificationDetail,
  readNotification,
  readAllNotifications,
  deleteNotification,
  deleteNotifications,
}
