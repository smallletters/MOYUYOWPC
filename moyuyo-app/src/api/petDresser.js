import { get, post, del } from '@/utils/request'

// 装扮列表：系统装扮(user_id=NULL) + 当前用户的自定义装扮(user_id=当前用户)
export function getPetOutfits(petId) {
  return get(`/api/v1/pets/${petId}/dresser`)
}

// 装备装扮
export function equipOutfit(petId, id) {
  return post(`/api/v1/pets/${petId}/dresser/${id}/equip`)
}

// 删除装扮：仅用户自定义装扮可删(userId 非 NULL)，系统装扮禁止删除
export function deleteOutfit(petId, id) {
  return del(`/api/v1/pets/${petId}/dresser/${id}`)
}

/**
 * 上传自定义装扮形象。
 * 前端完整流程：
 * 1. uni.chooseImage 选图 → uploadApi.uploadImage 拿到 imageUrl
 * 2. 调用本方法把 {category, name, imageUrl} 提交到后端
 * 后端会写入 mo_pet_outfit(user_id=当前用户)，APP 更新不会丢失。
 * @param {number} petId
 * @param {object} data { category, name, imageUrl }
 */
export function uploadOutfit(petId, data) {
  return post(`/api/v1/pets/${petId}/dresser/upload`, data)
}

export default {
  getPetOutfits,
  equipOutfit,
  deleteOutfit,
  uploadOutfit,
}
