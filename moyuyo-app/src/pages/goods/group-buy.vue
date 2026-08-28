<template>
  <view class="group-buy">
    <!-- 顶部导航栏 -->
    <view class="nav-header">
      <view class="nav-back" @tap="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="nav-title">拼团购买</text>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 主商品展示区 -->
      <view class="main-product">
        <view class="product-image" />
        <view class="product-info">
          <view class="group-tag">
            <text class="luc luc-users" />
            <text>3人拼团</text>
          </view>
          <text class="product-name">MILO联名探险胸背带</text>
          <view class="price-row">
            <text class="group-price">$35.99</text>
            <text class="original-price">$49</text>
            <text class="save-tag">省 $13.01</text>
          </view>
        </view>
      </view>

      <!-- 拼团进度区 -->
      <view class="group-progress">
        <view class="progress-header">
          <text class="progress-text">
            已有
            <text class="highlight">2/3</text>
            人参团，还差
            <text class="highlight">1</text>
            人
          </text>
          <view class="countdown-tag">
            <text class="luc luc-clock" />
            <text class="countdown-text">剩余 18:32:15</text>
          </view>
        </view>
        <view class="progress-track">
          <view class="progress-fill" style="width: 66.67%" />
        </view>
        <view class="member-list">
          <view class="member-item">
            <view class="member-avatar me">
              <text>我</text>
            </view>
            <text class="member-role">团长</text>
          </view>
          <view class="member-item">
            <view class="member-avatar friend">
              <text>J</text>
            </view>
            <text class="member-role">好友</text>
          </view>
          <view class="member-item">
            <view class="member-avatar empty">
              <text class="luc luc-plus" />
            </view>
            <text class="member-role">等待中</text>
          </view>
          <view class="invite-hint" @tap="handleInvite">
            <text class="luc luc-share-2" />
            <text>邀请好友加速成团</text>
          </view>
        </view>
      </view>

      <!-- 操作按钮区 -->
      <view class="action-section">
        <view class="btn-invite" @tap="handleInvite">
          <text class="luc luc-share-2" />
          <text>邀请好友参团</text>
        </view>
        <view class="btn-alone" @tap="handleAlone">
          <text>我一个人买</text>
          <text class="alone-price">$49</text>
        </view>
      </view>

      <!-- 拼团规则折叠面板 -->
      <view class="rules-section">
        <view class="rules-header" @tap="toggleRules">
          <view class="rules-left">
            <text class="luc luc-info" />
            <text class="rules-label">拼团规则</text>
          </view>
          <text class="luc luc-chevron-down" :class="{ rotated: rulesOpen }" />
        </view>
        <view class="rules-content" :class="{ open: rulesOpen }">
          <view class="rule-item">
            <view class="rule-icon clock">
              <text class="luc luc-clock" />
            </view>
            <view class="rule-text">
              <text class="rule-title">24小时倒计时</text>
              <text class="rule-desc">开团后24小时内邀请好友参团，超时未成团自动退款</text>
            </view>
          </view>
          <view class="rule-item">
            <view class="rule-icon check">
              <text class="luc luc-check" />
            </view>
            <view class="rule-text">
              <text class="rule-title">成团自动发货</text>
              <text class="rule-desc">拼团成功后系统自动发货，无需手动确认</text>
            </view>
          </view>
          <view class="rule-item">
            <view class="rule-icon refund">
              <text class="luc luc-rotate-ccw" />
            </view>
            <view class="rule-text">
              <text class="rule-title">未成团自动退款</text>
              <text class="rule-desc">拼团失败时系统自动全额退款至原支付账户</text>
            </view>
          </view>
          <view class="rule-item">
            <view class="rule-icon limit">
              <text class="luc luc-shield" />
            </view>
            <view class="rule-text">
              <text class="rule-title">每人限参1团</text>
              <text class="rule-desc">同一用户每个拼团活动仅可参与一次</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 更多拼团好物 -->
      <view class="recommend-section">
        <view class="recommend-header">
          <text class="recommend-title">更多拼团好物</text>
          <view class="recommend-all">
            <text>查看全部</text>
            <text class="luc luc-chevron-right" />
          </view>
        </view>
        <scroll-view scroll-x class="recommend-scroll">
          <view v-for="(item, i) in recommendList" :key="i" class="recommend-card">
            <view class="rec-image" :style="{ background: item.bg }" />
            <view class="rec-info">
              <text class="rec-name">{{ item.name }}</text>
              <view class="rec-price-row">
                <text class="rec-price">${{ item.price }}</text>
                <text class="rec-original">${{ item.original }}</text>
              </view>
              <view class="rec-count">
                <text class="luc luc-users" />
                <text>{{ item.count }}</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="bottom-safe" />
    </scroll-view>
  </view>
</template>

<script>
import { groupBuyApi } from '@/api'

export default {
  data() {
    return {
      groupBuyId: null,
      rulesOpen: false,
      groupBuyDetail: null,
      members: [],
      recommendList: [],
    }
  },

  onLoad(options) {
    this.groupBuyId = options.id
    this.loadGroupBuyDetail()
    this.loadGroupBuyMembers()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    toggleRules() {
      this.rulesOpen = !this.rulesOpen
    },

    async loadGroupBuyDetail() {
      try {
        const res = await groupBuyApi.getGroupBuyDetail(this.groupBuyId)
        this.groupBuyDetail = res.data
      } catch (err) {
        uni.showToast({ title: '加载拼团信息失败', icon: 'none' })
      }
    },

    async loadGroupBuyMembers() {
      try {
        const res = await groupBuyApi.getGroupBuyMembers(this.groupBuyId)
        this.members = res.data || []
      } catch (err) {
        console.error('加载拼团成员失败', err)
      }
    },

    handleInvite() {
      uni.showToast({ title: '邀请好友功能开发中', icon: 'none' })
    },

    handleAlone() {
      uni.showToast({ title: '以原价单独购买', icon: 'none' })
    },
  },
}
</script>

<style scoped>
.group-buy {
  min-height: 100vh;
  background: var(--background-100);
}
.nav-header {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--background-800);
}
.nav-back {
  position: absolute;
  left: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  color: var(--background-50);
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--background-50);
}
.scroll {
  padding-bottom: 32rpx;
}
.main-product {
  background: var(--card);
  border-radius: 0 0 32rpx 32rpx;
  overflow: hidden;
}
.product-image {
  width: 100%;
  aspect-ratio: 1;
  background: linear-gradient(
    145deg,
    var(--brand-50) 0%,
    var(--brand-100) 40%,
    var(--secondary) 100%
  );
}
.product-info {
  padding: 32rpx 32rpx 24rpx;
}
.group-tag {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
  background: rgba(255, 59, 48, 0.1);
  color: var(--destructive);
  font-size: 24rpx;
  font-weight: 600;
}
.product-name {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--foreground);
  line-height: 1.4;
  display: block;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-top: 16rpx;
}
.group-price {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--destructive);
}
.original-price {
  font-size: 28rpx;
  color: var(--muted-foreground);
  text-decoration: line-through;
}
.save-tag {
  font-size: 24rpx;
  font-weight: 600;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: rgba(255, 59, 48, 0.1);
  color: var(--destructive);
}
.group-progress {
  margin: 24rpx 32rpx;
  padding: 32rpx;
  border-radius: 32rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.progress-text {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.highlight {
  color: var(--primary);
}
.countdown-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 16rpx;
  border-radius: 16rpx;
  background: var(--background-100);
}
.countdown-tag .iconfont {
  color: var(--destructive);
  font-size: 28rpx;
}
.countdown-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--destructive);
}
.progress-track {
  width: 100%;
  height: 12rpx;
  border-radius: 999px;
  overflow: hidden;
  background: var(--background-200);
}
.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--primary);
}
.member-list {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 32rpx;
}
.member-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.member-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}
.member-avatar.me {
  background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
  color: var(--primary-foreground);
}
.member-avatar.friend {
  background: linear-gradient(135deg, #5856d6, #af52de);
  color: #fff;
}
.member-avatar.empty {
  border: 4rpx dashed var(--background-400);
  background: var(--background-100);
  color: var(--muted-foreground);
}
.member-role {
  font-size: 20rpx;
  font-weight: 500;
  color: var(--muted-foreground);
}
.invite-hint {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 24rpx;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--primary);
  font-size: 24rpx;
  font-weight: 500;
}
.action-section {
  margin: 0 32rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.btn-invite {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  height: 104rpx;
  border-radius: 32rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 32rpx;
  font-weight: 600;
}
.btn-alone {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  border-radius: 32rpx;
  border: 2rpx solid var(--border);
  background: var(--card);
  color: var(--foreground);
  font-size: 28rpx;
  font-weight: 600;
}
.alone-price {
  color: var(--muted-foreground);
}
.rules-section {
  margin: 24rpx 32rpx;
  border-radius: 32rpx;
  overflow: hidden;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.rules-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
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
}
.rules-content.open {
  max-height: 800rpx;
}
.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  padding: 0 32rpx 24rpx;
}
.rule-icon {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  flex-shrink: 0;
  margin-top: 4rpx;
}
.rule-icon.clock {
  background: var(--brand-50);
  color: var(--primary);
}
.rule-icon.check {
  background: var(--state-success-surface);
  color: var(--state-success);
}
.rule-icon.refund {
  background: rgba(255, 149, 0, 0.1);
  color: #ff9500;
}
.rule-icon.limit {
  background: var(--background-100);
  color: var(--muted-foreground);
}
.rule-title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
}
.rule-desc {
  font-size: 24rpx;
  color: var(--muted-foreground);
  margin-top: 4rpx;
  display: block;
}
.recommend-section {
  margin-top: 48rpx;
}
.recommend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  margin-bottom: 24rpx;
}
.recommend-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}
.recommend-all {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: 24rpx;
  font-weight: 500;
  color: var(--primary);
}
.recommend-scroll {
  display: flex;
  gap: 24rpx;
  padding: 0 32rpx;
  white-space: nowrap;
}
.recommend-card {
  display: inline-block;
  width: 280rpx;
  border-radius: 32rpx;
  overflow: hidden;
  background: var(--card);
  border: 2rpx solid var(--border);
  flex-shrink: 0;
}
.rec-image {
  width: 100%;
  aspect-ratio: 1;
}
.rec-info {
  padding: 20rpx;
}
.rec-name {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 12rpx;
}
.rec-price {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--destructive);
}
.rec-original {
  font-size: 20rpx;
  color: var(--muted-foreground);
  text-decoration: line-through;
}
.rec-count {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: var(--muted-foreground);
}
.rec-count .iconfont {
  color: var(--primary);
}
.bottom-safe {
  height: 32rpx;
}
</style>
