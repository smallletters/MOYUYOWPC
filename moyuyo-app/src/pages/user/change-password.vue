<template>
  <view class="change-pwd">
    <view class="header">
      <text class="title">{{ $t('changePassword.title') }}</text>
      <text class="sub">{{ $t('changePassword.sub') }}</text>
    </view>

    <view class="form">
      <view class="input-group">
        <text class="input-label">{{ $t('changePassword.oldPassword') }}</text>
        <input
          v-model="oldPassword"
          class="input"
          type="password"
          :placeholder="$t('changePassword.oldRequired')"
        >
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('changePassword.newPassword') }}</text>
        <input
          v-model="newPassword"
          class="input"
          type="password"
          :placeholder="$t('changePassword.newPlaceholder')"
        >
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('changePassword.confirmPassword') }}</text>
        <input
          v-model="confirmPassword"
          class="input"
          type="password"
          :placeholder="$t('changePassword.confirmRequired')"
        >
      </view>

      <view class="password-rules">
        <text class="rule" :class="{ met: hasLower }">● {{ $t('changePassword.ruleLower') }}</text>
        <text class="rule" :class="{ met: hasUpper }">● {{ $t('changePassword.ruleUpper') }}</text>
        <text class="rule" :class="{ met: hasDigit }">● {{ $t('changePassword.ruleDigit') }}</text>
        <text class="rule" :class="{ met: hasMinLen }">
          ● {{ $t('changePassword.ruleMinLen') }}
        </text>
        <text class="rule" :class="{ met: passwordsMatch }">
          ● {{ $t('changePassword.ruleMatch') }}
        </text>
      </view>

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canSubmit }" @click="onChange">
        {{ $t('changePassword.updatePassword') }}
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userChangePassword',

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
        uni.showToast({ title: i18n.t('changePassword.checkRules'), icon: 'none' })
        return
      }
      try {
        await this.userStore.changePassword(this.oldPassword, this.newPassword)
        uni.showToast({ title: i18n.t('changePassword.submitted'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.showToast({ title: e.message || i18n.t('changePassword.failed'), icon: 'none' })
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
