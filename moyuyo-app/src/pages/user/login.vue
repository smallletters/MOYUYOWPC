<template>
  <view class="login">
    <view class="header">
      <text class="logo">MOYUYO</text>
      <text class="welcome">Welcome back</text>
      <text class="sub">Sign in to continue your journey</text>
    </view>

    <view class="form">
      <view class="input-group">
        <text class="input-label">Email</text>
        <input
          v-model="email"
          class="input"
          type="text"
          placeholder="your@email.com"
          :maxlength="100"
        />
      </view>

      <view class="input-group">
        <text class="input-label">Password</text>
        <input
          v-model="password"
          class="input"
          :type="showPassword ? 'text' : 'password'"
          placeholder="At least 8 characters"
          :maxlength="64"
        />
        <text class="toggle-pwd" @click="showPassword = !showPassword">
          {{ showPassword ? 'Hide' : 'Show' }}
        </text>
      </view>

      <view class="form-extra">
        <view class="remember" @click="remember = !remember">
          <view class="checkbox" :class="{ checked: remember }">
            <text v-if="remember">✓</text>
          </view>
          <text>Remember me</text>
        </view>
        <text class="forgot" @click="onForgot">Forgot password?</text>
      </view>

      <view
        class="btn btn-primary login-btn"
        :class="{ disabled: !canSubmit }"
        @click="onLogin"
      >
        Sign In
      </view>

      <view class="divider">
        <view class="line"></view>
        <text class="or">or continue with</text>
        <view class="line"></view>
      </view>

      <view class="social-buttons">
        <view class="social-btn" @click="onSocial('apple')">
          <text class="social-icon"></text>
          <text>Apple</text>
        </view>
        <view class="social-btn" @click="onSocial('google')">
          <text class="social-icon">G</text>
          <text>Google</text>
        </view>
        <view class="social-btn" @click="onSocial('facebook')">
          <text class="social-icon">f</text>
          <text>Facebook</text>
        </view>
      </view>

      <view class="register">
        Don't have an account?
        <text class="register-link" @click="goRegister">Sign Up</text>
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
      remember: false,
      showPassword: false
    }
  },

  computed: {
    canSubmit() {
      return this.email.includes('@') && this.password.length >= 8
    },
    userStore() {
      return useUserStore()
    }
  },

  methods: {
    async onLogin() {
      if (!this.canSubmit) {
        uni.showToast({ title: '请输入邮箱和密码', icon: 'none' })
        return
      }
      try {
        await this.userStore.login({ username: this.email, password: this.password })
        uni.showToast({ title: 'Welcome back!', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home' }), 800)
      } catch (e) {
        uni.showToast({ title: e.message || 'Login failed', icon: 'none' })
      }
    },

    onSocial(provider) {
      uni.showToast({ title: `${provider} 登录开发中`, icon: 'none' })
    },

    onForgot() {
      uni.navigateTo({ url: '/pages/user/forgot' })
    },

    goRegister() {
      uni.navigateTo({ url: '/pages/user/register' })
    }
  }
}
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  background: var(--color-background);
  padding: 64rpx 48rpx;
  padding-top: calc(64rpx + env(safe-area-inset-top));
}

.header {
  margin-bottom: 64rpx;
}

.logo {
  display: block;
  font-size: 64rpx;
  font-weight: var(--font-weight-bold);
  letter-spacing: 4rpx;
  color: var(--color-text);
  margin-bottom: 48rpx;
}

.welcome {
  display: block;
  font-size: 40rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
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
  position: relative;
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
  color: var(--color-text);
}

.toggle-pwd {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
}

.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
}

.remember {
  display: flex;
  align-items: center;
  gap: 8rpx;
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
}

.checkbox.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.forgot {
  color: var(--color-primary-dark);
}

.login-btn {
  padding: 28rpx 0;
  font-size: var(--font-size-md);
}

.login-btn.disabled {
  opacity: 0.5;
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
  background: var(--color-divider);
}

.or {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.social-buttons {
  display: flex;
  gap: 16rpx;
}

.social-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 0;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.social-icon {
  font-size: 40rpx;
  font-weight: bold;
}

.register {
  text-align: center;
  margin-top: 32rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.register-link {
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-medium);
  margin-left: 8rpx;
}
</style>
