
const STORE = new Map()
export const STORAGE_KEYS = { LOCALE: 'moyuyo_locale' }
export function getStorage(k, d) { return STORE.has(k) ? STORE.get(k) : d }
export function setStorage(k, v) { STORE.set(k, v); return true }
