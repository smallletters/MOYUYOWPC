<template>
  <view class="change-pwd">
    <view class="header">
      <text class="title">Change Password</text>
      <text class="sub">Update your login password</text>
    </view>

    <view class="form">
      <view class="input-group">
        <text class="input-label">Current Password</text>
        <input
          v-model="oldPassword"
          class="input"
          type="password"
          placeholder="Enter current password"
        >
      </view>

      <view class="input-group">
        <text class="input-label">New Password</text>
        <input
          v-model="newPassword"
          class="input"
          type="password"
          placeholder="At least 8 characters, letters and numbers"
        >
      </view>

      <view class="input-group">
        <text class="input-label">Confirm New Password</text>
        <input
          v-model="confirmPassword"
          class="input"
          type="password"
          placeholder="Re-enter new password"
        >
      </view>

      <view class="password-rules">
        <text class="rule" :class="{ met: hasLower }">● One lowercase letter</text>
        <text class="rule" :class="{ met: hasUpper }">● One uppercase letter</text>
        <text class="rule" :class="{ met: hasDigit }">● One number</text>
        <text class="rule" :class="{ met: hasMinLen }">● At least 8 characters</text>
        <text class="rule" :class="{ met: passwordsMatch }">● Passwords match</text>
      </view>

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canSubmit }" @click="onChange">
        Update Password
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
    }
  },

  computed: {
    hasLower() {
      return /[a-z]/.test(this.newPassword)
    },
    hasUpper() {
      return /[A-Z]/.test(this.newPassword)
    },
    hasDigit() {
      return /\d/.test(this.newPassword)
    },
    hasMinLen() {
      return this.newPassword.length >= 8
    },
    passwordsMatch() {
      return this.newPassword.length > 0 && this.newPassword === this.confirmPassword
    },
    canSubmit() {
      return (
        this.oldPassword.length >= 8 &&
        this.hasLower &&
        this.hasUpper &&
        this.hasDigit &&
        this.hasMinLen &&
        this.passwordsMatch
      )
    },
    userStore() {
      return useUserStore()
    },
  },

  methods: {
    async onChange() {
      if (!this.canSubmit) {
        uni.showToast({ title: '请检查密码规则', icon: 'none' })
        return
      }
      try {
        await this.userStore.changePassword(this.oldPassword, this.newPassword)
        uni.showToast({ title: 'Password updated', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.showToast({ title: e.message || 'Failed to update password', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.change-pwd {
  min-height: 100vh;
  background: var(--color-background);
  padding: 64rpx 48rpx;
  padding-top: calc(64rpx + env(safe-area-inset-top));
}

.header {
  margin-bottom: 64rpx;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: var(--font-weight-semibold);
  margin-bottom: 8rpx;
}

.sub {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.input-group {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
}

.input-label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 8rpx;
}

.input {
  display: block;
  width: 100%;
  font-size: var(--font-size-base);
}

.password-rules {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 16rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
}

.rule {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.rule.met {
  color: var(--color-success);
}

.submit-btn {
  padding: 28rpx 0;
  font-size: var(--font-size-md);
}

.submit-btn.disabled {
  opacity: 0.5;
}
</style>
