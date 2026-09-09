import { get } from '@/utils/request'

/**
 * 地理位置代理（Google Places / Geocoding，海外）
 * 说明：API Key 由后端保管，前端只调用自有接口，不直连 Google。
 */
export function searchNearbyPlaces(params = {}) {
  return get('/api/v1/geo/places', params)
}

export function reverseGeocode(params = {}) {
  return get('/api/v1/geo/reverse', params)
}

export default {
  searchNearbyPlaces,
  reverseGeocode,
}
