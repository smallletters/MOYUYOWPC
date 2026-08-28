<template>
  <view class="privacy">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">隐私设置</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <view class="privacy-overview">
        <view class="overview-icon">
          <text class="shield-icon"><text class="luc luc-shield" /></text>
        </view>
        <view class="overview-info">
          <text class="overview-title">您的隐私保护等级：高</text>
          <text class="overview-subtitle">您可以随时管理您的隐私设置</text>
        </view>
      </view>

      <view class="section">
        <text class="section-title">隐私项目</text>
        <view class="section-body">
          <view v-for="item in toggleSettings" :key="item.key" class="setting-item">
            <text class="setting-label">{{ item.label }}</text>
            <view class="toggle" :class="{ active: item.value }" @click="toggleSwitch(item)">
              <view class="toggle-thumb" />
            </view>
          </view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">数据管理</text>
        <view class="section-body">
          <view class="action-item" @click="onExportData">
            <view class="action-left">
              <text class="action-icon">↓</text>
              <text class="action-label">导出我的数据</text>
            </view>
            <view class="action-right">
              <text class="action-hint">PDF/JSON</text>
              <text class="action-arrow"><text class="luc luc-chevron-right" /></text>
            </view>
          </view>
          <view class="item-divider" />
          <view class="action-item" @click="onDeleteAccount">
            <view class="action-left">
              <text class="action-icon del"><text class="luc luc-x" /></text>
              <text class="action-label del">删除账号</text>
            </view>
            <text class="action-arrow"><text class="luc luc-chevron-right" /></text>
          </view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">隐私政策</text>
        <view class="section-body">
          <view class="action-item" @click="onViewPolicy">
            <text class="action-label">隐私政策</text>
            <text class="action-arrow"><text class="luc luc-chevron-right" /></text>
          </view>
        </view>
      </view>

      <text class="footer-note">最后更新于 2026-07-01 · 符合 GDPR & CCPA 规范</text>
    </view>
  </view>
</template>

<script>
import { userApi } from '@/api'

export default {
  data() {
    return {
      toggleSettings: [
        { key: 'publicFavorites', label: '公开我的收藏', value: true },
        { key: 'allowViewProfile', label: '允许他人查看主页', value: true },
        { key: 'showOnlineStatus', label: '显示在线状态', value: false },
        { key: 'allowMessages', label: '允许私信', value: true },
      ],
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async toggleSwitch(item) {
      const newValue = !item.value
      try {
        await userApi.updateUser({ [item.key]: newValue })
        item.value = newValue
        uni.showToast({ title: newValue ? '已开启' : '已关闭', icon: 'none' })
      } catch {
        uni.showToast({ title: '设置失败，请重试', icon: 'none' })
      }
    },

    async onExportData() {
      uni.showModal({
        title: '导出数据',
        content: '我们将为您生成包含个人资料、订单记录等数据的文件，发送至您的邮箱。确定要导出吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              uni.showLoading({ title: '正在导出...' })
              await userApi.updateUser({ exportData: true })
              uni.hideLoading()
              uni.showToast({ title: '导出请求已提交，请查收邮件', icon: 'success' })
            } catch {
              uni.hideLoading()
              uni.showToast({ title: '导出失败，请重试', icon: 'none' })
            }
          }
        },
      })
    },

    async onDeleteAccount() {
      uni.showModal({
        title: '确认删除',
        content: '删除账号后，所有数据将被永久清除且无法恢复。确定要继续吗？',
        confirmText: '确认删除',
        confirmColor: '#ff3b30',
        success: async (res) => {
          if (res.confirm) {
            try {
              uni.showLoading({ title: '处理中...' })
              await userApi.updateUser({ deleteAccount: true })
              uni.hideLoading()
              uni.showToast({ title: '账号删除申请已提交', icon: 'success' })
            } catch {
              uni.hideLoading()
              uni.showToast({ title: '操作失败，请重试', icon: 'none' })
            }
          }
        },
      })
    },

    onViewPolicy() {
      uni.showToast({ title: '隐私政策', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.privacy {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn:last-child {
  left: auto;
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.content {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.privacy-overview {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx;
  background: #e9f9ee;
  border: 1rpx solid #34c759;
  border-radius: var(--radius-md);
}

.overview-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #34c759;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shield-icon {
  font-size: 40rpx;
  line-height: 1;
}

.overview-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.overview-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.overview-subtitle {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.section-title {
  padding-left: 8rpx;
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.section-body {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 28rpx;
  border-bottom: 1rpx solid var(--color-divider);

  &:last-child {
    border-bottom: none;
  }
}

.setting-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.toggle {
  position: relative;
  width: 88rpx;
  height: 52rpx;
  border-radius: 26rpx;
  background: var(--color-divider);
  transition: background-color 0.2s ease;
  flex-shrink: 0;
  cursor: pointer;

  &.active {
    background: var(--color-primary);
  }
}

.toggle-thumb {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s ease;

  .active & {
    transform: translateX(36rpx);
  }
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 28rpx;
  border-bottom: 1rpx solid var(--color-divider);

  &:last-child {
    border-bottom: none;
  }
}

.action-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.action-icon {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
  width: 40rpx;
  text-align: center;

  &.del {
    color: var(--color-danger);
  }
}

.action-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);

  &.del {
    color: var(--color-danger);
  }
}

.action-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.action-hint {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.action-arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.item-divider {
  height: 1rpx;
  background: var(--color-divider);
  margin-left: 88rpx;
}

.footer-note {
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
