<template>
  <view class="login">
    <view class="bg-overlay" />

    <view class="login-card">
      <view class="brand">
        <text class="brand-name">MOYUYO</text>
        <text class="brand-tagline">A Better Life for Your Pet</text>
      </view>

      <view class="tabs">
        <view
          v-for="t in tabs"
          :key="t.value"
          class="tab"
          :class="{ active: activeTab === t.value }"
          @click="activeTab = t.value"
        >
          {{ t.label }}
        </view>
      </view>

      <!-- Phone Login Form -->
      <view v-show="activeTab === 'phone'" class="form">
        <view class="input-row">
          <view class="country-code" @click="showCountryPicker = true">
            <text>{{ countryCode }}</text>
            <text class="arrow-down">▾</text>
          </view>
          <input
            v-model="phone"
            class="input phone-input"
            type="number"
            maxlength="10"
            placeholder="Phone number"
            inputmode="numeric"
          >
        </view>

        <view class="input-row code-row">
          <input
            v-model="smsCode"
            class="input"
            type="number"
            maxlength="6"
            placeholder="Verification code"
          >
          <view class="send-code" :class="{ disabled: codeCountdown > 0 }" @click="onSendCode">
            <text v-if="codeCountdown === 0">Send Code</text>
            <text v-else>{{ codeCountdown }}s</text>
          </view>
        </view>
      </view>

      <!-- Email Login Form -->
      <view v-show="activeTab === 'email'" class="form">
        <view class="input-group">
          <input
            v-model="email"
            class="input"
            type="text"
            placeholder="Email address">
        </view>
        <view class="input-group">
          <input
            v-model="password"
            class="input"
            :type="showPassword ? 'text' : 'password'"
            placeholder="Password"
          >
          <text class="toggle-pwd" @click="showPassword = !showPassword">
            {{ showPassword ? 'Hide' : 'Show' }}
          </text>
        </view>
      </view>

      <view class="btn btn-primary login-btn" :class="{ disabled: !canSubmit }" @click="onLogin">
        Sign In
      </view>

      <view class="divider">
        <view class="line" />
        <text class="or">or continue with</text>
        <view class="line" />
      </view>

      <view class="social-buttons">
        <view class="social-btn" @click="onSocial('google')">
          <image src="/static/icons/google.svg" class="social-icon-img" />
        </view>
        <view class="social-btn" @click="onSocial('apple')">
          <text class="social-icon-text"></text>
        </view>
        <view class="social-btn" @click="onSocialMore">
          <text class="social-icon-text">···</text>
        </view>
      </view>

      <view class="register-link">
        Don't have an account?
        <text class="link" @click="goRegister">Sign Up</text>
      </view>
    </view>

    <view
      v-if="showCountryPicker"
      class="country-picker-overlay"
      @click="showCountryPicker = false"
    >
      <view class="country-picker" @click.stop>
        <text class="picker-title">Select Country</text>
        <scroll-view scroll-y class="country-list">
          <view
            v-for="c in countries"
            :key="c.code"
            class="country-item"
            @click="selectCountry(c)">
            <text>{{ c.flag }} {{ c.name }}</text>
            <text class="country-dial">{{ c.dial }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      activeTab: 'phone',
      tabs: [
        { value: 'phone', label: 'Phone' },
        { value: 'email', label: 'Email' },
      ],
      phone: '',
      email: '',
      password: '',
      smsCode: '',
      showPassword: false,
      codeCountdown: 0,
      codeTimer: null,
      countryCode: '+1',
      showCountryPicker: false,
      countries: [
        { code: 'US', name: 'United States', dial: '+1', flag: '🇺🇸' },
        { code: 'CA', name: 'Canada', dial: '+1', flag: '🇨🇦' },
        { code: 'GB', name: 'United Kingdom', dial: '+44', flag: '🇬🇧' },
        { code: 'AU', name: 'Australia', dial: '+61', flag: '🇦🇺' },
        { code: 'DE', name: 'Germany', dial: '+49', flag: '🇩🇪' },
        { code: 'FR', name: 'France', dial: '+33', flag: '🇫🇷' },
        { code: 'JP', name: 'Japan', dial: '+81', flag: '🇯🇵' },
        { code: 'KR', name: 'South Korea', dial: '+82', flag: '🇰🇷' },
        { code: 'SG', name: 'Singapore', dial: '+65', flag: '🇸🇬' },
        { code: 'CN', name: 'China', dial: '+86', flag: '🇨🇳' },
      ],
    }
  },

  computed: {
    canSubmit() {
      if (this.activeTab === 'phone') {
        return this.phone.length >= 8 && this.smsCode.length >= 4
      }
      return this.email.includes('@') && this.password.length >= 8
    },
    userStore() {
      return useUserStore()
    },
  },

  methods: {
    async onLogin() {
      if (!this.canSubmit) return
      uni.showLoading({ title: 'Signing in...', mask: true })
      try {
        const credentials =
          this.activeTab === 'phone'
            ? { phone: this.countryCode + this.phone, code: this.smsCode }
            : { username: this.email, password: this.password }
        const result = await this.userStore.login(credentials)
        uni.hideLoading()
        if (result?.requiresTwoFactor) {
          uni.navigateTo({ url: '/pages/user/two-factor' })
          return
        }
        uni.showToast({ title: 'Welcome back!', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home' }), 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || 'Login failed', icon: 'none' })
      }
    },

    onSendCode() {
      if (this.codeCountdown > 0) return
      if (this.phone.length < 8) {
        uni.showToast({ title: 'Enter phone number', icon: 'none' })
        return
      }
      this.codeCountdown = 60
      this.codeTimer = setInterval(() => {
        this.codeCountdown -= 1
        if (this.codeCountdown <= 0) clearInterval(this.codeTimer)
      }, 1000)
      uni.showToast({ title: 'Code sent!', icon: 'success' })
    },

    selectCountry(c) {
      this.countryCode = c.dial
      this.showCountryPicker = false
    },

    onSocial(provider) {
      uni.showToast({ title: `${provider} login coming soon`, icon: 'none' })
    },

    onSocialMore() {
      uni.showToast({ title: 'More options coming soon', icon: 'none' })
    },

    onForgot() {
      uni.navigateTo({ url: '/pages/user/forgot' })
    },

    goRegister() {
      uni.navigateTo({ url: '/pages/user/register' })
    },
  },
}
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
}

.bg-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  opacity: 0.85;
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 600rpx;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 32rpx;
  padding: 48rpx 32rpx;
  backdrop-filter: blur(20px);
}

.brand {
  text-align: center;
  margin-bottom: 40rpx;
}

.brand-name {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  letter-spacing: 6rpx;
  color: #1d1d1f;
  margin-bottom: 8rpx;
}

.brand-tagline {
  display: block;
  font-size: 24rpx;
  color: #8e8e93;
  letter-spacing: 2rpx;
}

.tabs {
  display: flex;
  background: #f2f2f7;
  border-radius: 20rpx;
  padding: 4rpx;
  margin-bottom: 32rpx;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 28rpx;
  color: #8e8e93;
  border-radius: 16rpx;
  transition: all 0.2s;
}

.tab.active {
  background: #ffffff;
  color: #007aff;
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.08);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.input-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
  background: #f2f2f7;
  border-radius: 16rpx;
  padding: 0 20rpx;
}

.country-code {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 0;
  font-size: 28rpx;
  font-weight: 500;
  color: #1d1d1f;
  border-right: 2rpx solid #d1d1d6;
  padding-right: 16rpx;
}

.arrow-down {
  font-size: 20rpx;
  color: #8e8e93;
}

.phone-input {
  flex: 1;
}

.input-group {
  background: #f2f2f7;
  border-radius: 16rpx;
  padding: 24rpx 20rpx;
  position: relative;
}

.input {
  width: 100%;
  font-size: 28rpx;
  color: #1d1d1f;
}

.code-row {
  justify-content: space-between;
}

.send-code {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  background: #007aff;
  border-radius: 12rpx;
  font-size: 24rpx;
  color: #fff;
  font-weight: 500;
}

.send-code.disabled {
  background: #aeaeb2;
}

.toggle-pwd {
  position: absolute;
  right: 20rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 24rpx;
  color: #007aff;
}

.login-btn {
  padding: 28rpx 0;
  font-size: 30rpx;
  margin-top: 16rpx;
}

.login-btn.disabled {
  opacity: 0.4;
}

.divider {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 32rpx 0;
}

.line {
  flex: 1;
  height: 1rpx;
  background: #d1d1d6;
}

.or {
  font-size: 24rpx;
  color: #8e8e93;
  white-space: nowrap;
}

.social-buttons {
  display: flex;
  justify-content: center;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.social-btn {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #e5e5ea;
}

.social-icon-img {
  width: 40rpx;
  height: 40rpx;
}

.social-icon-text {
  font-size: 36rpx;
  color: #1d1d1f;
}

.register-link {
  text-align: center;
  font-size: 26rpx;
  color: #8e8e93;
}

.link {
  color: #007aff;
  font-weight: 500;
  margin-left: 4rpx;
}

.country-picker-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.country-picker {
  width: 100%;
  max-height: 60vh;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx;
}

.picker-title {
  font-size: 32rpx;
  font-weight: 600;
  display: block;
  margin-bottom: 24rpx;
  text-align: center;
}

.country-list {
  max-height: 50vh;
}

.country-item {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 0;
  font-size: 28rpx;
  border-bottom: 1rpx solid #f2f2f7;
}

.country-dial {
  color: #8e8e93;
}
</style>
