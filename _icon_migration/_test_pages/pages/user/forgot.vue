<template>
  <view class="forgot">
    <view class="header">
      <text class="title">Reset Password</text>
      <text class="sub">
        {{
          step === 1
            ? 'Enter your email to receive a reset link'
            : 'Enter the code and set a new password'
        }}
      </text>
    </view>

    <view v-if="step === 1" class="form">
      <view class="input-group">
        <text class="input-label">Email</text>
        <input
          v-model="email"
          class="input"
          type="text"
          placeholder="your@email.com">
      </view>

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canSend }" @click="onSendCode">
        Send Reset Code
      </view>

      <view class="back-link" @click="goBack">Back to Sign In</view>
    </view>

    <view v-else class="form">
      <view class="input-group">
        <text class="input-label">Verification Code</text>
        <input
          v-model="code"
          class="input"
          type="text"
          placeholder="6-digit code"
          maxlength="6">
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
        <text class="input-label">Confirm Password</text>
        <input
          v-model="confirmPassword"
          class="input"
          type="password"
          placeholder="Re-enter new password"
        >
      </view>

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canReset }" @click="onReset">
        Reset Password
      </view>

      <view class="back-link" @click="step = 1">Back to email</view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      step: 1,
      email: '',
      code: '',
      newPassword: '',
      confirmPassword: '',
    }
  },

  computed: {
    canSend() {
      return this.email.includes('@')
    },
    canReset() {
      const pwdOk = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(this.newPassword)
      return this.code.length === 6 && pwdOk && this.newPassword === this.confirmPassword
    },
    userStore() {
      return useUserStore()
    },
  },

  methods: {
    async onSendCode() {
      if (!this.canSend) return
      try {
        await this.userStore.forgotPassword(this.email)
        uni.showToast({ title: 'Reset code sent to your email', icon: 'success' })
        this.step = 2
      } catch (e) {
        uni.showToast({ title: e.message || 'Failed to send code', icon: 'none' })
      }
    },

    async onReset() {
      if (!this.canReset) return
      try {
        await this.userStore.resetPassword(this.code, this.newPassword)
        uni.showToast({ title: 'Password reset successfully', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.showToast({ title: e.message || 'Reset failed', icon: 'none' })
      }
    },

    goBack() {
      uni.navigateBack()
    },
  },
}
</script>

<style lang="scss" scoped>
.forgot {
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
  line-height: 1.6;
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

.submit-btn {
  padding: 28rpx 0;
  font-size: var(--font-size-md);
}

.submit-btn.disabled {
  opacity: 0.5;
}

.back-link {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
  margin-top: 16rpx;
}
</style>
