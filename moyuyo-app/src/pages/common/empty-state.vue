<template>
  <view class="empty-state-page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">{{ pageTitle }}</text>
    </view>

    <scroll-view class="state-body" scroll-y>
      <!-- 空状态展示区 -->
      <view v-if="currentType === 'empty'" class="state-section">
        <view v-for="(state, index) in emptyStates" :key="index" class="state-card">
          <text class="state-label">{{ state.label }}</text>
          <view class="state-icon-wrap" :style="{ background: state.iconBg }">
            <text class="state-emoji luc" :class="$luc(state.icon)"></text>
          </view>
          <text class="state-title">{{ state.title }}</text>
          <text class="state-desc">{{ state.desc }}</text>
          <view class="state-btn" :class="state.btnClass" @tap="state.action">
            <text class="state-btn-text" :class="state.btnTextClass">
              <text v-if="state.btnIcon" class="luc" :class="$luc(state.btnIcon)"></text>{{ state.btnText }}
            </text>
          </view>
        </view>
      </view>

      <!-- 加载状态展示区 -->
      <view v-if="currentType === 'loading'" class="state-section">
        <!-- 骨架屏示例 -->
        <view class="state-card">
          <text class="state-label">Skeleton · 列表</text>
          <view class="skeleton-group">
            <view class="skeleton-row">
              <view class="skeleton-circle" />
              <view class="skeleton-lines">
                <view class="skeleton-line" style="width: 75%" />
                <view class="skeleton-line" style="width: 40%" />
              </view>
            </view>
            <view class="skeleton-lines" style="padding-left: 96rpx">
              <view class="skeleton-line" style="width: 85%" />
              <view class="skeleton-line" style="width: 60%" />
            </view>
          </view>
        </view>

        <!-- Spinner -->
        <view class="state-card">
          <text class="state-label">加载中</text>
          <view class="spinner-row">
            <view class="dot-pulse">
              <view class="dot" />
              <view class="dot" />
              <view class="dot" />
            </view>
            <text class="loading-text">加载中...</text>
          </view>
        </view>

        <!-- 进度条 -->
        <view class="state-card">
          <text class="state-label">进度条</text>
          <view class="progress-track">
            <view class="progress-bar" style="width: 67%" />
          </view>
          <view class="progress-label-row">
            <text class="progress-label">67%</text>
            <text class="progress-label">下载中...</text>
          </view>
        </view>
      </view>

      <!-- 错误状态展示区 -->
      <view v-if="currentType === 'error'" class="state-section">
        <view v-for="(state, index) in errorStates" :key="index" class="state-card">
          <text class="state-label">{{ state.label }}</text>
          <view class="state-icon-wrap" :style="{ background: state.iconBg }">
            <text class="state-emoji luc" :class="$luc(state.icon)"></text>
          </view>
          <text class="state-title">{{ state.title }}</text>
          <text class="state-desc">{{ state.desc }}</text>
          <view class="state-btn state-btn-primary" @tap="state.action">
            <text class="state-btn-text state-btn-text-primary">
              <text v-if="state.btnIcon" class="luc" :class="$luc(state.btnIcon)"></text>{{ state.btnText }}
            </text>
          </view>
        </view>
      </view>

      <!-- 成功/提示状态 -->
      <view v-if="currentType === 'success'" class="state-section">
        <!-- Toast 通知 -->
        <view class="state-card toast-card">
          <text class="state-label">Toast 通知</text>
          <view class="toast-list">
            <view class="toast-item">
              <view class="toast-icon toast-icon-success">
                <text class="toast-emoji"><text class="luc luc-check"></text></text>
              </view>
              <view class="toast-content">
                <text class="toast-title">操作成功</text>
                <text class="toast-desc">订单已成功提交</text>
              </view>
            </view>
            <view class="toast-item">
              <view class="toast-icon toast-icon-error">
                <text class="toast-emoji"><text class="luc luc-x"></text></text>
              </view>
              <view class="toast-content">
                <text class="toast-title">操作失败</text>
                <text class="toast-desc">网络异常，请稍后重试</text>
              </view>
            </view>
            <view class="toast-item">
              <view class="toast-icon toast-icon-info">
                <text class="toast-emoji">ℹ</text>
              </view>
              <view class="toast-content">
                <text class="toast-title">温馨提示</text>
                <text class="toast-desc">您的会员即将过期</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 成功/失败状态卡片 -->
        <view class="state-card">
          <text class="state-label">Success</text>
          <view class="state-icon-wrap" style="background: rgba(171, 185, 173, 0.2)">
            <text class="state-emoji"><text class="luc luc-check"></text></text>
          </view>
          <text class="state-title">支付成功</text>
          <text class="state-desc">订单 #MOY20260715001 已支付成功</text>
          <view class="state-btn state-btn-primary">
            <text class="state-btn-text state-btn-text-primary">查看订单</text>
          </view>
        </view>

        <view class="state-card">
          <text class="state-label">Failure</text>
          <view class="state-icon-wrap" style="background: rgba(201, 110, 95, 0.15)">
            <text class="state-emoji"><text class="luc luc-x"></text></text>
          </view>
          <text class="state-title">支付失败</text>
          <text class="state-desc">余额不足，请更换支付方式</text>
          <view class="state-btn state-btn-primary">
            <text class="state-btn-text state-btn-text-primary">重新支付</text>
          </view>
        </view>
      </view>

      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部切换 Tab -->
    <view class="type-tab-bar">
      <view
        v-for="(tab, index) in typeTabs"
        :key="index"
        class="type-tab-item"
        :class="{ 'type-tab-active': currentType === tab.value }"
        @tap="switchType(tab.value)"
      >
        <text class="type-tab-text" :class="{ 'type-tab-text-active': currentType === tab.value }">
          {{ tab.label }}
        </text>
      </view>
    </view>
  </view>
</template><script setup>
import { ref } from 'vue'

// 页面标题
const pageTitle = ref('空状态')

// 当前展示类型
const currentType = ref('empty')

// 类型切换 Tab
const typeTabs = [
  { label: '空状态', value: 'empty' },
  { label: '加载中', value: 'loading' },
  { label: '错误', value: 'error' },
  { label: '成功/提示', value: 'success' },
]

// 空状态数据
const emptyStates = ref([
  {
    label: 'Cart',
    icon: 'shopping-cart',
    iconBg: 'rgba(219, 201, 138, 0.15)',
    title: '购物车是空的',
    desc: '快去挑选心仪的商品，把你的购物车装满吧',
    btnText: '去逛逛',
    btnClass: 'state-btn-primary',
    btnTextClass: 'state-btn-text-primary',
    action: () => uni.switchTab({ url: '/pages/tabbar/category' }),
  },
  {
    label: 'Orders',
    icon: 'clipboard-list',
    iconBg: 'rgba(219, 201, 138, 0.15)',
    title: '暂无订单记录',
    desc: '你还没有下过订单，去发现一些好物吧',
    btnText: '去下单',
    btnClass: 'state-btn-primary',
    btnTextClass: 'state-btn-text-primary',
    action: () => uni.switchTab({ url: '/pages/tabbar/home' }),
  },
  {
    label: 'Favorites',
    icon: 'heart',
    iconBg: 'rgba(201, 110, 95, 0.15)',
    title: '收藏还是空的',
    desc: '看到喜欢的商品就收藏起来，方便以后找到',
    btnText: '去发现',
    btnClass: 'state-btn-primary',
    btnTextClass: 'state-btn-text-primary',
    action: () => uni.switchTab({ url: '/pages/tabbar/category' }),
  },
  {
    label: 'Messages',
    icon: 'message-circle',
    iconBg: 'rgba(219, 201, 138, 0.15)',
    title: '暂无新消息',
    desc: '有新消息时会在这里通知你，保持关注哦',
    btnText: '我知道了',
    btnClass: 'state-btn-secondary',
    btnTextClass: 'state-btn-text-secondary',
    action: () => uni.navigateBack(),
  },
  {
    label: 'Search',
    icon: 'search',
    iconBg: '#F2F2F7',
    title: '未找到相关结果',
    desc: '试试其他关键词，或者换个筛选条件看看',
    btnText: '重新搜索',
    btnClass: 'state-btn-ghost',
    btnTextClass: 'state-btn-text-ghost',
    action: () => uni.navigateTo({ url: '/pages/goods/search' }),
  },
])

// 错误状态数据
const errorStates = ref([
  {
    label: 'Network',
    icon: 'signal',
    iconBg: 'rgba(255, 149, 0, 0.15)',
    title: '网络连接异常',
    desc: '请检查你的网络连接，稍后重试',
    btnIcon: 'refresh-cw', btnText: '重新连接',
    action: () => uni.showToast({ title: '正在重连...', icon: 'none' }),
  },
  {
    label: 'Server Error',
    icon: 'monitor',
    iconBg: 'rgba(201, 110, 95, 0.15)',
    title: '服务器开小差了',
    desc: '服务暂时不可用，我们正在紧急修复中',
    btnIcon: 'refresh-cw', btnText: '刷新重试',
    action: () => uni.showToast({ title: '正在刷新...', icon: 'none' }),
  },
  {
    label: '404',
    icon: 'search',
    iconBg: '#F2F2F7',
    title: '页面不存在',
    desc: '你访问的页面可能已被移除或地址有误',
    btnIcon: 'home', btnText: '返回首页',
    action: () => uni.switchTab({ url: '/pages/tabbar/home' }),
  },
])

// 切换类型
const switchType = (type) => {
  currentType.value = type
  const tab = typeTabs.find((t) => t.value === type)
  if (tab) pageTitle.value = tab.label
}

// 返回
const goBack = () => {
  uni.navigateBack()
}
</script>

<style lang="scss" scoped>
.empty-state-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

/* 导航栏 */
.nav-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--color-background);
  border-bottom: 1rpx solid var(--color-border);
  gap: 24rpx;
}

.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 44rpx;
  color: var(--color-primary);
}

.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}

/* 内容 */
.state-body {
  flex: 1;
  padding: 32rpx;
}

.state-section {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

/* 状态卡片 */
.state-card {
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  border-radius: 32rpx;
  padding: 64rpx 48rpx 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.state-label {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
  margin-bottom: 32rpx;
}

.state-icon-wrap {
  width: 144rpx;
  height: 144rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40rpx;
}

.state-emoji {
  font-size: 56rpx;
}

.state-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.state-desc {
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
  margin-bottom: 40rpx;
  max-width: 440rpx;
}

/* 状态按钮 */
.state-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  padding: 0 40rpx;
  border-radius: 36rpx;
}

.state-btn-primary {
  background: var(--color-primary);
}

.state-btn-secondary {
  background: #f2f2f7;
}

.state-btn-ghost {
  background: transparent;
}

.state-btn-text {
  font-size: 28rpx;
  font-weight: 600;
}

.state-btn-text-primary {
  color: #ffffff;
}

.state-btn-text-secondary {
  color: var(--color-text);
}

.state-btn-text-ghost {
  color: var(--color-primary);
}

/* 骨架屏 */
.skeleton-group {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.skeleton-row {
  display: flex;
  gap: 24rpx;
  align-items: center;
}

.skeleton-circle {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(90deg, #f2f2f7 25%, #f7f7fa 50%, #f2f2f7 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.skeleton-line {
  height: 28rpx;
  border-radius: 12rpx;
  background: linear-gradient(90deg, #f2f2f7 25%, #f7f7fa 50%, #f2f2f7 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* Spinner */
.spinner-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.dot-pulse {
  display: flex;
  gap: 12rpx;
}

.dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--color-primary);
  animation: dotPulse 1.4s ease-in-out infinite both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}
.dot:nth-child(2) {
  animation-delay: -0.16s;
}
.dot:nth-child(3) {
  animation-delay: 0s;
}

@keyframes dotPulse {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.loading-text {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

/* 进度条 */
.progress-track {
  width: 100%;
  height: 12rpx;
  background: #f2f2f7;
  border-radius: 6rpx;
  overflow: hidden;
  margin-bottom: 16rpx;
}

.progress-bar {
  height: 100%;
  border-radius: 6rpx;
  background: var(--color-primary);
}

.progress-label-row {
  display: flex;
  justify-content: space-between;
}

.progress-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

/* Toast */
.toast-card {
  align-items: stretch;
}

.toast-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.toast-icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.toast-icon-success {
  background: rgba(171, 185, 173, 0.2);
}

.toast-icon-error {
  background: rgba(201, 110, 95, 0.15);
}

.toast-icon-info {
  background: rgba(219, 201, 138, 0.15);
}

.toast-emoji {
  font-size: 22rpx;
  color: var(--color-primary);
}

.toast-content {
  flex: 1;
}

.toast-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.toast-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

.bottom-space {
  height: 160rpx;
}

/* 底部切换栏 */
.type-tab-bar {
  display: flex;
  background: var(--color-card);
  border-top: 1rpx solid var(--color-border);
  padding: 16rpx 0;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}

.type-tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 0;
  position: relative;
}

.type-tab-text {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.type-tab-text-active {
  font-weight: 600;
  color: var(--color-primary);
}

.type-tab-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-primary);
}
</style>
