<template>
  <view class="bargain">
    <!-- 深色顶部导航栏 -->



    <scroll-view scroll-y class="scroll">
      <!-- 商品展示卡片 -->
      <view class="product-card">
        <view class="product-content">
          <view class="product-image" />
          <view class="product-info">
            <text class="product-name">Premium 宠物自动饮水机 - 2.5L 大容量</text>
            <view class="product-price-row">
              <text class="price-original">$89</text>
              <text class="price-current">$72</text>
              <text class="price-goal-tag">目标价 $59</text>
            </view>
            <text class="price-progress">已砍 $17 / 还差 $13</text>
          </view>
        </view>
      </view>

      <!-- 砍价进度条区域 -->
      <view class="progress-section">
        <view class="progress-header">
          <text class="progress-label">砍价进度</text>
          <text class="progress-percent">已砍 56.7%</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" style="width: 56.7%" />
        </view>
        <view class="progress-labels">
          <view class="label-item">
            <text class="label-text">原价</text>
            <text class="label-price">$89</text>
          </view>
          <view class="label-item">
            <text class="label-text">当前价</text>
            <text class="label-price current">$72</text>
          </view>
          <view class="label-item">
            <text class="label-text">目标价</text>
            <text class="label-price goal">$59</text>
          </view>
        </view>
      </view>

      <!-- 倒计时区域 -->
      <view class="countdown-section">
        <view class="countdown-header">
          <text class="luc luc-clock" />
          <text class="countdown-label">砍价倒计时</text>
        </view>
        <view class="countdown-timer">
          <text class="timer-block">{{ timer.hours }}</text>
          <text class="timer-sep">:</text>
          <text class="timer-block">{{ timer.minutes }}</text>
          <text class="timer-sep">:</text>
          <text class="timer-block">{{ timer.seconds }}</text>
        </view>
        <text class="countdown-hint">剩余时间内砍到目标价即可下单购买</text>
      </view>

      <!-- 好友助力记录列表 -->
      <view class="helper-section">
        <view class="helper-header">
          <text class="helper-title">好友助力记录</text>
          <text class="helper-total">共 {{ helperList.length }} 位好友助力</text>
        </view>
        <view v-for="(item, i) in helperList" :key="i" class="helper-item">
          <view class="helper-avatar" :class="`avatar-${(i % 5) + 1}`">
            <text class="avatar-letter">{{ item.initial }}</text>
          </view>
          <view class="helper-info">
            <view class="helper-top">
              <text class="helper-name">{{ item.name }}</text>
              <text class="helper-amount">-${{ item.amount }}</text>
            </view>
            <text class="helper-time">{{ item.time }}</text>
          </view>
        </view>
      </view>

      <!-- 砍价规则折叠面板 -->
      <view class="rules-section">
        <view class="rules-header" @tap="toggleRules">
          <view class="rules-left">
            <text class="luc luc-info" />
            <text class="rules-label">砍价规则</text>
          </view>
          <text class="luc luc-chevron-down" :class="{ rotated: rulesOpen }" />
        </view>
        <view class="rules-content" :class="{ open: rulesOpen }">
          <view class="rule-item">
            <text class="rule-num">1</text>
            <text class="rule-desc">
              发起砍价后，需在
              <text class="rule-highlight">24 小时内</text>
              邀请好友助力，将商品价格砍至目标价。
            </text>
          </view>
          <view class="rule-item">
            <text class="rule-num">2</text>
            <text class="rule-desc">
              每位好友每个商品仅可助力
              <text class="rule-highlight">1 次</text>
              ，助力金额随机。
            </text>
          </view>
          <view class="rule-item">
            <text class="rule-num">3</text>
            <text class="rule-desc">
              在规定时间内砍到目标价，可按目标价下单购买；超时则砍价失败。
            </text>
          </view>
          <view class="rule-item">
            <text class="rule-num">4</text>
            <text class="rule-desc">砍价成功的订单不支持退换货，请确认后购买。</text>
          </view>
          <view class="rule-item">
            <text class="rule-num">5</text>
            <text class="rule-desc">如发现恶意刷助力行为，MOYUYO 有权取消砍价资格。</text>
          </view>
        </view>
      </view>

      <!-- 操作按钮区 -->
      <view class="action-section">
        <view class="btn-primary-large" @tap="showShare = true">
          <text class="luc luc-share-2" />
          <text>邀请好友帮砍</text>
        </view>
        <view class="btn-secondary" @tap="handleBuyCurrent">
          <text class="luc luc-shopping-bag" />
          <text>以当前价格 $72 购买</text>
        </view>
      </view>

      <view class="bottom-safe" />
    </scroll-view>

    <!-- 分享弹窗 -->
    <view class="share-overlay" :class="{ active: showShare }" @tap="showShare = false">
      <view class="share-sheet" @tap.stop>
        <view class="sheet-handle" />
        <text class="sheet-title">分享给好友</text>
        <view class="share-channels">
          <view class="channel-item">
            <view class="channel-icon bg-instagram">
              <text class="luc luc-camera" />
            </view>
            <text class="channel-name">Instagram</text>
          </view>
          <view class="channel-item">
            <view class="channel-icon bg-facebook">
              <text class="luc luc-users" />
            </view>
            <text class="channel-name">Facebook</text>
          </view>
          <view class="channel-item">
            <view class="channel-icon bg-twitter">
              <text class="luc luc-at-sign" />
            </view>
            <text class="channel-name">Twitter</text>
          </view>
          <view class="channel-item">
            <view class="channel-icon bg-link">
              <text class="luc luc-link" />
            </view>
            <text class="channel-name">复制链接</text>
          </view>
        </view>
        <view class="sheet-cancel" @tap="showShare = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script>
import { bargainApi } from '@/api'

export default {
  pageTitleKey: 'pageTitle.goodsBargain',

  data() {
    return {
      showShare: false,
      rulesOpen: false,
      bargainId: null,
      bargainDetail: null,
      timer: { hours: '00', minutes: '00', seconds: '00' },
      helperList: [],
    }
  },

  onLoad(options) {
    this.bargainId = options.id
    this.loadBargainDetail()
  },

  methods: {
    toggleRules() {
      this.rulesOpen = !this.rulesOpen
    },

    async loadBargainDetail() {
      try {
        const res = await bargainApi.getBargainDetail(this.bargainId)
        this.bargainDetail = res.data
        this.timer = {
          hours: String(res.data.remainingHours).padStart(2, '0'),
          minutes: String(res.data.remainingMinutes).padStart(2, '0'),
          seconds: String(res.data.remainingSeconds).padStart(2, '0'),
        }
        this.helperList = res.data.helpers || []
      } catch (err) {
        uni.showToast({ title: '加载砍价信息失败', icon: 'none' })
      }
    },

    async handleHelpBargain() {
      try {
        await bargainApi.helpBargain(this.bargainId)
        uni.showToast({ title: '助力成功！', icon: 'success' })
        this.loadBargainDetail()
      } catch (err) {
        uni.showToast({ title: err.message || '助力失败', icon: 'none' })
      }
    },

    handleBuyCurrent() {
      uni.showToast({ title: '以当前价格加入购物车', icon: 'none' })
    },
  },
}
</script>

<style scoped>
.bargain {
  min-height: 100vh;
  background: var(--background);
}
.nav-header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--background-800);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 32rpx;
}
.nav-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  color: var(--background-50);
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--background-50);
}
.nav-placeholder {
  width: 64rpx;
  height: 64rpx;
}
.scroll {
  padding: 0 32rpx;
}
.product-card {
  margin-top: 32rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 2rpx solid var(--border);
  overflow: hidden;
}
.product-content {
  display: flex;
  padding: 32rpx;
  gap: 32rpx;
}
.product-image {
  width: 192rpx;
  height: 192rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--brand-100), var(--brand-300), var(--brand-200));
}
.product-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12rpx;
}
.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}
.price-original {
  font-size: 24rpx;
  color: var(--text-400);
  text-decoration: line-through;
}
.price-current {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--state-error);
}
.price-goal-tag {
  font-size: 24rpx;
  font-weight: 600;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: var(--state-error-surface);
  color: var(--state-error);
}
.price-progress {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.progress-section {
  margin-top: 32rpx;
  padding: 32rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 2rpx solid var(--border);
}
.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.progress-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.progress-percent {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--primary);
}
.progress-track {
  width: 100%;
  height: 24rpx;
  border-radius: 999px;
  overflow: hidden;
  background: var(--background-200);
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--primary), var(--brand-300));
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}
.progress-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}
.label-item {
  text-align: center;
}
.label-text {
  display: block;
  font-size: 20rpx;
  color: var(--muted-foreground);
}
.label-price {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text-500);
}
.label-price.current {
  color: var(--state-error);
}
.label-price.goal {
  color: var(--state-success);
}
.countdown-section {
  margin-top: 32rpx;
  padding: 32rpx;
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--brand-500), var(--brand-600));
}
.countdown-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.countdown-header .iconfont {
  color: var(--primary-foreground);
  font-size: 32rpx;
}
.countdown-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--primary-foreground);
}
.countdown-timer {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 16rpx;
}
.timer-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.25);
  color: var(--primary-foreground);
  font-size: 32rpx;
  font-weight: 700;
}
.timer-sep {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--primary-foreground);
}
.countdown-hint {
  font-size: 24rpx;
  margin-top: 16rpx;
  color: rgba(255, 255, 255, 0.8);
}
.helper-section {
  margin-top: 32rpx;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.helper-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  border-bottom: 2rpx solid var(--border);
}
.helper-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.helper-total {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.helper-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  border-bottom: 2rpx solid var(--border);
}
.helper-item:last-child {
  border-bottom: none;
}
.helper-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-letter {
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
}
.avatar-1 {
  background: linear-gradient(135deg, var(--brand-200), var(--brand-400));
}
.avatar-2 {
  background: linear-gradient(135deg, var(--state-success), var(--brand-300));
}
.avatar-3 {
  background: linear-gradient(135deg, var(--brand-300), var(--brand-600));
}
.avatar-4 {
  background: linear-gradient(135deg, #ff9500, #ff6b35);
}
.avatar-5 {
  background: linear-gradient(135deg, #5856d6, #af52de);
}
.helper-info {
  flex: 1;
  min-width: 0;
}
.helper-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.helper-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.helper-amount {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--state-success);
  flex-shrink: 0;
}
.helper-time {
  font-size: 24rpx;
  color: var(--muted-foreground);
}
.rules-section {
  margin-top: 32rpx;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.rules-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
  background: transparent;
}
.rules-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.rules-left .iconfont {
  color: var(--muted-foreground);
  font-size: 32rpx;
}
.rules-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.rules-header .iconfont {
  color: var(--muted-foreground);
  font-size: 32rpx;
  transition: transform 0.3s ease;
}
.rules-header .iconfont.rotated {
  transform: rotate(180deg);
}
.rules-content {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
  padding: 0 32rpx;
}
.rules-content.open {
  max-height: 600rpx;
  padding-bottom: 32rpx;
}
.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 20rpx;
}
.rule-num {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 4rpx;
  background: var(--background-200);
  color: var(--text-600);
}
.rule-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  line-height: 1.6;
}
.rule-highlight {
  color: var(--foreground);
  font-weight: 600;
}
.action-section {
  margin-top: 40rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.btn-primary-large {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  height: 112rpx;
  border-radius: 24rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 32rpx;
  font-weight: 600;
}
.btn-secondary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--secondary);
  color: var(--secondary-foreground);
  font-size: 28rpx;
  font-weight: 600;
}
.bottom-safe {
  height: 64rpx;
}

/* 分享弹窗 */
.share-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  visibility: hidden;
  transition:
    opacity 0.25s ease,
    visibility 0.25s ease;
}
.share-overlay.active {
  opacity: 1;
  visibility: visible;
}
.share-sheet {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--card);
  border-radius: 32rpx 32rpx 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.share-overlay.active .share-sheet {
  transform: translateY(0);
}
.sheet-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999px;
  background: var(--background-400);
  margin: 24rpx auto 16rpx;
}
.sheet-title {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
  padding: 0 32rpx 32rpx;
}
.share-channels {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32rpx;
  padding: 0 32rpx 40rpx;
}
.channel-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.channel-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.bg-instagram {
  background: linear-gradient(135deg, #f09433, #e6683c, #dc2743, #cc2366, #bc1888);
}
.bg-facebook {
  background: var(--brand-500);
}
.bg-twitter {
  background: #1d1d1f;
}
.bg-link {
  background: var(--background-200);
}
.channel-icon .iconfont {
  color: #fff;
  font-size: 48rpx;
}
.bg-link .iconfont {
  color: var(--foreground);
}
.channel-name {
  font-size: 24rpx;
  color: var(--foreground);
}
.sheet-cancel {
  display: block;
  width: calc(100% - 64rpx);
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 600;
  margin: 0 32rpx 32rpx;
  background: var(--background-200);
  color: var(--foreground);
}
</style>
