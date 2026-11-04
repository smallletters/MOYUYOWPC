import { get, post, put, del } from '@/utils/request'

export function getPets() {
  return get('/api/v1/pets')
}

export function getPetDetail(id) {
  return get(`/api/v1/pets/${id}`)
}

export function createPet(data) {
  return post('/api/v1/pets', data)
}

export function updatePet(id, data) {
  return put(`/api/v1/pets/${id}`, data)
}

export function updatePetStatus(id, status) {
  return put(`/api/v1/pets/${id}/status`, { status })
}

export function deletePet(id) {
  return del(`/api/v1/pets/${id}`)
}

export function getGrowthRecords(petId) {
  return get(`/api/v1/pets/${petId}/records`)
}

export function createGrowthRecord(petId, data) {
  return post(`/api/v1/pets/${petId}/records`, data)
}

export function deleteGrowthRecord(petId, recordId) {
  return del(`/api/v1/pets/${petId}/records/${recordId}`)
}

export function getReminders(petId) {
  return get(`/api/v1/pets/${petId}/reminders`)
}

export function updateReminder(petId, reminderId, data) {
  return put(`/api/v1/pets/${petId}/reminders/${reminderId}`, data)
}

export function getCareSummary(petId) {
  return get(`/api/v1/pets/${petId}/care-summary`)
}

export function getAchievements(petId) {
  return get(`/api/v1/pets/${petId}/achievements`)
}

/**
 * 3D 空间模型配置：管理后台启用的房间场景 / 静态角色 / 行走角色
 * 失败时静默返回，由调用方回退到内置模型地址
 */
export function getPetSpaceModels() {
  return get('/api/v1/pet-space/models', {}, { showError: false })
}

export default {
  getPets,
  getPetDetail,
  createPet,
  updatePet,
  updatePetStatus,
  deletePet,
  getGrowthRecords,
  createGrowthRecord,
  deleteGrowthRecord,
  getReminders,
  updateReminder,
  getCareSummary,
  getAchievements,
  getPetSpaceModels,
}
