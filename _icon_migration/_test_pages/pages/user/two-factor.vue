<template>
  <view class="two-factor">
    <view class="header">
      <text class="title">Two-Factor Authentication</text>
      <text class="sub">Enter the 6-digit code sent to your phone</text>
    </view>

    <view class="form">
      <view class="code-inputs">
        <input
          v-for="(_, i) in 6"
          :key="i"
          :ref="'code_' + i"
          class="code-digit"
          :class="{ filled: code[i] }"
          type="number"
          :value="code[i] || ''"
          maxlength="1"
          @input="onDigitInput(i, $event)"
          @backspace="onDigitBackspace(i)"
        >
      </view>

      <view
        class="btn btn-primary verify-btn"
        :class="{ disabled: !codeComplete }"
        @click="onVerify"
      >
        Verify
      </view>

      <view class="resend" @click="onResend">
        {{ cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend Code' }}
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      code: [],
      cooldown: 0,
      timer: null,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    codeComplete() {
      return this.code.filter((c) => c).length === 6
    },
  },

  onLoad() {
    this.sendCode()
  },

  beforeUnmount() {
    if (this.timer) clearInterval(this.timer)
  },

  methods: {
    async sendCode() {
      try {
        await this.userStore.sendTwoFactorCode()
        uni.showToast({ title: 'Code sent to your phone', icon: 'success' })
        this.startCooldown()
      } catch (e) {
        uni.showToast({ title: e.message || 'Failed to send code', icon: 'none' })
      }
    },

    onDigitInput(index, e) {
      const val = e.detail.value
      this.code[index] = val.slice(-1)
      if (val && index < 5) {
        const nextRef = this.$refs['code_' + (index + 1)]
        if (nextRef && nextRef[0]) nextRef[0].focus()
      }
    },

    onDigitBackspace(index) {
      if (!this.code[index] && index > 0) {
        const prevRef = this.$refs['code_' + (index - 1)]
        if (prevRef && prevRef[0]) prevRef[0].focus()
      }
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

    async onVerify() {
      if (!this.codeComplete) return
      const fullCode = this.code.join('')
      try {
        await this.userStore.verifyTwoFactorCode(fullCode)
        uni.showToast({ title: 'Verified', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.showToast({ title: e.message || 'Invalid code', icon: 'none' })
        this.code = []
      }
    },

    onResend() {
      if (this.cooldown > 0) return
      this.code = []
      this.sendCode()
    },
  },
}
</script>

<style lang="scss" scoped>
.two-factor {
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
  align-items: center;
  gap: 32rpx;
}

.code-inputs {
  display: flex;
  gap: 16rpx;
  justify-content: center;
}

.code-digit {
  width: 80rpx;
  height: 100rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  text-align: center;
  font-size: 48rpx;
  font-weight: var(--font-weight-semibold);
  border: 2rpx solid var(--color-divider);
}

.code-digit.filled {
  border-color: var(--color-primary);
}

.verify-btn {
  width: 100%;
  padding: 28rpx 0;
  font-size: var(--font-size-md);
}

.verify-btn.disabled {
  opacity: 0.5;
}

.resend {
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
  padding: 16rpx 0;
}
</style>
