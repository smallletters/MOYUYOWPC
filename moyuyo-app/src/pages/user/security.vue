<template>
  <view class="security">
    <!-- 安全状态概览卡片 -->
    <view class="status-card">
      <view class="status-icon">
        <text class="icon-shield">🛡</text>
      </view>
      <view class="status-info">
        <view class="status-row">
          <text class="status-title">安全评分：高</text>
          <view class="status-badge-safe">安全</view>
        </view>
        <text class="status-desc">您的账号安全等级良好</text>
        <text class="status-hint">2 条安全建议可供优化</text>
      </view>
    </view>

    <!-- 登录密码 -->
    <view class="section">
      <text class="section-label">登录密码</text>
      <view class="row" @click="onChangePassword">
        <view class="row-left">
          <text class="row-icon">🔒</text>
          <text class="row-text">修改密码</text>
        </view>
        <view class="row-right">
          <view class="badge-safe">已设置</view>
          <text class="row-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 两步验证 -->
    <view class="section">
      <text class="section-label">两步验证</text>
      <view class="row">
        <view class="row-left">
          <text class="row-icon">📱</text>
          <text class="row-text">开启两步验证</text>
        </view>
        <view class="toggle" :class="{ active: tfaEnabled }" @click="tfaEnabled = !tfaEnabled">
          <view class="toggle-knob" />
        </view>
      </view>
      <view class="divider" />
      <view class="row" @click="goTwoFactor">
        <view class="row-left">
          <text class="row-icon">🔑</text>
          <text class="row-text">验证方式</text>
        </view>
        <view class="row-right">
          <text class="row-value">TOTP</text>
          <text class="row-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 登录设备管理 -->
    <view class="section">
      <text class="section-label">设备管理</text>
      <view class="row" @click="goDevices">
        <view class="row-left">
          <text class="row-icon">💻</text>
          <text class="row-text">登录设备管理</text>
        </view>
        <view class="row-right">
          <text class="row-value">{{ deviceCount }}/3</text>
          <text class="row-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 第三方账号 -->
    <view class="section">
      <text class="section-label">第三方账号</text>
      <view v-for="(acc, i) in socialAccounts" :key="acc.id">
        <view class="row">
          <view class="row-left">
            <text class="row-icon">{{ acc.icon }}</text>
            <text class="row-text">{{ acc.name }}</text>
          </view>
          <view class="row-right">
            <view v-if="acc.linked" class="badge-safe">已绑定</view>
            <template v-else>
              <text class="badge-unlinked">未绑定</text>
              <view class="btn-bind" @click="onLink(acc)">绑定</view>
            </template>
            <text v-if="acc.linked" class="row-arrow">›</text>
          </view>
        </view>
        <view v-if="i < socialAccounts.length - 1" class="divider" />
      </view>
    </view>

    <!-- 其他 -->
    <view class="section">
      <text class="section-label">其他</text>
      <view class="row" @click="onMergeAccount">
        <view class="row-left">
          <text class="row-icon">🔀</text>
          <text class="row-text">账号合并</text>
        </view>
        <text class="row-arrow">›</text>
      </view>
      <view class="divider" />
      <view class="row" @click="onLockRecords">
        <view class="row-left">
          <text class="row-icon">📄</text>
          <text class="row-text">账号锁定记录</text>
        </view>
        <view class="row-right">
          <text class="row-value">查看记录</text>
          <text class="row-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 注销账号 -->
    <view class="delete-section" @click="onDeleteAccount">
      <text class="delete-text">注销账号</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      socialAccounts: [
        { id: 1, name: 'Apple ID', icon: '🍎', linked: true },
        { id: 2, name: 'Google', icon: '🔗', linked: true },
        { id: 3, name: 'Facebook', icon: '👤', linked: false },
      ],
      deviceCount: 2,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    tfaEnabled: {
      get() {
        return this.userStore.userInfo?.twoFactorEnabled || false
      },
      set(val) {
        this.userStore.toggle2FA(val)
      },
    },
  },

  methods: {
    onChangePassword() {
      uni.navigateTo({ url: '/pages/user/change-password' })
    },

    goTwoFactor() {
      uni.navigateTo({ url: '/pages/user/two-factor' })
    },

    goDevices() {
      uni.navigateTo({ url: '/pages/user/devices' })
    },

    onLink(acc) {
      uni.showToast({ title: `${acc.name} binding...`, icon: 'none' })
    },

    onMergeAccount() {
      uni.showToast({ title: '账号合并', icon: 'none' })
    },

    onLockRecords() {
      uni.showToast({ title: '锁定记录', icon: 'none' })
    },

    onDeleteAccount() {
      uni.showModal({
        title: 'Delete Account?',
        content: 'Your account will be scheduled for deletion. You have 30 days to recover.',
        confirmText: 'Delete',
        confirmColor: '#C96E5F',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: 'Account scheduled for deletion', icon: 'none' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.security {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

/* 安全状态概览卡片 */
.status-card {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
}

.status-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: rgba(171, 185, 173, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-shield {
  font-size: 40rpx;
}

.status-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.status-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.status-badge-safe {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: rgba(171, 185, 173, 0.15);
  color: var(--color-success);
}

.status-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.status-hint {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 区块 */
.section {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.section-label {
  display: block;
  padding: 20rpx 24rpx 8rpx;
  font-size: 22rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-tertiary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

/* 行项目 */
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 104rpx;
  padding: 0 24rpx;
}

.row-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex: 1;
  min-width: 0;
}

.row-icon {
  font-size: 40rpx;
  width: 40rpx;
  text-align: center;
  flex-shrink: 0;
}

.row-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.row-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.row-value {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.row-arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

/* 分隔线 */
.divider {
  height: 1rpx;
  background: var(--color-divider);
  margin-left: 80rpx;
}

/* 徽章 */
.badge-safe {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: rgba(171, 185, 173, 0.15);
  color: var(--color-success);
}

.badge-unlinked {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

.btn-bind {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8rpx 24rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: var(--color-primary);
  color: var(--color-text);
  line-height: 1;
}

/* Toggle 开关 */
.toggle {
  position: relative;
  width: 102rpx;
  height: 62rpx;
  border-radius: 999rpx;
  background: var(--color-divider);
  cursor: pointer;
  transition: background-color 0.2s ease;
  flex-shrink: 0;
}

.toggle.active {
  background: var(--color-primary);
}

.toggle-knob {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 54rpx;
  height: 54rpx;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s cubic-bezier(0.32, 0.72, 0, 1);
}

.toggle.active .toggle-knob {
  transform: translateX(40rpx);
}

/* 注销账号 */
.delete-section {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 104rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-top: 8rpx;
}

.delete-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-danger);
}
</style>
