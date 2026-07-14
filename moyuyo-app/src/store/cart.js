/**
 * 购物车状态管理
 * 需求：本地优先的购物车（游客也可加购），下单时同步到 WooCommerce 订单
 */
import { defineStore } from 'pinia'
import { setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

export const useCartStore = defineStore('cart', {
  state: () => ({
    // items: [{ productId, name, image, price, quantity, sku, variationId, attrs }]
    items: getStorage(STORAGE_KEYS.CART, []),
    // 当前选中的地址 ID
    selectedAddressId: '',
    // 当前选中的优惠券
    selectedCoupon: null
  }),

  getters: {
    // 总数量
    totalQuantity: (state) =>
      state.items.reduce((sum, item) => sum + item.quantity, 0),

    // 总价（不含运费）
    totalPrice: (state) =>
      state.items.reduce((sum, item) => sum + item.price * item.quantity, 0),

    // 选中商品的数量（用于结算按钮）
    selectedQuantity: (state) =>
      state.items.filter((i) => i.checked).reduce((sum, i) => sum + i.quantity, 0),

    // 选中商品的总价
    selectedPrice: (state) =>
      state.items.filter((i) => i.checked).reduce((sum, i) => sum + i.price * i.quantity, 0),

    // 选中商品列表
    selectedItems: (state) => state.items.filter((i) => i.checked),

    // 是否全选
    isAllChecked: (state) =>
      state.items.length > 0 && state.items.every((i) => i.checked)
  },

  actions: {
    persist() {
      setStorage(STORAGE_KEYS.CART, this.items)
    },

    /**
     * 加入购物车
     * 已存在的 SKU 累加数量
     */
    addItem(product, quantity = 1) {
      const key = product.variationId || product.productId
      const exist = this.items.find((i) => (i.variationId || i.productId) === key)
      if (exist) {
        exist.quantity += quantity
      } else {
        this.items.push({
          productId: product.productId,
          variationId: product.variationId || null,
          name: product.name,
          image: product.image,
          price: product.price,
          quantity,
          sku: product.sku || '',
          attrs: product.attrs || [],
          checked: true
        })
      }
      this.persist()
    },

    /**
     * 更新数量
     */
    updateQuantity(key, quantity) {
      const item = this.items.find((i) => (i.variationId || i.productId) === key)
      if (item) {
        item.quantity = Math.max(1, quantity)
        this.persist()
      }
    },

    /**
     * 删除商品
     */
    removeItem(key) {
      this.items = this.items.filter((i) => (i.variationId || i.productId) !== key)
      this.persist()
    },

    /**
     * 选中 / 取消选中
     */
    toggleCheck(key) {
      const item = this.items.find((i) => (i.variationId || i.productId) === key)
      if (item) {
        item.checked = !item.checked
        this.persist()
      }
    },

    /**
     * 全选 / 取消全选
     */
    toggleCheckAll(checked) {
      this.items.forEach((i) => (i.checked = checked))
      this.persist()
    },

    /**
     * 清空购物车（下单成功后）
     */
    clear() {
      this.items = []
      this.selectedCoupon = null
      this.persist()
    }
  }
})
