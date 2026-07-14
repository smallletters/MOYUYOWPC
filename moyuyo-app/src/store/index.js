/**
 * Pinia 入口
 */
import { createPinia } from 'pinia'

export const pinia = createPinia()

export { useUserStore } from './user'
export { useCartStore } from './cart'
export { useProductStore } from './product'
export { useThemeStore } from './theme'
