import { defineStore } from 'pinia'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'
import { cartApi } from '@/api'
import { useUserStore } from './user'

/**
 * 购物车条目可用状态（与后端 CartEntity.cartStatus 对齐）：
 * - VALID        可售（可勾选结算）
 * - OUT_OF_STOCK 缺货/售罄（主列表置灰展示，禁止勾选结算）
 * - OFF_SALE     商品已下架
 * - PRODUCT_GONE 商品已删除
 * - INVALID_SKU  规格(SKU)已失效
 * 后三种进入"失效商品"折叠区；无 cartStatus 的旧数据按可售兜底
 */
const isSellable = (i) => !i.cartStatus || i.cartStatus === 'VALID'
const isInvalid = (i) =>
  i.cartStatus === 'OFF_SALE' || i.cartStatus === 'PRODUCT_GONE' || i.cartStatus === 'INVALID_SKU'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: getStorage(STORAGE_KEYS.CART, []),
    selectedAddressId: '',
    selectedCoupon: null,
    // 立即购买临时单品(不写入购物车 items,仅用于结算页直接结算;刷新后仍保留)
    buyNowItem: getStorage(STORAGE_KEYS.BUYNOW_ITEM, null),
  }),

  getters: {
    // 角标/合计只统计仍展示在主列表的商品(可售+缺货)，失效(下架/删除/规格失效)不计入，避免误导
    totalQuantity: (state) =>
      state.items.filter((i) => !isInvalid(i)).reduce((sum, item) => sum + (item.quantity || 0), 0),
    totalPrice: (state) =>
      state.items
        .filter((i) => !isInvalid(i))
        .reduce((sum, item) => sum + (item.price || 0) * (item.quantity || 0), 0),
    // 结算只统计"可售且勾选"的商品;缺货/下架/失效一律不计入,防止误结算
    selectedQuantity: (state) =>
      state.items
        .filter((i) => i.checked && isSellable(i))
        .reduce((sum, i) => sum + (i.quantity || 0), 0),
    selectedPrice: (state) =>
      state.items
        .filter((i) => i.checked && isSellable(i))
        .reduce((sum, i) => sum + (i.price || 0) * (i.quantity || 0), 0),
    selectedItems: (state) => state.items.filter((i) => i.checked && isSellable(i)),
    isAllChecked: (state) => {
      const sellable = state.items.filter(isSellable)
      return sellable.length > 0 && sellable.every((i) => i.checked)
    },
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
          // 保留本地勾选状态(勾选是用户偏好,服务端 selected 仅兜底)，
          // 避免“重进页面 sync 后取消勾选被重置、误结算未勾商品”。
          const prevItems = this.items || []
          this.items = serverItems.map((item) => {
            const key = item.skuId || item.productId
            const prior = prevItems.find((i) => (i.skuId || i.productId) === key)
            return {
              cartId: item.id,
              skuId: item.skuId,
              productId: item.productId,
              name: item.productName,
              image: item.mainImage,
              price: item.price,
              quantity: item.quantity,
              // 服务端返回的当前可用库存,用于前端限制加购数量上限
              stock: typeof item.stock === 'number' ? item.stock : null,
              // 服务端计算的可用状态(可售/缺货/已下架/失效)
              cartStatus: item.cartStatus || null,
              // 失效/缺货一律不勾；有效商品优先沿用本地勾选，本地无记录才用服务端
              checked: isSellable({ cartStatus: item.cartStatus || null })
                ? prior
                  ? prior.checked
                  : item.selected !== false
                : false,
              sku: item.skuSpec || '',
              attrs: item.skuSpec ? [{ name: '规格', value: item.skuSpec }] : [],
            }
          })
          this.persist()
        }
      } catch (e) {
        console.warn('[cart] syncFromServer failed, using local cart', e)
      }
    },

    async addItem(product, quantity = 1) {
      const key = product.skuId || product.variationId || product.productId
      const exist = this.items.find((i) => (i.skuId || i.variationId || i.productId) === key)
      // 库存上限:仅当传入数字库存时才限制(缺失时交由后端校验)
      const stockLimit =
        typeof product.stock === 'number' && Number.isFinite(product.stock)
          ? Math.max(0, product.stock)
          : null

      // 超库存时直接拦截,避免购物车数量超过可售库存
      if (exist && stockLimit !== null && exist.quantity + quantity > stockLimit) {
        uni.showToast({ title: stockLimit <= 0 ? '该商品已售罄' : '库存不足', icon: 'none' })
        return false
      }
      if (!exist && stockLimit !== null && quantity > stockLimit) {
        uni.showToast({ title: stockLimit <= 0 ? '该商品已售罄' : '库存不足', icon: 'none' })
        return false
      }

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
          stock: typeof product.stock === 'number' ? product.stock : null,
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
          // 服务端拒绝(多为库存已变化):回滚本地加购并提示,保持两端一致
          if (exist) {
            exist.quantity -= quantity
            if (exist.quantity <= 0) {
              this.items = this.items.filter((i) => i !== exist)
            }
          } else {
            this.items = this.items.filter((i) => (i.skuId || i.variationId || i.productId) !== key)
          }
          this.persist()
          uni.showToast({ title: e?.message || '加购失败', icon: 'none' })
          return false
        }
      }
      return true
    },

    async updateQuantity(key, quantity) {
      const item = this.items.find((i) => (i.skuId || i.variationId || i.productId) === key)
      if (!item) return
      const before = item.quantity
      const isIncrease = quantity > before
      // 库存上限:仅增加数量时封顶(数字库存才限制,缺失时交由后端校验)
      const stockLimit =
        typeof item.stock === 'number' && Number.isFinite(item.stock) ? item.stock : null

      let target = Math.max(1, quantity)
      if (isIncrease && stockLimit !== null) {
        if (target > stockLimit) {
          target = stockLimit
        }
        // 库存已售罄/已达上限:提示并保持原数量
        if (target <= before) {
          uni.showToast({ title: stockLimit <= 0 ? '该商品已售罄' : '库存不足', icon: 'none' })
          return
        }
      }
      if (target === before) return
      item.quantity = target
      this.persist()

      const userStore = useUserStore()
      if (userStore.isLoggedIn && item?.skuId) {
        try {
          await cartApi.updateQuantity(item.skuId, item.quantity)
        } catch (e) {
          // 服务端库存已变化导致拒绝:回滚到原数量并提示
          item.quantity = before
          this.persist()
          uni.showToast({ title: e?.message || '库存不足', icon: 'none' })
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
      if (!item) return
      // 缺货/失效商品不允许勾选结算
      if (item.cartStatus === 'OUT_OF_STOCK' || !isSellable(item)) {
        uni.showToast({
          title:
            item.cartStatus === 'OUT_OF_STOCK' ? '商品缺货,暂不能结算' : '该商品已失效,不能结算',
          icon: 'none',
        })
        return
      }
      item.checked = !item.checked
      this.persist()
    },

    toggleCheckAll(checked) {
      this.items.forEach((i) => {
        // 全不选对所有生效;全选只勾可售商品(缺货/失效保持不选)
        i.checked = checked && isSellable(i)
      })
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
