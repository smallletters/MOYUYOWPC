<template>
  <view class="bundle-deal">
    <!-- 顶部导航栏 -->
    <view class="nav-header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon">‹</text>
      </view>
      <text class="nav-title">套餐优惠</text>
      <view class="nav-right">
        <text class="share-icon">↗</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 套餐类型 Tab -->
      <view class="tab-bar">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          :style="{
            color: activeTab === tab.key ? 'var(--primary)' : 'var(--text-400)',
            borderBottom:
              activeTab === tab.key ? '2rpx solid var(--primary)' : '2rpx solid transparent',
          }"
          @tap="onTabChange(tab.key)"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>

      <!-- 推荐 Tab -->
      <view v-show="activeTab === 'recommend'" class="tab-content">
        <!-- 推荐套餐大卡片 -->
        <view v-for="bundle in recommendBundles" :key="bundle.id" class="bundle-card featured">
          <view class="bundle-tags">
            <view class="tag-hot">{{ bundle.tag }}</view>
            <text class="tag-series">{{ bundle.series }}</text>
          </view>
          <view class="bundle-header">
            <text class="bundle-name">{{ bundle.name }}</text>
            <text class="bundle-desc">{{ bundle.desc }}</text>
          </view>
          <view class="product-thumbs">
            <view
              v-for="(thumb, idx) in bundle.thumbs"
              :key="idx"
              class="thumb-item"
              :style="{ background: thumb.color }"
            />
            <text class="thumb-more">›</text>
          </view>
          <view class="product-list">
            <view v-for="item in bundle.items" :key="item.name" class="product-row">
              <text class="product-name">{{ item.name }}</text>
              <text class="product-price">${{ item.price }}</text>
            </view>
          </view>
          <view class="divider-dashed" />
          <view class="price-row">
            <view class="price-left">
              <text class="original-price">原价 ${{ bundle.originalPrice }}</text>
              <text class="deal-price">${{ bundle.dealPrice }}</text>
            </view>
            <view class="savings-badge">立省 ${{ bundle.savings }}</view>
          </view>
          <view class="add-btn" @tap="handleAddBundle(bundle)">
            <text>一键加购 - ${{ bundle.dealPrice }}</text>
          </view>
        </view>

        <view class="section-title">
          <text>热门套餐</text>
        </view>

        <view v-for="bundle in hotBundles" :key="bundle.id" class="bundle-card compact">
          <view class="compact-inner">
            <view class="compact-thumb" :style="{ background: bundle.thumbColor }" />
            <view class="compact-info">
              <view class="compact-top">
                <text class="compact-name">{{ bundle.name }}</text>
                <view class="tag-save">省 ${{ bundle.savings }}</view>
              </view>
              <text class="compact-desc">{{ bundle.desc }}</text>
              <view class="compact-price-row">
                <view class="price-left">
                  <text class="original-price-sm">${{ bundle.originalPrice }}</text>
                  <text class="deal-price-sm">${{ bundle.dealPrice }}</text>
                </view>
                <view class="add-sm-btn" @tap="handleAddBundle(bundle)">加购</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 固定套餐 Tab -->
      <view v-show="activeTab === 'fixed'" class="tab-content">
        <text class="tab-hint">精选固定组合，套餐价低于单买总价，直接加购即可。</text>
        <view v-for="bundle in fixedBundles" :key="bundle.id" class="bundle-card compact">
          <view class="compact-inner">
            <view class="compact-thumb fixed-thumb" :style="{ background: bundle.thumbColor }" />
            <view class="compact-info">
              <view class="compact-top">
                <text class="compact-name">{{ bundle.name }}</text>
                <view class="tag-save">省 ${{ bundle.savings }}</view>
              </view>
              <text class="compact-desc">{{ bundle.desc }}</text>
              <view class="thumb-row">
                <view
                  v-for="(t, i) in bundle.thumbs"
                  :key="i"
                  class="mini-thumb"
                  :style="{ background: t }"
                />
              </view>
              <view class="compact-price-row">
                <view class="price-left">
                  <text class="original-price-sm">${{ bundle.originalPrice }}</text>
                  <text class="deal-price-sm">${{ bundle.dealPrice }}</text>
                </view>
                <view class="add-sm-btn" @tap="handleAddBundle(bundle)">加购</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 可选套餐 Tab -->
      <view v-show="activeTab === 'optional'" class="tab-content">
        <view v-for="bundle in optionalBundles" :key="bundle.id" class="bundle-card">
          <view class="bundle-tags">
            <view class="tag-optional">可选套餐</view>
            <text class="tag-series">{{ bundle.series }}</text>
          </view>
          <text class="bundle-name">{{ bundle.name }}</text>
          <text class="bundle-desc">{{ bundle.desc }}</text>

          <view class="main-product-item">
            <view class="main-product-thumb" :style="{ background: bundle.mainProduct.color }" />
            <view class="main-product-info">
              <view class="main-product-tag">主商品</view>
              <text class="main-product-name">{{ bundle.mainProduct.name }}</text>
              <text class="main-product-price">${{ bundle.mainProduct.price }}</text>
            </view>
            <text class="check-icon">✓</text>
          </view>

          <view class="accessory-section">
            <view class="accessory-header">
              <text class="accessory-title">选择配件</text>
              <view class="accessory-limit">三选一</view>
            </view>
            <view class="accessory-grid">
              <view
                v-for="acc in bundle.accessories"
                :key="acc.id"
                class="accessory-card"
                :class="{ selected: selectedAccessory === acc.id }"
                :style="{
                  borderColor: selectedAccessory === acc.id ? 'var(--primary)' : 'var(--border)',
                  borderWidth: selectedAccessory === acc.id ? '4rpx' : '2rpx',
                }"
                @tap="selectedAccessory = acc.id"
              >
                <view class="acc-thumb" :style="{ background: acc.color }" />
                <text class="acc-name">{{ acc.name }}</text>
                <text class="acc-price">${{ acc.price }}</text>
              </view>
            </view>
          </view>

          <view class="divider-dashed" />
          <view class="price-row">
            <view class="price-left">
              <text class="original-price">原价 ${{ bundle.originalPrice }}</text>
              <text class="deal-price">${{ bundle.dealPrice }}</text>
              <view class="tag-save-sm">立省 ${{ bundle.savings }}</view>
            </view>
            <view class="add-optional-btn" @tap="handleAddBundle(bundle)">
              <text>加购</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 自定义搭配 Tab -->
      <view v-show="activeTab === 'custom'" class="tab-content">
        <view class="custom-banner">
          <text class="custom-banner-icon">✦</text>
          <text class="custom-banner-title">自定义搭配 85 折</text>
          <text class="custom-banner-desc">
            从下方商品池中自选 3 件，即可享受 85 折优惠。已选
            <text class="highlight">{{ customSelected.length }}</text>
            /3 件
          </text>
        </view>

        <view class="custom-pool">
          <view
            v-for="item in customPool"
            :key="item.id"
            class="custom-item"
            :class="{ selected: customSelected.includes(item.id) }"
            :style="{
              borderColor: customSelected.includes(item.id) ? 'var(--primary)' : 'var(--border)',
              borderWidth: customSelected.includes(item.id) ? '4rpx' : '2rpx',
            }"
            @tap="toggleCustomItem(item.id)"
          >
            <view class="custom-item-thumb" :style="{ background: item.color }" />
            <text class="custom-item-name">{{ item.name }}</text>
            <text class="custom-item-price">${{ item.price }}</text>
          </view>
        </view>

        <view class="custom-footer">
          <view class="custom-summary">
            <text class="custom-summary-label">已选 {{ customSelected.length }} 件原价</text>
            <text class="custom-original">${{ customOriginalTotal }}</text>
            <text class="custom-discount-label">85折价</text>
            <text class="custom-discount">${{ customDiscountTotal }}</text>
          </view>
          <view
            class="custom-add-btn"
            :class="{ disabled: customSelected.length !== 3 }"
            @tap="handleCustomAdd"
          >
            <text v-if="customSelected.length === 3">一键加购 - ${{ customDiscountTotal }}</text>
            <text v-else>请选择 3 件商品</text>
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script>
import { bundleDealApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'recommend',
      tabs: [
        { key: 'recommend', label: '推荐' },
        { key: 'fixed', label: '固定套餐' },
        { key: 'optional', label: '可选套餐' },
        { key: 'custom', label: '自定义搭配' },
      ],
      selectedAccessory: 'cushion',
      customSelected: [],
      recommendBundles: [],
      hotBundles: [],
      fixedBundles: [],
      optionalBundles: [],
      customPool: [],
    }
  },

  computed: {
    customOriginalTotal() {
      let total = 0
      this.customSelected.forEach((id) => {
        const item = this.customPool.find((p) => p.id === id)
        if (item) total += parseFloat(item.price)
      })
      return total.toFixed(2)
    },
    customDiscountTotal() {
      if (this.customSelected.length === 3) {
        return (parseFloat(this.customOriginalTotal) * 0.85).toFixed(2)
      }
      return '0.00'
    },
  },

  onLoad() {
    this.loadBundleDeals()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onTabChange(key) {
      this.activeTab = key
    },

    async loadBundleDeals() {
      try {
        const res = await bundleDealApi.getBundleDeals()
        const data = res.data || {}
        this.recommendBundles = data.recommendBundles || []
        this.hotBundles = data.hotBundles || []
        this.fixedBundles = data.fixedBundles || []
        this.optionalBundles = data.optionalBundles || []
        this.customPool = data.customPool || []
      } catch (err) {
        uni.showToast({ title: '加载套餐信息失败', icon: 'none' })
      }
    },

    handleAddBundle(bundle) {
      uni.showToast({ title: `已添加「${bundle.name}」到购物车`, icon: 'success' })
    },

    toggleCustomItem(id) {
      const idx = this.customSelected.indexOf(id)
      if (idx > -1) {
        this.customSelected.splice(idx, 1)
      } else if (this.customSelected.length < 3) {
        this.customSelected.push(id)
      } else {
        uni.showToast({ title: '最多选择 3 件商品', icon: 'none' })
      }
    },

    handleCustomAdd() {
      if (this.customSelected.length === 3) {
        uni.showToast({ title: '自定义套餐已加入购物车', icon: 'success' })
      }
    },
  },
}
</script>

<style scoped lang="scss">
/* MOYUYO 品牌变量映射（替换原 Apple 设计系统 token） */
.bundle-deal {
  /* 颜色映射 */
  --primary: var(--color-primary);
  --primary-foreground: #2e2b29;
  --foreground: var(--color-text);
  --background: var(--color-background);
  --background-50: var(--color-surface);
  --background-100: #faf8f5;
  --background-200: #f0ede8;
  --background-700: #3a3530;
  --background-800: #1c1a16;
  --card: var(--color-surface);
  --card-foreground: var(--color-text);
  --border: var(--color-divider);
  --input: #c0bcb4;
  --muted-foreground: var(--color-text-tertiary);
  --text-400: var(--color-text-tertiary);
  --text-500: #7a746c;
  --text-600: var(--color-text-secondary);
  --icon-muted: var(--color-text-tertiary);
  --destructive: var(--color-danger);
  --destructive-foreground: #ffffff;
  --success: var(--color-success);
  --brand-50: #f8f3e6;
  --brand-100: #ede5c2;
  --shadow-sm: var(--shadow-sm);
  --shadow-md: var(--shadow-md);
  --shadow-lg: var(--shadow-lg);

  min-height: 100vh;
  background: var(--color-background);
}
.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 30rpx;
  background: var(--background-800);
  position: sticky;
  top: 0;
  z-index: 30;
}
.nav-back,
.nav-right {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--background-700);
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon,
.share-icon {
  color: var(--background-50);
  font-size: 36rpx;
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--background-50);
}
.tab-bar {
  display: flex;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 2rpx solid var(--border);
  position: sticky;
  top: 88rpx;
  z-index: 20;
}
.tab-btn {
  padding: 24rpx 30rpx;
  font-size: 28rpx;
  font-weight: 600;
  white-space: nowrap;
}
.tab-content {
  padding: 30rpx;
}
.tab-hint {
  font-size: 24rpx;
  color: var(--text-400);
  margin-bottom: 24rpx;
  display: block;
}
.bundle-card {
  background: var(--card);
  border: 2rpx solid var(--border);
  border-radius: 24rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
}
.bundle-card.featured {
  box-shadow: var(--shadow-md);
}
.bundle-tags {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 30rpx 30rpx 0;
}
.tag-hot,
.tag-save-sm {
  background: var(--destructive);
  color: var(--destructive-foreground);
  font-size: 22rpx;
  font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
}
.tag-series {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.bundle-header {
  padding: 16rpx 30rpx 0;
}
.bundle-name {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--foreground);
}
.bundle-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  margin-top: 8rpx;
  display: block;
}
.product-thumbs {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 30rpx 30rpx 0;
}
.thumb-item {
  width: 112rpx;
  height: 112rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
}
.thumb-more {
  color: var(--icon-muted);
  font-size: 32rpx;
}
.product-list {
  padding: 30rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.product-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.product-name {
  font-size: 28rpx;
  color: var(--foreground);
}
.product-price {
  font-size: 28rpx;
  color: var(--muted-foreground);
}
.divider-dashed {
  margin: 0 30rpx;
  border-top: 2rpx dashed var(--border);
}
.price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 24rpx 30rpx;
}
.price-left {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  flex-wrap: wrap;
}
.original-price {
  font-size: 24rpx;
  text-decoration: line-through;
  color: var(--muted-foreground);
}
.deal-price {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--destructive);
}
.savings-badge {
  background: var(--destructive);
  color: var(--destructive-foreground);
  font-size: 22rpx;
  font-weight: 700;
  padding: 8rpx 20rpx;
  border-radius: 16rpx;
}
.add-btn {
  margin: 0 30rpx 30rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}
.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--foreground);
  margin: 32rpx 0 24rpx;
}
.bundle-card.compact {
  box-shadow: var(--shadow-sm);
}
.compact-inner {
  display: flex;
  gap: 24rpx;
  padding: 30rpx;
}
.compact-thumb {
  width: 104rpx;
  height: 104rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
}
.fixed-thumb {
  width: 128rpx;
  height: 128rpx;
}
.compact-info {
  flex: 1;
  min-width: 0;
}
.compact-top {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.compact-name {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-save {
  background: var(--destructive);
  color: var(--destructive-foreground);
  font-size: 20rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
}
.compact-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 8rpx;
}
.compact-price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}
.original-price-sm {
  font-size: 24rpx;
  text-decoration: line-through;
  color: var(--muted-foreground);
}
.deal-price-sm {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--destructive);
}
.add-sm-btn {
  height: 64rpx;
  padding: 0 32rpx;
  border-radius: 16rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb-row {
  display: flex;
  gap: 8rpx;
  margin: 16rpx 0;
}
.mini-thumb {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
}
.main-product-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  margin: 24rpx 30rpx;
  background: var(--background-100);
  border-radius: 24rpx;
}
.main-product-thumb {
  width: 112rpx;
  height: 112rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
}
.main-product-info {
  flex: 1;
}
.main-product-tag {
  font-size: 22rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: var(--background-200);
  color: var(--text-600);
  display: inline-block;
}
.main-product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  margin-top: 4rpx;
}
.main-product-price {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.check-icon {
  color: var(--success);
  font-size: 40rpx;
  flex-shrink: 0;
}
.accessory-section {
  padding: 0 30rpx;
}
.accessory-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.accessory-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.accessory-limit {
  font-size: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: var(--background-200);
  color: var(--text-500);
}
.accessory-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
}
.accessory-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: var(--background-50);
  border-style: solid;
}
.acc-thumb {
  width: 112rpx;
  height: 112rpx;
  border-radius: 24rpx;
}
.acc-name {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--foreground);
  text-align: center;
}
.acc-price {
  font-size: 22rpx;
  color: var(--muted-foreground);
}
.add-optional-btn {
  height: 80rpx;
  padding: 0 40rpx;
  border-radius: 24rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.custom-banner {
  background: linear-gradient(135deg, var(--brand-50), var(--background-50));
  border: 2rpx solid var(--brand-100);
  border-radius: 24rpx;
  padding: 30rpx;
  margin-bottom: 30rpx;
}
.custom-banner-icon {
  color: var(--primary);
  font-size: 40rpx;
}
.custom-banner-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--foreground);
  display: block;
  margin-top: 8rpx;
}
.custom-banner-desc {
  font-size: 24rpx;
  color: var(--text-600);
  display: block;
  margin-top: 8rpx;
}
.highlight {
  color: var(--primary);
  font-weight: 700;
}
.custom-pool {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
}
.custom-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: var(--card);
  border-style: solid;
}
.custom-item-thumb {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 16rpx;
}
.custom-item-name {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--foreground);
  text-align: center;
}
.custom-item-price {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--text-600);
}
.custom-footer {
  position: sticky;
  bottom: 0;
  border-radius: 24rpx;
  padding: 30rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  box-shadow: var(--shadow-lg);
  margin-top: 30rpx;
}
.custom-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
  flex-wrap: wrap;
  gap: 8rpx;
}
.custom-summary-label {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.custom-original {
  font-size: 28rpx;
  text-decoration: line-through;
  color: var(--muted-foreground);
}
.custom-discount-label {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.custom-discount {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--destructive);
}
.custom-add-btn {
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.custom-add-btn.disabled {
  opacity: 0.5;
}
.bottom-spacer {
  height: 40rpx;
}
.tag-optional {
  font-size: 22rpx;
  font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: var(--brand-50);
  color: var(--primary);
}
</style>
