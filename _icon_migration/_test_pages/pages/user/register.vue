<template>
  <view class="register">
    <view class="header-bar">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">注册</text>
      <view class="header-spacer" />
    </view>

    <view class="register-card">
      <text class="title">创建账号</text>
      <text class="agreement-text">
        注册即同意
        <text class="link">用户协议</text>
        和
        <text class="link">隐私政策</text>
      </text>

      <view class="form">
        <view class="input-group">
          <input
            v-model="nickname"
            class="input"
            type="text"
            placeholder="设置昵称"
            maxlength="20"
          >
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

        <!-- Phone Registration -->
        <view v-show="activeTab === 'phone'" class="form-section">
          <view class="input-row">
            <view class="country-code" @click="showCountryPicker = true">
              <text>{{ countryCode }}</text>
              <text class="arrow">▾</text>
            </view>
            <input
              v-model="phone"
              class="input"
              type="number"
              maxlength="11"
              placeholder="手机号"
              inputmode="numeric"
            >
          </view>
          <view class="code-row">
            <input
              v-model="smsCode"
              class="input"
              type="number"
              maxlength="6"
              placeholder="验证码"
            >
            <view class="send-code" :class="{ disabled: codeCountdown > 0 }" @click="onSendCode">
              <text v-if="codeCountdown === 0">获取验证码</text>
              <text v-else>{{ codeCountdown }}s</text>
            </view>
          </view>
        </view>

        <!-- Email Registration -->
        <view v-show="activeTab === 'email'" class="form-section">
          <view class="input-group">
            <input
              v-model="email"
              class="input"
              type="email"
              placeholder="邮箱地址">
          </view>
          <view class="code-row">
            <input
              v-model="emailCode"
              class="input"
              type="number"
              maxlength="6"
              placeholder="验证码"
            >
            <view
              class="send-code"
              :class="{ disabled: emailCodeCountdown > 0 }"
              @click="onSendEmailCode"
            >
              <text v-if="emailCodeCountdown === 0">获取验证码</text>
              <text v-else>{{ emailCodeCountdown }}s</text>
            </view>
          </view>
        </view>

        <!-- Password -->
        <view class="input-group">
          <input
            v-model="password"
            class="input"
            :type="showPassword ? 'text' : 'password'"
            placeholder="设置密码"
          >
          <text class="toggle-pwd" @click="showPassword = !showPassword">
            {{ showPassword ? 'Hide' : 'Show' }}
          </text>
        </view>

        <view class="input-group">
          <input
            v-model="confirmPassword"
            class="input"
            :type="showConfirm ? 'text' : 'password'"
            placeholder="确认密码"
          >
          <text class="toggle-pwd" @click="showConfirm = !showConfirm">
            {{ showConfirm ? 'Hide' : 'Show' }}
          </text>
        </view>

        <!-- Pet Preference -->
        <view class="pet-preference">
          <text class="pref-label">选择你的宠物类型</text>
          <view class="pref-options">
            <view
              v-for="p in petTypes"
              :key="p.value"
              class="pref-btn"
              :class="{ active: petType === p.value }"
              @click="petType = p.value"
            >
              <text class="pref-emoji">{{ p.emoji }}</text>
              <text>{{ p.label }}</text>
            </view>
          </view>
        </view>

        <view
          class="btn btn-primary submit-btn"
          :class="{ disabled: !canSubmit }"
          @click="onRegister"
        >
          注册
        </view>

        <view class="login-link">
          已有账号？
          <text class="link" @click="goLogin">立即登录</text>
        </view>
      </view>
    </view>
  </view>
</template><script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      activeTab: 'phone',
      tabs: [
        { value: 'phone', label: '手机号注册' },
        { value: 'email', label: '邮箱注册' },
      ],
      nickname: '',
      phone: '',
      email: '',
      smsCode: '',
      emailCode: '',
      password: '',
      confirmPassword: '',
      showPassword: false,
      showConfirm: false,
      countryCode: '+86',
      showCountryPicker: false,
      codeCountdown: 0,
      emailCodeCountdown: 0,
      petType: 'dog',
      petTypes: [
        { value: 'dog', label: '狗狗', emoji: 'paw-print' },
        { value: 'cat', label: '猫咪', emoji: 'paw-print' },
        { value: 'other', label: '其他', emoji: 'paw-print' },
      ],
      countries: [
        { code: 'CN', name: '中国', dial: '+86', flag: '🇨🇳' },
        { code: 'US', name: 'United States', dial: '+1', flag: '🇺🇸' },
        { code: 'GB', name: 'United Kingdom', dial: '+44', flag: '🇬🇧' },
        { code: 'JP', name: '日本', dial: '+81', flag: '🇯🇵' },
        { code: 'KR', name: 'South Korea', dial: '+82', flag: '🇰🇷' },
        { code: 'HK', name: '香港', dial: '+852', flag: '🇭🇰' },
      ],
    }
  },

  computed: {
    canSubmit() {
      if (!this.nickname) return false
      if (this.activeTab === 'phone') {
        if (!this.phone || this.phone.length < 8 || !this.smsCode) return false
      } else {
        if (!this.email?.includes('@') || !this.emailCode) return false
      }
      if (!this.password || this.password.length < 8) return false
      if (this.password !== this.confirmPassword) return false
      return true
    },
    userStore() {
      return useUserStore()
    },
  },

  methods: {
    async onRegister() {
      if (!this.canSubmit) {
        if (this.password !== this.confirmPassword) {
          uni.showToast({ title: '两次密码不一致', icon: 'none' })
          return
        }
        return
      }
      uni.showLoading({ title: '注册中...', mask: true })
      try {
        await this.userStore.register({
          nickname: this.nickname,
          email: this.activeTab === 'email' ? this.email : null,
          phone: this.activeTab === 'phone' ? this.countryCode + this.phone : null,
          password: this.password,
          petType: this.petType,
        })
        uni.hideLoading()
        uni.showToast({ title: '注册成功！', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '注册失败', icon: 'none' })
      }
    },

    onSendCode() {
      if (this.codeCountdown > 0) return
      if (!this.phone || this.phone.length < 8) {
        uni.showToast({ title: '请输入手机号', icon: 'none' })
        return
      }
      this.codeCountdown = 60
      const timer = setInterval(() => {
        this.codeCountdown -= 1
        if (this.codeCountdown <= 0) clearInterval(timer)
      }, 1000)
    },

    onSendEmailCode() {
      if (this.emailCodeCountdown > 0) return
      if (!this.email?.includes('@')) {
        uni.showToast({ title: '请输入正确邮箱', icon: 'none' })
        return
      }
      this.emailCodeCountdown = 60
      const timer = setInterval(() => {
        this.emailCodeCountdown -= 1
        if (this.emailCodeCountdown <= 0) clearInterval(timer)
      }, 1000)
    },

    goBack() {
      uni.navigateBack()
    },
    goLogin() {
      uni.navigateTo({ url: '/pages/user/login' })
    },
  },
}
</script>

<style lang="scss" scoped>
.register {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 0 32rpx 64rpx;
}

.header-bar {
  display: flex;
  align-items: center;
  padding: 24rpx 0 16rpx;
  padding-top: calc(24rpx + env(safe-area-inset-top));
}

.back-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 40rpx;
  color: #fff;
  line-height: 1;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
}

.header-spacer {
  width: 64rpx;
}

.register-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 32rpx;
  padding: 40rpx 32rpx;
  backdrop-filter: blur(20px);
}

.title {
  font-size: 40rpx;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 12rpx;
}

.agreement-text {
  font-size: 24rpx;
  color: #8e8e93;
  margin-bottom: 32rpx;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.tabs {
  display: flex;
  background: #f2f2f7;
  border-radius: 16rpx;
  padding: 4rpx;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  font-size: 26rpx;
  color: #8e8e93;
  border-radius: 12rpx;
}

.tab.active {
  background: #fff;
  color: #007aff;
  font-weight: 600;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
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

.toggle-pwd {
  position: absolute;
  right: 20rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 24rpx;
  color: #007aff;
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
  gap: 6rpx;
  padding: 24rpx 0;
  font-size: 28rpx;
  font-weight: 500;
  border-right: 2rpx solid #d1d1d6;
  padding-right: 16rpx;
}

.arrow {
  font-size: 20rpx;
  color: #8e8e93;
}

.code-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
  background: #f2f2f7;
  border-radius: 16rpx;
  padding: 0 20rpx;
}

.code-row .input {
  flex: 1;
  padding: 24rpx 0;
}

.send-code {
  flex-shrink: 0;
  padding: 14rpx 24rpx;
  background: #007aff;
  border-radius: 12rpx;
  font-size: 24rpx;
  color: #fff;
  font-weight: 500;
}

.send-code.disabled {
  background: #aeaeb2;
}

.pet-preference {
  margin-top: 8rpx;
}

.pref-label {
  display: block;
  font-size: 26rpx;
  color: #48484a;
  margin-bottom: 16rpx;
}

.pref-options {
  display: flex;
  gap: 16rpx;
}

.pref-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 20rpx 0;
  background: #f2f2f7;
  border-radius: 16rpx;
  font-size: 24rpx;
  color: #8e8e93;
  border: 2rpx solid transparent;
  transition: all 0.2s;
}

.pref-btn.active {
  border-color: #007aff;
  background: #e8f2ff;
  color: #007aff;
  font-weight: 600;
}

.pref-emoji {
  font-size: 40rpx;
}

.submit-btn {
  padding: 28rpx 0;
  font-size: 30rpx;
  margin-top: 8rpx;
}

.submit-btn.disabled {
  opacity: 0.4;
}

.login-link {
  text-align: center;
  font-size: 26rpx;
  color: #8e8e93;
  margin-top: 24rpx;
}

.link {
  color: #007aff;
  font-weight: 500;
  margin-left: 4rpx;
}
</style>
