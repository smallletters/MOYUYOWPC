import { defineStore } from 'pinia'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'
import { cartApi } from '@/api'
import { useUserStore } from './user'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: getStorage(STORAGE_KEYS.CART, []),
    selectedAddressId: '',
    selectedCoupon: null,
    // 立即购买临时单品(不写入购物车 items,仅用于结算页直接结算;刷新后仍保留)
    buyNowItem: getStorage(STORAGE_KEYS.BUYNOW_ITEM, null),
  }),

  getters: {
    totalQuantity: (state) => state.items.reduce((sum, item) => sum + (item.quantity || 0), 0),
    totalPrice: (state) =>
      state.items.reduce((sum, item) => sum + (item.price || 0) * (item.quantity || 0), 0),
    selectedQuantity: (state) =>
      state.items.filter((i) => i.checked).reduce((sum, i) => sum + (i.quantity || 0), 0),
    selectedPrice: (state) =>
      state.items
        .filter((i) => i.checked)
        .reduce((sum, i) => sum + (i.price || 0) * (i.quantity || 0), 0),
    selectedItems: (state) => state.items.filter((i) => i.checked),
    isAllChecked: (state) => state.items.length > 0 && state.items.every((i) => i.checked),
  },

  actions: {
    persist() {
      setStorage(STORAGE_KEYS.CART, this.items)
    },

    async syncFromServer() {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) return
      try {
        const serverItems = await cartApi.getCart()
        if (serverItems && serverItems.length > 0) {
          this.items = serverItems.map((item) => ({
            cartId: item.id,
            skuId: item.skuId,
            productId: item.productId,
            name: item.productName,
            image: item.mainImage,
            price: item.price,
            quantity: item.quantity,
            checked: item.selected !== false,
            sku: item.skuSpec || '',
            attrs: item.skuSpec ? [{ name: '规格', value: item.skuSpec }] : [],
          }))
          this.persist()
        }
      } catch (e) {
        console.warn('[cart] syncFromServer failed, using local cart', e)
      }
    },

    async addItem(product, quantity = 1) {
      const key = product.skuId || product.variationId || product.productId
      const exist = this.items.find((i) => (i.skuId || i.variationId || i.productId) === key)

      if (exist) {
        exist.quantity += quantity
      } else {
        this.items.push({
          cartId: null,
          skuId: product.skuId || null,
          productId: product.productId,
          variationId: product.variationId || null,
          name: product.name,
          image: product.image,
          price: product.price,
          quantity,
          sku: product.sku || '',
          attrs: product.attrs || [],
          checked: true,
        })
      }
      this.persist()

      const userStore = useUserStore()
      if (userStore.isLoggedIn && product.skuId) {
        try {
          await cartApi.addItem(product.skuId, quantity)
        } catch (e) {
          console.warn('[cart] server sync failed', e)
        }
      }
    },

    async updateQuantity(key, quantity) {
      const item = this.items.find((i) => (i.skuId || i.variationId || i.productId) === key)
      if (item) {
        item.quantity = Math.max(1, quantity)
        this.persist()
      }
      const userStore = useUserStore()
      if (userStore.isLoggedIn && item?.skuId) {
        try {
          await cartApi.updateQuantity(item.skuId, item.quantity)
        } catch (e) {
          /* ignore */
        }
      }
    },

    async removeItem(key) {
      const removed = this.items.find((i) => (i.skuId || i.variationId || i.productId) === key)
      this.items = this.items.filter((i) => (i.skuId || i.variationId || i.productId) !== key)
      this.persist()
      const userStore = useUserStore()
      if (userStore.isLoggedIn && removed?.skuId) {
        try {
          await cartApi.removeItem(removed.skuId)
        } catch (e) {
          /* ignore */
        }
      }
    },

    toggleCheck(key) {
      const item = this.items.find((i) => (i.skuId || i.variationId || i.productId) === key)
      if (item) {
        item.checked = !item.checked
        this.persist()
      }
    },

    toggleCheckAll(checked) {
      this.items.forEach((i) => (i.checked = checked))
      this.persist()
    },

    async clear() {
      this.items = []
      this.selectedCoupon = null
      this.persist()
      const userStore = useUserStore()
      if (userStore.isLoggedIn) {
        try {
          await cartApi.clearCart()
        } catch (e) {
          /* ignore */
        }
      }
    },

    async checkout(addressId, remark, couponId) {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) throw new Error('Please login first')
      const order = await cartApi.checkout(addressId, remark, couponId)
      this.items = this.items.filter((i) => !i.checked)
      this.persist()
      return order
    },

    /** 设置立即购买临时单品(不写入购物车 items,持久化防止刷新丢失) */
    setBuyNow(product) {
      const item = {
        cartId: null,
        skuId: product.skuId || null,
        productId: product.productId,
        variationId: product.variationId || null,
        name: product.name,
        image: product.image,
        price: product.price,
        quantity: product.quantity || 1,
        sku: product.sku || '',
        attrs: product.attrs || [],
        checked: true,
      }
      this.buyNowItem = item
      setStorage(STORAGE_KEYS.BUYNOW_ITEM, item)
    },

    /** 清除立即购买临时单品(同步删除本地存储) */
    clearBuyNow() {
      this.buyNowItem = null
      removeStorage(STORAGE_KEYS.BUYNOW_ITEM)
    },
  },
})
