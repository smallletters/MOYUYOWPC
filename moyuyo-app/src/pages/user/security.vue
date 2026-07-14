<template>
  <view class="security">
    <view class="card">
      <view class="row">
        <text class="label">Login Password</text>
        <text class="action" @click="onChangePassword">Change ›</text>
      </view>
      <view class="row">
        <text class="label">2-Factor Authentication</text>
        <switch :checked="tfaEnabled" color="#DBC98A" @change="onToggleTFA" />
      </view>
      <view class="row">
        <text class="label">Trusted Devices</text>
        <text class="action" @click="goDevices">Manage ›</text>
      </view>
    </view>

    <view class="card">
      <view class="card-title">Third-Party Accounts</view>
      <view v-for="acc in socialAccounts" :key="acc.id" class="row">
        <text class="label">{{ acc.name }}</text>
        <text v-if="acc.linked" class="linked">Linked</text>
        <text v-else class="action" @click="onLink(acc)">Link ›</text>
      </view>
    </view>

    <view class="card danger-card">
      <view class="row" @click="onDeleteAccount">
        <text class="danger-text">Delete Account</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="card export-card">
      <view class="row" @click="onExportData">
        <text>Export My Data</text>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      tfaEnabled: false,
      socialAccounts: [
        { id: 1, name: 'Apple ID', linked: true },
        { id: 2, name: 'Google', linked: true },
        { id: 3, name: 'Facebook', linked: false }
      ]
    }
  },

  methods: {
    onChangePassword() {
      uni.navigateTo({ url: '/pages/user/change-password' })
    },

    onToggleTFA(e) {
      this.tfaEnabled = e.detail.value
      uni.showToast({
        title: this.tfaEnabled ? '2FA Enabled' : '2FA Disabled',
        icon: 'none'
      })
    },

    goDevices() {
      uni.navigateTo({ url: '/pages/user/devices' })
    },

    onLink(acc) {
      uni.showToast({ title: `${acc.name} linking...`, icon: 'none' })
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
        }
      })
    },

    onExportData() {
      uni.showLoading({ title: 'Generating...' })
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({ title: 'Data export will be emailed', icon: 'success' })
      }, 1500)
    }
  }
}
</script>

<style lang="scss" scoped>
.security {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: 16rpx;
  padding: 0 24rpx;
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  padding: 24rpx 0 16rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.row:last-child {
  border-bottom: none;
}

.label {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.action {
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
}

.linked {
  font-size: var(--font-size-sm);
  color: var(--color-success);
}

.arrow {
  color: var(--color-text-tertiary);
}

.danger-text {
  color: var(--color-danger);
  font-size: var(--font-size-base);
}

.export-card .row {
  color: var(--color-primary-dark);
}
</style>
