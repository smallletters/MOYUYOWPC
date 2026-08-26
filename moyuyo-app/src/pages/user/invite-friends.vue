<template>
  <view class="invite-friends">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回">‹</view>
      <text class="title">邀请好友</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 顶部宣传 -->
      <view class="hero">
        <text class="hero-icon">🎁</text>
        <text class="hero-title">邀请好友各得 ¥20</text>
        <text class="hero-desc">好友通过您的链接注册，您和好友各获 ¥20 奖励</text>
      </view>

      <!-- 邀请链接 -->
      <view class="link-card">
        <text class="card-label">我的邀请码</text>
        <view class="link-row">
          <text class="link-text">{{ inviteCode }}</text>
          <view class="btn-copy" @click="onCopy">复制</view>
        </view>
      </view>

      <!-- 分享方式 -->
      <view class="share-section">
        <text class="section-title">分享到</text>
        <view class="share-grid">
          <view v-for="s in shareMethods" :key="s.id" class="share-item" @click="onShare(s)">
            <text class="share-icon">{{ s.icon }}</text>
            <text class="share-label">{{ s.label }}</text>
          </view>
        </view>
      </view>

      <!-- 邀请记录 -->
      <view class="record-section">
        <view class="record-header">
          <text class="section-title">邀请记录</text>
          <text class="record-more">已邀请 {{ invitedCount }} 人 ›</text>
        </view>
        <view v-if="records.length === 0" class="empty">暂无邀请记录</view>
        <view v-else class="record-list">
          <view v-for="r in records" :key="r.id" class="record-item">
            <text class="record-name">{{ r.name }}</text>
            <text class="record-time">{{ r.time }}</text>
            <text class="record-reward">+¥{{ r.reward }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      inviteCode: 'MOYUYO8888',
      invitedCount: 0,
      records: [],
      shareMethods: [
        { id: 'wechat', label: '微信', icon: '💚' },
        { id: 'moments', label: '朋友圈', icon: '👥' },
        { id: 'qq', label: 'QQ', icon: '🐧' },
        { id: 'link', label: '复制链接', icon: '🔗' },
      ],
    }
  },

  onShow() {
    this.loadRecords()
  },

  methods: {
    async loadRecords() {
      this.records = [
        { id: 1, name: '用户A***', time: '昨天', reward: 20 },
        { id: 2, name: '用户B***', time: '3天前', reward: 20 },
      ]
      this.invitedCount = this.records.length
    },

    goBack() {
      uni.navigateBack()
    },

    onCopy() {
      uni.setClipboardData({ data: `https://moyuyo.com/invite/${this.inviteCode}` })
    },

    onShare(s) {
      uni.showToast({ title: `分享到${s.label}`, icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.invite-friends {
  min-height: 100vh;
  background: var(--color-background);
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-right: 60rpx;
}

.content {
  padding: 24rpx;
}

.hero {
  padding: 48rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  text-align: center;
  margin-bottom: 24rpx;
}

.hero-icon {
  font-size: 80rpx;
  display: block;
  margin-bottom: 16rpx;
}

.hero-title {
  display: block;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
  margin-bottom: 8rpx;
}

.hero-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.link-card {
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.card-label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: 12rpx;
}

.link-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
}

.link-text {
  flex: 1;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.btn-copy {
  padding: 8rpx 16rpx;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: 999rpx;
  font-size: var(--font-size-xs);
}

.section-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.share-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.share-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.share-icon {
  font-size: 48rpx;
}

.share-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.record-section {
  margin-bottom: 24rpx;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.record-more {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.empty {
  text-align: center;
  padding: 48rpx 0;
  background: var(--color-surface);
  border: 1rpx dashed var(--color-divider);
  border-radius: var(--radius-md);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.record-item {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.record-name {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.record-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-right: 24rpx;
}

.record-reward {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}
</style>