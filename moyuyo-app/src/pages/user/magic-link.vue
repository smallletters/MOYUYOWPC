<template>
  <view class="magic-link">
    <view class="header">
      <text class="title">Magic Link</text>
      <text class="sub">
        {{
          step === 1
            ? "Enter your email and we'll send a sign-in link"
            : 'Check your email for the sign-in link'
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

      <view class="btn btn-primary submit-btn" :class="{ disabled: !canSend }" @click="onSend">
        Send Magic Link
      </view>

      <view class="back-link" @click="goBack">Back to Sign In</view>
    </view>

    <view v-else class="form">
      <view class="sent-info">
        <text class="sent-icon"><text class="luc luc-mail" /></text>
        <text class="sent-text">We sent a sign-in link to</text>
        <text class="sent-email">{{ email }}</text>
        <text class="sent-hint">
          The link expires in 15 minutes. If you don't see it, check your spam folder.
        </text>
      </view>

      <view class="resend" @click="onResend">
        {{ cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend Magic Link' }}
      </view>

      <view class="back-link" @click="goBack">Back to Sign In</view>
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
      cooldown: 0,
      timer: null,
    }
  },

  computed: {
    canSend() {
      return this.email.includes('@')
    },
    userStore() {
      return useUserStore()
    },
  },

  beforeUnmount() {
    if (this.timer) clearInterval(this.timer)
  },

  methods: {
    async onSend() {
      if (!this.canSend) return
      try {
        await this.userStore.sendMagicLink(this.email)
        this.step = 2
        this.startCooldown()
      } catch (e) {
        uni.showToast({ title: e.message || 'Failed to send', icon: 'none' })
      }
    },

    async onResend() {
      if (this.cooldown > 0) return
      await this.onSend()
    },

    startCooldown() {
      this.cooldown = 60
      this.timer = setInterval(() => {
        this.cooldown -= 1
        if (this.cooldown <= 0) {
          clearInterval(this.timer)
          this.timer = null
        }
      }, 1000)
    },

    goBack() {
      if (this.timer) clearInterval(this.timer)
      uni.navigateBack()
    },
  },
}
</script>

<style lang="scss" scoped>
.magic-link {
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

.sent-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 48rpx 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
}

.sent-icon {
  font-size: 64rpx;
}

.sent-text {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.sent-email {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.sent-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  text-align: center;
  line-height: 1.6;
}

.resend {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
  padding: 16rpx 0;
}
</style>
