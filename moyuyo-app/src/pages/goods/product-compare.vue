<template>
  <view class="product-compare">
    <scroll-view class="scroll" scroll-y>
      <view class="thumb-row">
        <view v-for="(product, idx) in products" :key="product.id" class="thumb-item">
          <view class="thumb-img" :style="{ background: product.color }">
            <text class="thumb-placeholder">{{ product.name.slice(0, 2) }}</text>
          </view>
          <text class="thumb-label">{{ product.name }}</text>
          <view class="thumb-remove" @click.stop="removeProduct(idx)">
            <text class="remove-icon luc-x" />
          </view>
        </view>
        <view
          v-for="n in 4 - products.length"
          :key="'empty-' + n"
          class="thumb-item thumb-empty"
          @click="showAddPicker = true"
        >
          <view class="thumb-add">
            <text class="add-icon">+</text>
          </view>
          <text class="thumb-label muted">添加</text>
        </view>
      </view>

      <view class="compare-table">
        <view class="table-row header-row">
          <view class="row-label">对比项</view>
          <view v-for="product in products" :key="product.id" class="row-value">
            <view class="product-header">
              <view class="product-img" :style="{ background: product.color }">
                <text class="img-placeholder">{{ product.name.slice(0, 2) }}</text>
              </view>
              <text class="product-name">{{ product.name }}</text>
              <text class="product-price">${{ product.price }}</text>
              <view class="product-rating">
                <text class="star luc-star" />
                <text class="rating-num">{{ product.rating }}</text>
              </view>
            </view>
          </view>
        </view>

        <view
          v-for="spec in specRows"
          :key="spec.key"
          class="table-row"
          :class="{ highlight: spec.highlight }"
        >
          <view class="row-label">{{ spec.label }}</view>
          <view v-for="product in products" :key="product.id" class="row-value">
            <text
              v-if="spec.key === 'freeShipping'"
              class="check-icon luc"
              :class="[product[spec.key] ? 'luc-check' : 'luc-x', { checked: product[spec.key] }]"
            />
            <text
              v-else-if="spec.key === 'subscribe'"
              class="check-icon luc"
              :class="[
                product[spec.key] ? 'luc-check' : 'luc-minus',
                { checked: product[spec.key] },
              ]"
            />
            <text
              v-else
              class="cell-text"
              :class="{ highlight: spec.highlight && spec.key === 'material' }"
            >
              {{ product[spec.key] }}
            </text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view
        v-for="product in products"
        :key="product.id"
        class="cart-btn"
        @click="addToCart(product)"
      >
        <text class="cart-btn-text">加入购物车 - ${{ product.price }}</text>
      </view>
    </view>

    <view v-if="showAddPicker" class="modal-overlay" @click="showAddPicker = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">选择对比商品</text>
        <scroll-view class="modal-list" scroll-y>
          <view
            v-for="item in availableProducts"
            :key="item.id"
            class="modal-item"
            @click="addProduct(item)"
          >
            <view class="modal-item-img" :style="{ background: item.color }">
              <text class="modal-item-placeholder">{{ item.name.slice(0, 2) }}</text>
            </view>
            <view class="modal-item-info">
              <text class="modal-item-name">{{ item.name }}</text>
              <text class="modal-item-price">${{ item.price }}</text>
            </view>
          </view>
        </scroll-view>
        <view class="modal-close" @click="showAddPicker = false">
          <text>关闭</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
const MOCK_PRODUCTS = [
  {
    id: 1,
    name: '高端宠物洗护套装',
    price: '28.00',
    rating: '4.8',
    brand: 'PawPure',
    size: 'XS-XL',
    material: '天然有机',
    colorOptions: '3色可选',
    features: ['低敏配方', '持久留香'],
    freeShipping: true,
    subscribe: true,
    color: 'linear-gradient(135deg, #e8f2ff, #cfe5ff)',
  },
  {
    id: 2,
    name: '舒适胸背带',
    price: '35.00',
    rating: '4.5',
    brand: 'WalkSafe',
    size: 'S-L',
    material: '普通材质',
    colorOptions: '5色可选',
    features: ['透气网布', '均匀受力'],
    freeShipping: false,
    subscribe: false,
    color: 'linear-gradient(135deg, var(--color-fill, #f2f2f7), var(--color-fill-dim))',
  },
]

const AVAILABLE = [
  {
    id: 3,
    name: '互动益智玩具',
    price: '35.00',
    color: 'linear-gradient(135deg, #fff3e0, #ffe0b2)',
  },
  {
    id: 4,
    name: '宠物潮流外套',
    price: '110.00',
    color: 'linear-gradient(135deg, #fce4ec, #f8bbd0)',
  },
  { id: 5, name: '自动喂食器', price: '79.00', color: 'linear-gradient(135deg, #e0f2f1, #b2dfdb)' },
  { id: 6, name: '猫抓板套装', price: '45.00', color: 'linear-gradient(135deg, #f3e5f5, #e1bee7)' },
]

const SPEC_ROWS = [
  { key: 'brand', label: '品牌', highlight: false },
  { key: 'price', label: '价格', highlight: true },
  { key: 'rating', label: '评分', highlight: true },
  { key: 'size', label: '尺码范围', highlight: true },
  { key: 'material', label: '材质', highlight: true },
  { key: 'colorOptions', label: '颜色', highlight: true },
  { key: 'freeShipping', label: '是否包邮', highlight: true },
  { key: 'subscribe', label: '可订阅', highlight: true },
]

export default {
  pageTitleKey: 'pageTitle.goodsProductCompare',

  data() {
    return {
      products: [...MOCK_PRODUCTS],
      availableProducts: AVAILABLE,
      showAddPicker: false,
      specRows: SPEC_ROWS,
    }
  },

  methods: {
    removeProduct(idx) {
      this.products.splice(idx, 1)
    },

    addProduct(item) {
      if (this.products.length >= 4) {
        uni.showToast({ title: this.$t('productCompare.maxFour'), icon: 'none' })
        return
      }
      const fullItem = {
        id: item.id,
        name: item.name,
        price: item.price,
        rating: '4.0',
        brand: '通用品牌',
        size: '均码',
        material: '标准材质',
        colorOptions: '多色可选',
        features: ['常规款'],
        freeShipping: false,
        subscribe: false,
        color: item.color,
      }
      this.products.push(fullItem)
      this.showAddPicker = false
    },

    addToCart(product) {
      uni.showToast({
        title: this.$t('productCompare.addedToCart', { name: product.name }),
        icon: 'success',
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.product-compare {
  /* 视口锁定外壳：整页固定为 100vh 并裁剪溢出，消除「页面级滚动」，
     唯一滚动权威是内部的 <scroll-view class="scroll">（flex:1 占满剩余空间）。
     这样不会再出现外层滚动与内部 scroll-view 双层滚动互相抢手势 → 滑不动，
     底部栏作为常流 flex 子项也不会因页面橡皮筋(bounce)被拖动/形变。 */
  display: flex;
  flex-direction: column;
  /* 100vh 先写做兜底：不支持 dvh 的旧 webview/APP 端会忽略下一行并保留它 */
  height: 100vh;
  /* H5 移动浏览器：dvh 跟随动态视口，避免地址栏/工具栏把底部栏顶出可视区 */
  height: 100dvh;
  /* 非 custom 页（app-plus/H5）：100dvh 已含原生导航栏区，须减去 --window-top
     （标题栏高 + 状态栏高）让内容避开原生导航栏；只减 window-top，不减 window-bottom。 */
  height: calc(100dvh - var(--window-top, 0px));
  overflow: hidden;
  background: var(--color-background);
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.nav-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.nav-action {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.action-icon {
  font-size: 28rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-bold);
}

.action-text {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.scroll {
  /* 不再硬算高度：用 flex:1 占据剩余空间，min-height:0 覆盖 flex 项默认的
     min-height:auto，允许收缩到小于内容高度，使 <scroll-view> 内部真正产生
     overflow-y:auto 滚动，避免 calc(100vh - Nrpx) 在 rpx/env() 失效时退回 auto。 */
  flex: 1;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
}

.thumb-row {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  overflow-x: auto;
}

.thumb-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  width: 128rpx;
  position: relative;
  flex-shrink: 0;
}

.thumb-img {
  width: 112rpx;
  height: 112rpx;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid var(--color-primary);
}

.thumb-placeholder {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-semibold);
}

.thumb-label {
  font-size: 20rpx;
  color: var(--color-text-secondary);
  max-width: 128rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thumb-label.muted {
  color: var(--color-text-tertiary);
}

.thumb-remove {
  position: absolute;
  top: -8rpx;
  right: 4rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: var(--color-danger);
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-icon {
  color: #fff;
  font-size: 24rpx;
  line-height: 1;
}

.thumb-empty {
  cursor: pointer;
}

.thumb-add {
  width: 112rpx;
  height: 112rpx;
  border-radius: var(--radius-md);
  border: 2rpx dashed var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
}

.add-icon {
  font-size: 40rpx;
  color: var(--color-text-tertiary);
}

.compare-table {
  margin: 0 24rpx 24rpx;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.table-row {
  display: grid;
  grid-template-columns: 160rpx 1fr;
  border-bottom: 1rpx solid var(--color-divider);
}

.table-row:last-child {
  border-bottom: none;
}

.table-row.header-row {
  grid-template-columns: 160rpx 1fr;
}

.table-row.highlight {
  background: rgba(171, 185, 173, 0.08);
}

.row-label {
  padding: 20rpx;
  display: flex;
  align-items: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
  border-right: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.row-value {
  padding: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8rpx;
  border-left: 1rpx solid var(--color-divider);
}

.product-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.product-img {
  width: 100rpx;
  height: 100rpx;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.img-placeholder {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-semibold);
}

.product-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  text-align: center;
}

.product-price {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
}

.product-rating {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.star {
  font-size: 24rpx;
  color: var(--color-primary);
}

.rating-num {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.cell-text {
  font-size: var(--font-size-base);
  color: var(--color-text);
  text-align: center;
}

.cell-text.highlight {
  color: var(--color-success);
  font-weight: var(--font-weight-semibold);
}

.check-icon {
  font-size: 32rpx;
}

.check-icon.checked {
  color: var(--color-success);
}

.check-icon:not(.checked) {
  color: var(--color-danger);
}

.bottom-bar {
  /* 作为视口锁定外壳(.product-compare)的最后一个常流 flex 子项，天然稳定贴底，
     不再使用 position:fixed：页面不发生滚动，底栏就不会随拖动位移/形变。 */
  flex-shrink: 0;
  /* 保持 content-box：safe-area 的 padding-bottom 需叠加在内容盒之外，
     改成 border-box 会把安全区内缩进内容盒，把按钮压扁。 */
  box-sizing: content-box;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.cart-btn {
  flex: 1;
  height: 80rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cart-btn-text {
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
}

.modal-content {
  /* 列 flex 容器：max-height:60vh 封顶，内部 .modal-list 用 flex:1 占据剩余高度，
     从而给 <scroll-view> 一个确定高度、恢复其滚动能力（同一类根因的修复）。 */
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 60vh;
  background: var(--color-surface);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: 32rpx;
}

.modal-title {
  /* 固定高度子项：必须 flex-shrink:0，否则会被 .modal-list 挤压塌陷 */
  flex-shrink: 0;
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 24rpx;
  text-align: center;
}

.modal-list {
  /* 删除 max-height：改由 flex:1 占据 .modal-content 剩余高度（父级 max-height:60vh 封顶），
     min-height:0 允许收缩，使 <scroll-view> 获得确定高度后能正常滚动。 */
  flex: 1;
  min-height: 0;
}

.modal-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.modal-item-img {
  width: 80rpx;
  height: 80rpx;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.modal-item-placeholder {
  font-size: 20rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-semibold);
}

.modal-item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.modal-item-name {
  font-size: var(--font-size-base);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.modal-item-price {
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.modal-close {
  /* 固定高度子项：必须 flex-shrink:0，否则会被 .modal-list 挤压 */
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0 0;
  font-size: var(--font-size-base);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}
</style>
