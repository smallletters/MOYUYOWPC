<template>
  <view class="product-subscribe-list">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">订阅商品</text>
      <view class="header-spacer" />
    </view>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="search-icon"><text class="luc luc-search"></text></text>
        <input
          class="search-input"
          placeholder="搜索可订阅商品"
          placeholder-class="search-placeholder"
          :value="searchKeyword"
          @input="onSearchInput"
        >
      </view>
    </view>

    <!-- 分类标签 -->
    <scroll-view class="category-scroll" scroll-x>
      <view class="category-list">
        <view
          v-for="cat in categories"
          :key="cat.key"
          class="category-tag"
          :class="{ active: selectedCategory === cat.key }"
          @click="selectedCategory = cat.key"
        >
          <text
            class="category-text"
            :class="{ 'category-text-active': selectedCategory === cat.key }"
          >
            {{ cat.name }}
          </text>
        </view>
      </view>
    </scroll-view>

    <scroll-view class="content" scroll-y>
      <!-- 订阅优惠横幅 -->
      <view class="promo-banner">
        <view class="promo-left">
          <text class="promo-title">Subscribe & Save</text>
          <text class="promo-desc">订阅即享 10% off，可随时取消</text>
        </view>
        <view class="promo-badge">
          <text class="promo-badge-text">10%</text>
          <text class="promo-badge-sub">OFF</text>
        </view>
      </view>

      <!-- 商品列表 -->
      <view class="product-list">
        <view v-for="item in filteredProducts" :key="item.id" class="product-card">
          <image :src="item.image" class="product-image" mode="aspectFill" />
          <view class="product-info">
            <text class="product-name">{{ item.name }}</text>
            <text class="product-desc">{{ item.desc }}</text>
            <!-- 订阅标签 -->
            <view class="sub-tags">
              <text class="sub-tag">可订阅</text>
              <text class="sub-save-tag">省{{ item.savePercent }}%</text>
            </view>
            <!-- 价格行 -->
            <view class="price-row">
              <view class="price-left">
                <text class="price-sub">¥{{ item.subPrice }}</text>
                <text class="price-original">¥{{ item.price }}</text>
              </view>
              <view class="subscribe-btn-small" @click="goSubscribe(item)">
                <text class="subscribe-btn-text">订阅</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-if="filteredProducts.length === 0" class="empty-state">
        <text class="empty-icon"><text class="luc luc-package"></text></text>
        <text class="empty-text">暂无匹配的可订阅商品</text>
      </view>

      <!-- 底部提示 -->
      <view class="tip-box">
        <text class="tip-icon"><text class="luc luc-lightbulb"></text></text>
        <text class="tip-text">更多商品正在陆续开放订阅，敬请期待！</text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

// 搜索关键词
const searchKeyword = ref('')

// 选中的分类
const selectedCategory = ref('all')

// 分类列表
const categories = [
  { key: 'all', name: '全部' },
  { key: 'bath', name: '洗浴护理' },
  { key: 'food', name: '食品零食' },
  { key: 'health', name: '健康保健' },
  { key: 'toy', name: '玩具用品' },
  { key: 'clean', name: '清洁除味' },
]

// 商品列表 mock
const products = ref([
  {
    id: 1,
    name: 'MOYUYO 温和沐浴露',
    desc: '宠物专用 500ml',
    price: '28.00',
    subPrice: '25.20',
    savePercent: 10,
    category: 'bath',
    image: '/static/images/product-shampoo.png',
  },
  {
    id: 2,
    name: 'MOYUYO 宠物零食混合装',
    desc: '混合口味 200g',
    price: '20.00',
    subPrice: '18.00',
    savePercent: 10,
    category: 'food',
    image: '/static/images/product-treats.png',
  },
  {
    id: 3,
    name: 'MOYUYO 营养膏',
    desc: '全年龄段 120g',
    price: '45.00',
    subPrice: '40.50',
    savePercent: 10,
    category: 'health',
    image: '/static/images/product-nutrition.png',
  },
  {
    id: 4,
    name: 'MOYUYO 互动玩具球',
    desc: '弹力球 3 只装',
    price: '15.00',
    subPrice: '13.50',
    savePercent: 10,
    category: 'toy',
    image: '/static/images/product-toy.png',
  },
  {
    id: 5,
    name: 'MOYUYO 环境清洁喷雾',
    desc: '除味去污 300ml',
    price: '32.00',
    subPrice: '28.80',
    savePercent: 10,
    category: 'clean',
    image: '/static/images/product-clean.png',
  },
  {
    id: 6,
    name: 'MOYUYO 高端猫粮',
    desc: '鸡肉配方 2kg',
    price: '189.00',
    subPrice: '170.10',
    savePercent: 10,
    category: 'food',
    image: '/static/images/product-catfood.png',
  },
])

// 过滤后的商品列表
const filteredProducts = computed(() => {
  let list = products.value
  // 按分类过滤
  if (selectedCategory.value !== 'all') {
    list = list.filter((p) => p.category === selectedCategory.value)
  }
  // 按搜索关键词过滤
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(
      (p) => p.name.toLowerCase().includes(kw) || p.desc.toLowerCase().includes(kw),
    )
  }
  return list
})

// 返回
const goBack = () => {
  uni.navigateBack()
}

// 搜索输入
const onSearchInput = (e) => {
  searchKeyword.value = e.detail.value
}

// 跳转订阅
const goSubscribe = (item) => {
  uni.navigateTo({ url: `/pages/goods/product-subscribe?id=${item.id}` })
}
</script>

<style lang="scss" scoped>
.product-subscribe-list {
  min-height: 100vh;
  background: var(--color-background);
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  border-bottom: 1rpx solid var(--color-border);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}

.header-spacer {
  width: 72rpx;
}

/* 搜索栏 */
.search-bar {
  padding: 16rpx 32rpx;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
}

.search-icon {
  font-size: 28rpx;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text);
  background: transparent;
}

.search-placeholder {
  color: var(--color-text-secondary);
}

/* 分类标签 */
.category-scroll {
  white-space: nowrap;
  padding: 0 32rpx;
}

.category-list {
  display: inline-flex;
  gap: 16rpx;
}

.category-tag {
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
}

.category-tag.active {
  background: var(--color-primary);
}

.category-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.category-text-active {
  color: #ffffff;
}

/* 内容区域 */
.content {
  height: calc(100vh - 88rpx - 120rpx - 80rpx);
  padding: 0 32rpx;
}

.bottom-spacer {
  height: 48rpx;
}

/* 优惠横幅 */
.promo-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 28rpx 32rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, var(--color-primary), #c4a85a);
}

.promo-left {
  display: flex;
  flex-direction: column;
}

.promo-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #ffffff;
}

.promo-desc {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4rpx;
}

.promo-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.2);
}

.promo-badge-text {
  font-size: 36rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1;
}

.promo-badge-sub {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.8);
}

/* 商品列表 */
.product-list {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.product-card {
  display: flex;
  gap: 24rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.product-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.product-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

.sub-tags {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}

.sub-tag {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary);
  font-size: 20rpx;
  font-weight: 600;
}

.sub-save-tag {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: rgba(171, 185, 173, 0.2);
  color: var(--color-success);
  font-size: 20rpx;
  font-weight: 600;
}

.price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.price-left {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.price-sub {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-primary);
}

.price-original {
  font-size: 24rpx;
  text-decoration: line-through;
  color: var(--color-text-secondary);
}

.subscribe-btn-small {
  padding: 12rpx 32rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
}

.subscribe-btn-text {
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  margin-top: 120rpx;
}

.empty-icon {
  font-size: 80rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}

/* 底部提示 */
.tip-box {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 40rpx;
  margin-bottom: 32rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: rgba(0, 0, 0, 0.04);
}

.tip-icon {
  font-size: 28rpx;
  flex-shrink: 0;
}

.tip-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}
</style>
