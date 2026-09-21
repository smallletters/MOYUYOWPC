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

export default {
  getNotifications,
  getNotificationDetail,
  readNotification,
  readAllNotifications,
  deleteNotification,
}
