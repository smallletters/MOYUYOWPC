<template>
  <view class="frequent-purchase">
    <!-- 顶部导航栏 -->
    <view class="nav-header">
      <view class="nav-inner">
        <view class="status-bar">
          <text class="status-time">9:41</text>
          <view class="status-icons">
            <text class="luc luc-signal" />
            <text class="luc luc-wifi" />
            <text class="luc luc-battery-full" />
          </view>
        </view>
        <view class="nav-row">
          <view class="nav-back" @tap="goBack">
            <text class="luc luc-arrow-left" />
          </view>
          <text class="nav-title">常购清单</text>
        </view>
      </view>
    </view>

    <!-- 排序切换 Tab -->
    <view class="tab-bar">
      <view class="tab-inner">
        <view class="tab-item" :class="{ active: currentTab === 'freq' }" @tap="switchTab('freq')">
          <text class="luc luc-trending-up" />
          <text>购买频次</text>
        </view>
        <view
          class="tab-item"
          :class="{ active: currentTab === 'recent' }"
          @tap="switchTab('recent')"
        >
          <text class="luc luc-clock" />
          <text>最近购买</text>
        </view>
      </view>
    </view>

    <!-- 商品列表 -->
    <scroll-view scroll-y class="product-list">
      <view v-for="(item, i) in productList" :key="i" class="product-card">
        <view class="product-thumb" :style="{ background: item.bg }" />
        <view class="product-info">
          <text class="product-name">{{ item.name }}</text>
          <text class="product-spec">{{ item.spec }}</text>
          <text class="product-price">${{ item.price }}</text>
          <view class="product-meta">
            <text class="meta-item">已购 {{ item.bought }} 次</text>
            <text class="meta-divider" />
            <text class="meta-item">上次购买：{{ item.lastBuy }}</text>
          </view>
        </view>
        <view class="add-btn" :class="{ selected: item.selected }" @tap="toggleSelect(i)">
          <text class="luc luc-plus" />
        </view>
      </view>
    </scroll-view>

    <!-- 底部固定操作栏 -->
    <view class="bottom-bar">
      <view class="bar-inner">
        <text class="selected-text" :class="{ error: showError }">
          {{ showError ? '请先选择商品' : `已选 ${selectedCount} 件` }}
        </text>
        <view class="add-all-btn" @tap="handleAddAll">
          <text class="luc luc-shopping-cart" />
          <text>一键加入购物车</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      currentTab: 'freq',
      showError: false,
      productList: [
        {
          name: '皇家猫粮 室内成猫配方',
          spec: '2kg / 鸡肉口味',
          price: '42.99',
          bought: 12,
          lastBuy: '2026-06-28',
          selected: false,
          bg: 'linear-gradient(135deg, var(--brand-50) 0%, var(--brand-200) 100%)',
        },
        {
          name: '天然宠物湿粮 金枪鱼拌饭',
          spec: '85g x 12 罐 / 整箱装',
          price: '36.50',
          bought: 10,
          lastBuy: '2026-06-25',
          selected: false,
          bg: 'linear-gradient(135deg, var(--state-success-surface) 0%, var(--brand-100) 100%)',
        },
        {
          name: '宠物猫砂 豆腐混合砂',
          spec: '6.5L / 原味除臭',
          price: '18.99',
          bought: 9,
          lastBuy: '2026-06-20',
          selected: false,
          bg: 'linear-gradient(135deg, var(--background-100) 0%, var(--brand-50) 100%)',
        },
        {
          name: '高端宠物洗护套装',
          spec: '500ml / 温和低敏配方',
          price: '28.50',
          bought: 8,
          lastBuy: '2026-06-15',
          selected: false,
          bg: 'linear-gradient(135deg, var(--brand-100) 0%, var(--brand-300) 100%)',
        },
        {
          name: '猫抓板 瓦楞纸组合',
          spec: '大号 / 多层可替换',
          price: '15.99',
          bought: 7,
          lastBuy: '2026-06-10',
          selected: false,
          bg: 'linear-gradient(135deg, var(--background-200) 0%, var(--brand-50) 100%)',
        },
        {
          name: '宠物营养膏 维生素补充',
          spec: '120g / 离乳期适用',
          price: '12.99',
          bought: 6,
          lastBuy: '2026-06-05',
          selected: false,
          bg: 'linear-gradient(135deg, var(--brand-50) 0%, var(--background-100) 100%)',
        },
        {
          name: '舒适胸背带 透气款',
          spec: 'M码 / 黑色',
          price: '24.99',
          bought: 5,
          lastBuy: '2026-05-30',
          selected: false,
          bg: 'linear-gradient(135deg, var(--brand-200) 0%, var(--brand-50) 100%)',
        },
        {
          name: '互动益智玩具 漏食球',
          spec: '直径7cm / 食品级硅胶',
          price: '9.99',
          bought: 4,
          lastBuy: '2026-05-22',
          selected: false,
          bg: 'linear-gradient(135deg, var(--background-100) 0%, var(--brand-100) 100%)',
        },
        {
          name: '宠物自动饮水机',
          spec: '2L / 静音循环过滤',
          price: '39.99',
          bought: 3,
          lastBuy: '2026-05-10',
          selected: false,
          bg: 'linear-gradient(135deg, var(--brand-50) 0%, var(--background-200) 100%)',
        },
        {
          name: '宠物航空箱 便携出行',
          spec: 'M码 / 透气网格',
          price: '55.00',
          bought: 2,
          lastBuy: '2026-04-18',
          selected: false,
          bg: 'linear-gradient(135deg, var(--brand-100) 0%, var(--background-100) 100%)',
        },
      ],
    }
  },
  computed: {
    selectedCount() {
      return this.productList.filter((item) => item.selected).length
    },
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    switchTab(tab) {
      this.currentTab = tab
    },
    toggleSelect(i) {
      this.productList[i].selected = !this.productList[i].selected
      this.showError = false
    },
    handleAddAll() {
      if (this.selectedCount === 0) {
        this.showError = true
        setTimeout(() => {
          this.showError = false
        }, 1500)
        return
      }
      uni.showToast({ title: `成功加入 ${this.selectedCount} 件商品`, icon: 'none' })
      this.productList.forEach((item) => {
        item.selected = false
      })
    },
  },
}
</script>

<style scoped>
.frequent-purchase {
  min-height: 100vh;
  background: var(--background);
}
.nav-header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--background-800);
}
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44rpx;
  padding: 0 32rpx;
}
.status-time {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-50);
}
.status-icons {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.status-icons .iconfont {
  color: var(--text-50);
  font-size: 24rpx;
}
.nav-row {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 32rpx;
}
.nav-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  color: var(--text-50);
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-50);
  margin-left: -72rpx;
}
.tab-bar {
  padding: 24rpx 32rpx 16rpx;
  background: var(--background);
}
.tab-inner {
  display: flex;
  gap: 0;
  border-radius: 16rpx;
  padding: 6rpx;
  background: var(--secondary);
}
.tab-item {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  border-radius: 14rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--muted-foreground);
  transition: all 0.2s ease;
}
.tab-item.active {
  background: var(--primary);
  color: var(--primary-foreground);
  font-weight: 600;
}
.product-list {
  padding: 16rpx 32rpx;
  padding-bottom: 160rpx;
}
.product-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 2rpx solid var(--border);
}
.product-thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: calc(var(--radius) - 8rpx);
  flex-shrink: 0;
}
.product-info {
  flex: 1;
  min-width: 0;
}
.product-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-spec {
  font-size: 24rpx;
  color: var(--text-400);
  display: block;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-price {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--primary);
  display: block;
  margin-top: 12rpx;
}
.product-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
}
.meta-item {
  font-size: 24rpx;
  color: var(--text-400);
}
.meta-divider {
  width: 2rpx;
  height: 24rpx;
  background: var(--background-300);
}
.add-btn {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-50);
  color: var(--primary);
  flex-shrink: 0;
  transition: all 0.2s ease;
}
.add-btn.selected {
  background: var(--primary);
  color: var(--primary-foreground);
}
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 40;
  background: rgba(255, 255, 255, 0.92);
  border-top: 2rpx solid var(--border);
}
.bar-inner {
  display: flex;
  align-items: center;
  gap: 32rpx;
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}
.selected-text {
  font-size: 28rpx;
  color: var(--text-400);
  white-space: nowrap;
}
.selected-text.error {
  color: var(--destructive);
}
.add-all-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 30rpx;
  font-weight: 600;
}
</style>
