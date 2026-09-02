<template>
  <view class="ar-tryon-page">
    <!-- 导航栏 -->


    <!-- 主视图区域 -->
    <view class="main-view">
      <!-- AR 预览区域 -->
      <view class="ar-preview">
        <!-- 模拟 AR 竖屏预览 -->
        <view class="ar-scene">
          <!-- 站位引导 -->
          <view v-if="!arActive" class="ar-guide" @click="startAr">
            <view class="guide-circle">
              <text class="guide-icon luc-smartphone" />
            </view>
            <text class="guide-text">点击开始 AR 试穿</text>
          </view>
          <!-- AR 活跃时的模拟视图 -->
          <view v-else class="ar-active-view">
            <!-- 轮廓线 -->
            <view class="silhouette">
              <view class="sil-body" />
              <!-- 当前试穿商品叠加 -->
              <view class="ar-overlay" :style="{ background: currentProduct.overlayBg }" />
            </view>
            <!-- AR 指示器 -->
            <view class="ar-indicator">
              <text class="ar-indicator-dot" />
              <text class="ar-indicator-text">AR 追踪中</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 功能按钮行 -->
      <view class="action-row">
        <view class="action-btn" @click="toggleCamera">
          <text class="action-icon luc-refresh-cw" />
          <text class="action-label">切换镜头</text>
        </view>
        <view class="action-btn capture-btn" @click="capturePhoto">
          <view class="capture-ring">
            <view class="capture-inner" />
          </view>
        </view>
        <view class="action-btn" @click="toggleFlash">
          <text class="action-icon luc" :class="$luc(flashOn ? 'zap' : 'flashlight')" />
          <text class="action-label">{{ flashOn ? '闪光开' : '闪光关' }}</text>
        </view>
      </view>
    </view>

    <!-- 商品选择面板 -->
    <view class="product-panel">
      <!-- 面板头部 -->
      <view class="panel-header">
        <text class="panel-title">选择商品试穿</text>
        <view class="panel-close" @click="goBack">
          <text class="panel-close-text luc-x" />
        </view>
      </view>

      <!-- 分类标签 -->
      <scroll-view scroll-x class="category-scroll">
        <view class="category-row">
          <view
            v-for="cat in categories"
            :key="cat"
            class="category-item"
            :class="{ active: activeCategory === cat }"
            @click="activeCategory = cat"
          >
            <text class="category-text">{{ cat }}</text>
          </view>
        </view>
      </scroll-view>

      <!-- 商品列表 -->
      <scroll-view scroll-x class="product-scroll">
        <view class="product-row">
          <view
            v-for="product in filteredProducts"
            :key="product.id"
            class="product-thumb"
            :class="{ selected: currentProduct.id === product.id }"
            @click="selectProduct(product)"
          >
            <view class="thumb-img" :style="{ background: product.thumbBg }" />
            <text class="thumb-name">{{ product.name }}</text>
            <view v-if="currentProduct.id === product.id" class="thumb-check">
              <text class="thumb-check-text luc-check" />
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 颜色选择 -->
      <view v-if="currentProduct.colors && currentProduct.colors.length" class="color-section">
        <text class="color-label">颜色</text>
        <view class="color-row">
          <view
            v-for="color in currentProduct.colors"
            :key="color.name"
            class="color-dot"
            :class="{ active: selectedColor === color.name }"
            :style="{ background: color.hex }"
            @click="selectedColor = color.name"
          >
            <view v-if="selectedColor === color.name" class="color-check">
              <text class="color-check-text luc-check" />
            </view>
          </view>
        </view>
      </view>

      <!-- 尺寸选择 -->
      <view v-if="currentProduct.sizes && currentProduct.sizes.length" class="size-section">
        <text class="size-label">尺寸</text>
        <view class="size-row">
          <view
            v-for="size in currentProduct.sizes"
            :key="size"
            class="size-chip"
            :class="{ active: selectedSize === size }"
            @click="selectedSize = size"
          >
            <text class="size-chip-text">{{ size }}</text>
          </view>
        </view>
      </view>

      <!-- 操作按钮 -->
      <view class="panel-actions">
        <view class="try-again-btn" @click="resetAr">
          <text class="try-again-text">重新试穿</text>
        </view>
        <view class="add-to-cart-btn" @click="addToCart">
          <text class="add-cart-text">
            <text class="luc luc-shopping-bag" />
            加入购物车 · {{ currentProduct.price }}
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.goodsArTryOn')


// AR 状态


const arActive = ref(false)
const flashOn = ref(false)
const selectedColor = ref('奶油白')
const selectedSize = ref('M')

// 当前选中商品
const currentProduct = ref({
  id: 1,
  name: '舒适透气胸背带',
  price: '$24.00',
  overlayBg: 'rgba(219, 201, 138, 0.4)',
  thumbBg: 'linear-gradient(135deg, #e9f9ee, #f2f2f7)',
  colors: [
    { name: '奶油白', hex: '#f7f0e5' },
    { name: '燕麦灰', hex: '#8e8e93' },
    { name: '焦糖棕', hex: '#a67c52' },
  ],
  sizes: ['XS', 'S', 'M', 'L', 'XL'],
})

// 分类
const categories = ref(['全部', '胸背带', '项圈', '服饰', '鞋套'])
const activeCategory = ref('全部')

// 商品列表
const products = ref([
  {
    id: 1,
    name: '舒适透气胸背带',
    category: '胸背带',
    price: '$24.00',
    overlayBg: 'rgba(219, 201, 138, 0.4)',
    thumbBg: 'linear-gradient(135deg, #e9f9ee, #f2f2f7)',
    colors: [
      { name: '奶油白', hex: '#f7f0e5' },
      { name: '燕麦灰', hex: '#8e8e93' },
      { name: '焦糖棕', hex: '#a67c52' },
    ],
    sizes: ['XS', 'S', 'M', 'L', 'XL'],
  },
  {
    id: 2,
    name: '轻奢皮质项圈',
    category: '项圈',
    price: '$18.00',
    overlayBg: 'rgba(166, 124, 82, 0.4)',
    thumbBg: 'linear-gradient(135deg, #fff4e5, #f7f7fa)',
    colors: [
      { name: '复古棕', hex: '#a67c52' },
      { name: '墨黑', hex: '#1d1d1f' },
      { name: '酒红', hex: '#8b2252' },
    ],
    sizes: ['S', 'M', 'L'],
  },
  {
    id: 3,
    name: '防风保暖外套',
    category: '服饰',
    price: '$38.00',
    overlayBg: 'rgba(0, 122, 255, 0.3)',
    thumbBg: 'linear-gradient(135deg, #e8f2ff, #f2f2f7)',
    colors: [
      { name: '雾蓝', hex: '#007aff' },
      { name: '浅粉', hex: '#ff9a9e' },
      { name: '薄荷绿', hex: '#84fab0' },
    ],
    sizes: ['S', 'M', 'L', 'XL'],
  },
  {
    id: 4,
    name: '户外防护鞋套',
    category: '鞋套',
    price: '$15.00',
    overlayBg: 'rgba(52, 199, 89, 0.3)',
    thumbBg: 'linear-gradient(135deg, #e9f9ee, #f2f2f7)',
    colors: [
      { name: '活力橙', hex: '#ff9500' },
      { name: '沉稳灰', hex: '#8e8e93' },
    ],
    sizes: ['S', 'M', 'L'],
  },
])

// 按分类筛选
const filteredProducts = computed(() => {
  if (activeCategory.value === '全部') return products.value
  return products.value.filter((p) => p.category === activeCategory.value)
})

// 返回
const goBack = () => {
  uni.navigateBack()
}

// 启动 AR
const startAr = () => {
  arActive.value = true
}

// 切换摄像头
const toggleCamera = () => {
  uni.showToast({ title: '切换摄像头', icon: 'none' })
}

// 闪光灯
const toggleFlash = () => {
  flashOn.value = !flashOn.value
}

// 拍照
const capturePhoto = () => {
  uni.showToast({ title: '已保存到相册', icon: 'success' })
}

// 选择商品
const selectProduct = (product) => {
  currentProduct.value = product
  selectedColor.value = product.colors?.[0]?.name || ''
  selectedSize.value = 'M'
  if (!arActive.value) arActive.value = true
}

// 重新试穿
const resetAr = () => {
  arActive.value = false
}

// 加入购物车
const addToCart = () => {
  uni.showToast({ title: '已加入购物车', icon: 'success' })
}
</script>

<style lang="scss" scoped>
.ar-tryon-page {
  min-height: 100vh;
  background: #1d1d1f;
  display: flex;
  flex-direction: column;
}

// 导航栏
.nav-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
}
.nav-share {
  font-size: 32rpx;
}

// 主视图
.main-view {
  flex: 1;
  position: relative;
}

// AR 预览
.ar-preview {
  width: 100%;
  height: 100%;
}
.ar-scene {
  width: 100%;
  height: 100%;
  min-height: 800rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: linear-gradient(180deg, #2c2c2e, #1d1d1f);
}

// 站位引导
.ar-guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}
.guide-circle {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  border: 4rpx dashed rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.05);
    opacity: 1;
  }
}
.guide-icon {
  font-size: 64rpx;
}
.guide-text {
  font-size: 28rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}

// AR 活跃视图
.ar-active-view {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.silhouette {
  width: 240rpx;
  height: 480rpx;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sil-body {
  width: 200rpx;
  height: 440rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  border-radius: 100rpx 100rpx 60rpx 60rpx;
  position: relative;
}
.ar-overlay {
  position: absolute;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 180rpx;
  height: 80rpx;
  border-radius: 20rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.5);
}
.ar-indicator {
  position: absolute;
  top: 108rpx;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.5);
}
.ar-indicator-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--color-success);
}
.ar-indicator-text {
  font-size: 22rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.8);
}

// 功能按钮
.action-row {
  position: absolute;
  bottom: 40rpx;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 56rpx;
}
.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.action-icon {
  font-size: 40rpx;
}
.action-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.7);
}
.capture-btn {
  margin-top: -16rpx;
}
.capture-ring {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  border: 6rpx solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.capture-inner {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #fff;
}

// 商品选择面板
.product-panel {
  background: var(--color-card);
  border-radius: 32rpx 32rpx 0 0;
  padding: 28rpx 28rpx 0;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}

// 面板头部
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.panel-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}
.panel-close {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;
}
.panel-close-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

// 分类标签
.category-scroll {
  white-space: nowrap;
}
.category-row {
  display: inline-flex;
  gap: 8rpx;
  margin-bottom: 20rpx;
}
.category-item {
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: var(--color-background);
}
.category-item.active {
  background: var(--color-primary);
}
.category-text {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--color-text-secondary);
}
.category-item.active .category-text {
  font-weight: 600;
  color: #fff;
}

// 商品缩略图
.product-scroll {
  white-space: nowrap;
}
.product-row {
  display: inline-flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.product-thumb {
  width: 128rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  position: relative;
  padding: 12rpx 8rpx;
  border-radius: 16rpx;
  border: 2rpx solid transparent;
}
.product-thumb.selected {
  border-color: var(--color-primary);
  background: #e8f2ff;
}
.thumb-img {
  width: 88rpx;
  height: 88rpx;
  border-radius: 16rpx;
}
.thumb-name {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  text-align: center;
}
.thumb-check {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb-check-text {
  font-size: 18rpx;
  color: #fff;
  font-weight: 700;
}

// 颜色选择
.color-section {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.color-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--color-text-secondary);
}
.color-row {
  display: flex;
  gap: 12rpx;
}
.color-dot {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 3rpx solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.color-dot.active {
  border-color: var(--color-primary);
  border-width: 4rpx;
}
.color-check-text {
  font-size: 20rpx;
  color: #fff;
  font-weight: 700;
}

// 尺寸选择
.size-section {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.size-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--color-text-secondary);
}
.size-row {
  display: flex;
  gap: 8rpx;
}
.size-chip {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  border: 2rpx solid var(--color-border);
  background: var(--color-card);
}
.size-chip.active {
  border-color: var(--color-primary);
  background: #e8f2ff;
}
.size-chip-text {
  font-size: 24rpx;
  color: var(--color-text);
}
.size-chip.active .size-chip-text {
  font-weight: 600;
  color: var(--color-primary);
}

// 操作按钮
.panel-actions {
  display: flex;
  gap: 16rpx;
  padding-top: 20rpx;
  padding-bottom: 20rpx;
  border-top: 1rpx solid var(--color-border);
}
.try-again-btn {
  height: 88rpx;
  padding: 0 32rpx;
  border-radius: 24rpx;
  border: 2rpx solid var(--color-border);
  background: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.try-again-text {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--color-text);
}
.add-to-cart-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 24rpx;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.add-cart-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #fff;
}
</style>
