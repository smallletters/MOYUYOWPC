<template>
  <view class="register">
    <view class="header">
      <text class="title">Create Account</text>
      <text class="sub">Join MOYUYO community</text>
    </view>

    <view class="form">
      <view class="input-group">
        <text class="input-label">Email</text>
        <input v-model="email" class="input" type="text" placeholder="your@email.com" />
      </view>

      <view class="input-group">
        <text class="input-label">Password</text>
        <input
          v-model="password"
          class="input"
          type="password"
          placeholder="At least 8 characters, letters and numbers"
        />
      </view>

      <view class="input-group">
        <text class="input-label">First Name</text>
        <input v-model="firstName" class="input" type="text" placeholder="Your first name" />
      </view>

      <view class="input-group">
        <text class="input-label">Last Name</text>
        <input v-model="lastName" class="input" type="text" placeholder="Your last name" />
      </view>

      <view class="input-group">
        <text class="input-label">Birthday</text>
        <picker mode="date" :value="birthday" start="1900-01-01" :end="today" @change="onBirthdayChange">
          <view class="input picker">{{ birthday || 'Select your birthday' }}</view>
        </picker>
      </view>

      <!-- GDPR 同意 -->
      <view class="agreements">
        <view class="agreement" @click="agreed.privacy = !agreed.privacy">
          <view class="checkbox" :class="{ checked: agreed.privacy }">
            <text v-if="agreed.privacy">✓</text>
          </view>
          <text>I agree to the <text class="link">Privacy Policy</text></text>
        </view>
        <view class="agreement" @click="agreed.terms = !agreed.terms">
          <view class="checkbox" :class="{ checked: agreed.terms }">
            <text v-if="agreed.terms">✓</text>
          </view>
          <text>I agree to the <text class="link">Terms of Service</text></text>
        </view>
        <view class="agreement" @click="agreed.marketing = !agreed.marketing">
          <view class="checkbox" :class="{ checked: agreed.marketing }">
            <text v-if="agreed.marketing">✓</text>
          </view>
          <text>Subscribe to MOYUYO marketing emails (optional)</text>
        </view>
      </view>

      <view
        class="btn btn-primary submit-btn"
        :class="{ disabled: !canSubmit }"
        @click="onRegister"
      >
        Create Account
      </view>

      <view class="login-link">
        Already have an account?
        <text class="link" @click="goLogin">Sign In</text>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      birthday: '',
      agreed: {
        privacy: false,
        terms: false,
        marketing: false
      }
    }
  },

  computed: {
    today() {
      return new Date().toISOString().split('T')[0]
    },
    canSubmit() {
      return (
        this.email.includes('@') &&
        this.password.length >= 8 &&
        this.firstName &&
        this.lastName &&
        this.birthday &&
        this.agreed.privacy &&
        this.agreed.terms
      )
    },
    userStore() {
      return useUserStore()
    }
  },

  methods: {
    onBirthdayChange(e) {
      this.birthday = e.detail.value
      // 年龄校验
      const age = this.calcAge(this.birthday)
      if (age < 13) {
        uni.showModal({
          title: 'Sorry',
          content: 'You must be at least 13 years old to register.',
          showCancel: false
        })
        this.birthday = ''
      }
    },

    calcAge(birthday) {
      const birth = new Date(birthday)
      const now = new Date()
      let age = now.getFullYear() - birth.getFullYear()
      const m = now.getMonth() - birth.getMonth()
      if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age -= 1
      return age
    },

    async onRegister() {
      if (!this.canSubmit) {
        uni.showToast({ title: '请完整填写信息并同意协议', icon: 'none' })
        return
      }
      try {
        // 强度校验
        const strongPwd = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(this.password)
        if (!strongPwd) {
          uni.showToast({ title: '密码需含大小写字母+数字', icon: 'none' })
          return
        }
        await this.userStore.register({
          email: this.email,
          password: this.password,
          first_name: this.firstName,
          last_name: this.lastName,
          username: this.email
        })
        uni.showToast({ title: '注册成功，请登录', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.showToast({ title: e.message || 'Registration failed', icon: 'none' })
      }
    },

    goLogin() {
      uni.navigateBack()
    }
  }
}
</script>

<style lang="scss" scoped>
.register {
  min-height: 100vh;
  background: var(--color-background);
  padding: 48rpx 32rpx;
  padding-top: calc(48rpx + env(safe-area-inset-top));
}

.header {
  margin-bottom: 48rpx;
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
  gap: 16rpx;
}

.input-group {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16rpx 24rpx;
}

.input-label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 4rpx;
}

.input {
  display: block;
  width: 100%;
  font-size: var(--font-size-base);
}

.picker {
  min-height: 44rpx;
}

.agreements {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin: 16rpx 0;
}

.agreement {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.checkbox {
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20rpx;
  flex-shrink: 0;
}

.checkbox.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.link {
  color: var(--color-primary-dark);
}

.submit-btn {
  padding: 24rpx 0;
  font-size: var(--font-size-md);
}

.submit-btn.disabled {
  opacity: 0.5;
}

.login-link {
  text-align: center;
  margin-top: 24rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
</style>
