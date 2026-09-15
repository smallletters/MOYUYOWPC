<template>
  <view class="forgot">
    <view class="header">
      <text class="title">{{ $t('forgot.title') }}</text>
      <text class="sub">
        {{ step === 1 ? $t('forgot.subStep1') : $t('forgot.subStep2') }}
      </text>
    </view>

    <view v-if="step === 1" class="form">
      <view class="input-group">
        <text class="input-label">{{ $t('forgot.email') }}</text>
        <input
          v-model="email"
          class="input"
          type="text"
          :placeholder="$t('forgot.emailPlaceholder')"
        >
      </view>

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canSend }" @click="onSendCode">
        {{ $t('forgot.sendCode') }}
      </view>

      <view class="back-link" @click="goBack">{{ $t('forgot.backToSignIn') }}</view>
    </view>

    <view v-else class="form">
      <view class="input-group">
        <text class="input-label">{{ $t('forgot.code') }}</text>
        <input
          v-model="code"
          class="input"
          type="text"
          :placeholder="$t('forgot.codePlaceholder')"
          maxlength="6"
        >
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('forgot.newPassword') }}</text>
        <input
          v-model="newPassword"
          class="input"
          type="password"
          :placeholder="$t('forgot.passwordPlaceholder')"
        >
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('forgot.confirmPassword') }}</text>
        <input
          v-model="confirmPassword"
          class="input"
          type="password"
          :placeholder="$t('forgot.confirmPlaceholder')"
        >
      </view>

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canReset }" @click="onReset">
        {{ $t('forgot.reset') }}
      </view>

      <view class="back-link" @click="step = 1">{{ $t('forgot.backToEmail') }}</view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userForgot',

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
        uni.showToast({ title: i18n.t('forgot.codeSent'), icon: 'success' })
        this.step = 2
      } catch (e) {
        uni.showToast({ title: e.message || i18n.t('forgot.sendFailed'), icon: 'none' })
      }
    },

    async onReset() {
      if (!this.canReset) return
      try {
        await this.userStore.resetPassword(this.code, this.newPassword)
        uni.showToast({ title: i18n.t('forgot.resetSuccess'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.showToast({ title: e.message || i18n.t('forgot.resetFailed'), icon: 'none' })
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
  padding-top: calc(64rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
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
