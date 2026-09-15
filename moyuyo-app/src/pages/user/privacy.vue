<template>
  <view class="privacy">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('privacy.title') }}</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <view class="privacy-overview">
        <view class="overview-icon">
          <text class="shield-icon luc-shield" />
        </view>
        <view class="overview-info">
          <text class="overview-title">{{ $t('privacy.overviewTitle') }}</text>
          <text class="overview-subtitle">{{ $t('privacy.overviewSubtitle') }}</text>
        </view>
      </view>

      <view class="section">
        <text class="section-title">{{ $t('privacy.itemsTitle') }}</text>
        <view class="section-body">
          <view v-for="item in toggleSettings" :key="item.key" class="setting-item">
            <text class="setting-label">{{ $t('privacy.' + item.key) }}</text>
            <view class="toggle" :class="{ active: item.value }" @click="toggleSwitch(item)">
              <view class="toggle-thumb" />
            </view>
          </view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">{{ $t('privacy.dataTitle') }}</text>
        <view class="section-body">
          <view class="action-item" @click="onExportData">
            <view class="action-left">
              <text class="action-icon">↓</text>
              <text class="action-label">{{ $t('privacy.exportData') }}</text>
            </view>
            <view class="action-right">
              <text class="action-hint">PDF/JSON</text>
              <text class="action-arrow luc-chevron-right" />
            </view>
          </view>
          <view class="item-divider" />
          <view class="action-item" @click="onDeleteAccount">
            <view class="action-left">
              <text class="action-icon del luc-x" />
              <text class="action-label del">{{ $t('privacy.deleteAccount') }}</text>
            </view>
            <text class="action-arrow luc-chevron-right" />
          </view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">{{ $t('privacy.policyName') }}</text>
        <view class="section-body">
          <view class="action-item" @click="onViewPolicy">
            <text class="action-label">{{ $t('privacy.policyName') }}</text>
            <text class="action-arrow luc-chevron-right" />
          </view>
        </view>
      </view>

      <text class="footer-note">{{ $t('privacy.footerNote') }}</text>
    </view>
  </view>
</template>

<script>
import { userApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userPrivacy',

  data() {
    return {
      // 隐私开关项（label 由模板按 key 取 i18n 文案）
      toggleSettings: [
        { key: 'publicFavorites', value: true },
        { key: 'allowViewProfile', value: true },
        { key: 'showOnlineStatus', value: false },
        { key: 'allowMessages', value: true },
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
        uni.showToast({
          title: newValue ? i18n.t('privacy.enabled') : i18n.t('privacy.disabled'),
          icon: 'none',
        })
      } catch {
        uni.showToast({ title: i18n.t('privacy.toggleFailed'), icon: 'none' })
      }
    },

    async onExportData() {
      uni.showModal({
        title: i18n.t('privacy.exportModalTitle'),
        content: i18n.t('privacy.exportModalContent'),
        success: async (res) => {
          if (res.confirm) {
            try {
              uni.showLoading({ title: i18n.t('privacy.exporting') })
              await userApi.updateUser({ exportData: true })
              uni.hideLoading()
              uni.showToast({ title: i18n.t('privacy.exportSubmitted'), icon: 'success' })
            } catch {
              uni.hideLoading()
              uni.showToast({ title: i18n.t('privacy.exportFailed'), icon: 'none' })
            }
          }
        },
      })
    },

    async onDeleteAccount() {
      uni.showModal({
        title: i18n.t('privacy.deleteModalTitle'),
        content: i18n.t('privacy.deleteModalContent'),
        confirmText: i18n.t('privacy.deleteConfirmText'),
        confirmColor: '#ff3b30',
        success: async (res) => {
          if (res.confirm) {
            try {
              uni.showLoading({ title: i18n.t('privacy.deleting') })
              await userApi.updateUser({ deleteAccount: true })
              uni.hideLoading()
              uni.showToast({ title: i18n.t('privacy.deleteSubmitted'), icon: 'success' })
            } catch {
              uni.hideLoading()
              uni.showToast({ title: i18n.t('privacy.operationFailed'), icon: 'none' })
            }
          }
        },
      })
    },

    onViewPolicy() {
      uni.showToast({ title: i18n.t('privacy.policyName'), icon: 'none' })
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
