<template>
  <view class="invite">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon">‹</text>
      </view>
      <text class="header-title">邀请好友</text>
      <view class="header-btn" />
    </view>

    <!-- 顶部品牌色渐变区域 -->
    <view class="hero-section">
      <view class="hero-bg-circle hero-bg-circle--tl" />
      <view class="hero-bg-circle hero-bg-circle--bl" />
      <view class="hero-content">
        <text class="hero-title">邀请好友，各得奖励</text>
        <text class="hero-subtitle">每邀请1位好友注册，双方各得200积分</text>
        <!-- 邀请码展示 -->
        <view class="invite-code-box">
          <text class="invite-code-label">邀请码</text>
          <text class="invite-code-value">{{ inviteCode }}</text>
          <view class="invite-code-copy" @click="onCopyCode">
            <text class="copy-icon">📋</text>
            <text class="copy-text">复制</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 奖励说明卡片 -->
    <view class="reward-card">
      <text class="reward-card-title">如何获取奖励</text>
      <view class="reward-steps">
        <view class="reward-step">
          <view class="step-icon step-icon--brand">
            <text class="step-icon-text">🔗</text>
          </view>
          <text class="step-title">分享邀请链接</text>
          <text class="step-desc">发送给好友</text>
        </view>
        <view class="reward-step">
          <view class="step-icon step-icon--brand">
            <text class="step-icon-text">👤</text>
          </view>
          <text class="step-title">好友注册下单</text>
          <text class="step-desc">完成首单购买</text>
        </view>
        <view class="reward-step">
          <view class="step-icon step-icon--success">
            <text class="step-icon-text">🎁</text>
          </view>
          <text class="step-title">双方获得积分</text>
          <text class="step-desc">积分即时到账</text>
        </view>
      </view>
    </view>

    <!-- 分享渠道 -->
    <view class="share-section">
      <text class="share-section-title">分享给好友</text>
      <view class="share-channels">
        <view class="share-channel" @click="onShareWeChat">
          <view class="channel-icon channel-icon--green">
            <text class="channel-icon-text">💬</text>
          </view>
          <text class="channel-label">微信</text>
        </view>
        <view class="share-channel" @click="onShareWhatsApp">
          <view class="channel-icon channel-icon--brand">
            <text class="channel-icon-text">📞</text>
          </view>
          <text class="channel-label">WhatsApp</text>
        </view>
        <view class="share-channel" @click="onShareSMS">
          <view class="channel-icon channel-icon--blue">
            <text class="channel-icon-text">✉️</text>
          </view>
          <text class="channel-label">短信</text>
        </view>
        <view class="share-channel" @click="onCopyLink">
          <view class="channel-icon">
            <text class="channel-icon-text">🔗</text>
          </view>
          <text class="channel-label">复制链接</text>
        </view>
      </view>
    </view>

    <!-- 邀请统计 -->
    <view class="stats-section">
      <text class="section-title">我的邀请</text>
      <view class="stats-row">
        <view class="stat-item">
          <text class="stat-value stat-value--brand">{{ stats.invited }}</text>
          <text class="stat-label">已邀请</text>
        </view>
        <view class="stat-item">
          <text class="stat-value stat-value--brand">{{ stats.ordered }}</text>
          <text class="stat-label">已完成首单</text>
        </view>
        <view class="stat-item">
          <text class="stat-value stat-value--success">{{ stats.points }}</text>
          <text class="stat-label">获得积分</text>
        </view>
      </view>
    </view>

    <!-- 邀请记录列表 -->
    <view class="invite-list">
      <view v-for="item in inviteHistory" :key="item.id" class="invite-item">
        <view
          class="invite-avatar"
          :class="item.status === 'ordered' ? 'avatar-ordered' : 'avatar-pending'"
        >
          <text class="avatar-text">{{ item.initial }}</text>
        </view>
        <view class="invite-info">
          <text class="invite-name">{{ item.name }}</text>
          <text class="invite-time">{{ item.time }}</text>
        </view>
        <view class="invite-status">
          <text
            class="status-badge"
            :class="item.status === 'ordered' ? 'badge-ordered' : 'badge-pending'"
          >
            {{ item.statusLabel }}
          </text>
          <text v-if="item.reward" class="invite-reward">+{{ item.reward }}</text>
        </view>
      </view>
    </view>

    <!-- 月度排行提醒 -->
    <view class="ranking-banner">
      <view class="ranking-banner-icon">🏆</view>
      <view class="ranking-banner-info">
        <text class="ranking-banner-title">月度 TOP 10 额外奖励</text>
        <text class="ranking-banner-desc">本月排名靠前可获额外积分奖励</text>
      </view>
      <view class="ranking-banner-rank">
        <text class="rank-number">{{ stats.rank }}</text>
        <text class="rank-unit">th</text>
        <text class="rank-label">你的排名</text>
      </view>
    </view>
  </view>
</template>

<script>
import { inviteApi } from '@/api'

export default {
  data() {
    return {
      inviteCode: '',
      stats: {
        invited: 0,
        ordered: 0,
        points: 0,
        rank: 0,
      },
      inviteHistory: [],
    }
  },

  onLoad() {
    this.loadInviteData()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadInviteData() {
      try {
        const [codeRes, statsRes, historyRes] = await Promise.all([
          inviteApi.getInviteCode(),
          inviteApi.getInviteStats(),
          inviteApi.getInviteHistory({ page: 1, size: 20 }),
        ])
        // 后端 Result.success 包装：{ code, data, success }
        // getInviteCode 返回字符串 → data 字段是字符串本身
        this.inviteCode = (codeRes && codeRes.data) || codeRes || ''
        const stats = (statsRes && statsRes.data) || statsRes || {}
        this.stats = {
          invited: stats.invitedCount || 0,
          ordered: stats.completedOrders || 0,
          points: stats.earnedPoints || 0,
          rank: 0, // 排行暂未接入后端
        }
        const page = (historyRes && historyRes.data) || historyRes || {}
        const records = Array.isArray(page.records) ? page.records : (Array.isArray(page) ? page : [])
        this.inviteHistory = records.map((it) => {
          const ordered = it.status === 'ORDERED'
          return {
            id: it.id,
            initial: (it.inviteeUserId || 'U').toString().slice(-1).toUpperCase(),
            name: ordered ? `好友 ${it.inviteeUserId}` : `待激活 ${it.inviteeUserId || '注册中'}`,
            time: it.createTime ? new Date(it.createTime).toLocaleDateString('zh-CN') : '',
            status: ordered ? 'ordered' : 'pending',
            statusLabel: ordered ? '已完成' : '待完成',
            reward: it.pointsAwarded || 0,
          }
        })
      } catch (e) {
        console.warn('[invite] load failed', e)
        this.inviteCode = ''
        this.stats = { invited: 0, ordered: 0, points: 0, rank: 0 }
        this.inviteHistory = []
      }
    },

    onCopyCode() {
      if (!this.inviteCode) return
      uni.setClipboardData({
        data: this.inviteCode,
        success: () => {
          uni.showToast({ title: '复制成功', icon: 'none' })
        },
      })
    },

    onShareWeChat() {
      uni.showToast({ title: '分享到微信', icon: 'none' })
    },

    onShareWhatsApp() {
      uni.showToast({ title: '分享到 WhatsApp', icon: 'none' })
    },

    onShareSMS() {
      uni.showToast({ title: '分享到短信', icon: 'none' })
    },

    onCopyLink() {
      if (!this.inviteCode) return
      uni.setClipboardData({
        data: 'https://moyuyo.com/invite/' + this.inviteCode,
        success: () => {
          uni.showToast({ title: '链接已复制', icon: 'none' })
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.invite {
  min-height: 100vh;
  background: var(--background);
  padding-bottom: 40rpx;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 1rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 44rpx;
  color: var(--icon-700);
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}

/* ===== 品牌渐变区域 ===== */
.hero-section {
  position: relative;
  overflow: hidden;
  background: linear-gradient(160deg, var(--brand-400), var(--brand-600), var(--brand-700));
  padding: 60rpx 40rpx 50rpx;
}

.hero-bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  pointer-events: none;
}

.hero-bg-circle--tl {
  top: -120rpx;
  right: -120rpx;
  width: 360rpx;
  height: 360rpx;
}

.hero-bg-circle--bl {
  bottom: -80rpx;
  left: -80rpx;
  width: 240rpx;
  height: 240rpx;
}

.hero-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.5rpx;
}

.hero-subtitle {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
  text-align: center;
}

.invite-code-box {
  margin-top: 40rpx;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 32rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.15);
  border: 1rpx solid rgba(255, 255, 255, 0.25);
}

.invite-code-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
  white-space: nowrap;
}

.invite-code-value {
  flex: 1;
  text-align: center;
  font-size: 28rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
  color: #ffffff;
  font-family: monospace;
}

.invite-code-copy {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 24rpx;
  border-radius: 999rpx;
  background: #ffffff;
  white-space: nowrap;
}

.copy-icon {
  font-size: 24rpx;
}

.copy-text {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--brand-600);
}

/* ===== 奖励说明卡片 ===== */
.reward-card {
  margin: -24rpx 30rpx 0;
  padding: 36rpx 32rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 1rpx solid var(--border);
  box-shadow: var(--shadow-md);
  position: relative;
  z-index: 10;
}

.reward-card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-800);
  display: block;
  margin-bottom: 36rpx;
}

.reward-steps {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.reward-step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.step-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-icon--brand {
  background: var(--brand-50);
  border: 2rpx solid var(--brand-200);
}

.step-icon--success {
  background: var(--state-success-surface);
  border: 2rpx solid var(--state-success);
}

.step-icon-text {
  font-size: 32rpx;
}

.step-title {
  margin-top: 16rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--text-800);
  text-align: center;
  line-height: 1.3;
}

.step-desc {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: var(--text-400);
  text-align: center;
}

/* ===== 分享渠道 ===== */
.share-section {
  margin: 36rpx 30rpx 0;
}

.share-section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-800);
  display: block;
  margin-bottom: 28rpx;
}

.share-channels {
  display: flex;
  justify-content: space-around;
  padding: 28rpx 20rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 1rpx solid var(--border);
  box-shadow: var(--shadow-sm);
}

.share-channel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.channel-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--secondary);
}

.channel-icon--green {
  background: var(--state-success-surface);
}

.channel-icon--brand {
  background: var(--brand-50);
}

.channel-icon--blue {
  background: var(--brand-50);
}

.channel-icon-text {
  font-size: 36rpx;
}

.channel-label {
  font-size: 20rpx;
  font-weight: 500;
  color: var(--text-500);
}

/* ===== 邀请统计 ===== */
.stats-section {
  margin: 40rpx 30rpx 0;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-800);
  display: block;
  margin-bottom: 28rpx;
}

.stats-row {
  display: flex;
  gap: 20rpx;
  margin-bottom: 32rpx;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 28rpx 0;
  border-radius: var(--radius);
  background: var(--secondary);
}

.stat-value {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.2;
}

.stat-value--brand {
  color: var(--brand-500);
}

.stat-value--success {
  color: var(--state-success);
}

.stat-label {
  margin-top: 8rpx;
  font-size: 20rpx;
  font-weight: 500;
  color: var(--text-500);
}

/* ===== 邀请记录 ===== */
.invite-list {
  margin: 0 30rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.invite-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  border-radius: var(--radius);
  background: var(--card);
  border: 1rpx solid var(--border);
}

.invite-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-ordered {
  background: var(--brand-100);
}

.avatar-pending {
  background: var(--background-200);
}

.avatar-text {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--brand-500);
}

.avatar-pending .avatar-text {
  color: var(--text-500);
}

.invite-info {
  flex: 1;
  min-width: 0;
}

.invite-name {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-800);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invite-time {
  font-size: 22rpx;
  color: var(--text-400);
  display: block;
  margin-top: 6rpx;
}

.invite-status {
  text-align: right;
  flex-shrink: 0;
}

.status-badge {
  display: inline-block;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 600;
}

.badge-ordered {
  background: var(--state-success-surface);
  color: var(--state-success);
}

.badge-pending {
  background: var(--secondary);
  color: var(--text-500);
}

.invite-reward {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--brand-500);
}

/* ===== 月度排行 ===== */
.ranking-banner {
  margin: 32rpx 30rpx 0;
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx;
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--brand-50), var(--brand-100));
  border: 1rpx solid var(--brand-200);
}

.ranking-banner-icon {
  font-size: 44rpx;
  flex-shrink: 0;
}

.ranking-banner-info {
  flex: 1;
  min-width: 0;
}

.ranking-banner-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--brand-700);
  display: block;
}

.ranking-banner-desc {
  font-size: 22rpx;
  color: var(--text-500);
  display: block;
  margin-top: 6rpx;
}

.ranking-banner-rank {
  text-align: right;
  flex-shrink: 0;
}

.rank-number {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--brand-500);
  line-height: 1;
}

.rank-unit {
  font-size: 22rpx;
  color: var(--text-500);
}

.rank-label {
  font-size: 20rpx;
  color: var(--text-400);
  display: block;
  margin-top: 4rpx;
}
</style>
