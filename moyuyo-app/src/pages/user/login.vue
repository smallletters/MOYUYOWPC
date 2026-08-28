<template>
  <view class="login">
    <view class="bg-overlay" />

    <view class="login-card">
      <view class="brand">
        <text class="brand-name">MOYUYO</text>
        <text class="brand-tagline">给爱宠更美好的生活</text>
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

      <!-- 手机号登录 -->
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
            placeholder="手机号"
            inputmode="numeric"
          >
        </view>

        <view class="input-row code-row">
          <input
            v-model="smsCode"
            class="input"
            type="number"
            maxlength="6"
            placeholder="验证码">
          <view class="send-code" :class="{ disabled: codeCountdown > 0 }" @click="onSendCode">
            <text v-if="codeCountdown === 0">获取验证码</text>
            <text v-else>{{ codeCountdown }}s</text>
          </view>
        </view>
      </view>

      <!-- 邮箱登录 -->
      <view v-show="activeTab === 'email'" class="form">
        <view class="input-group">
          <input
            v-model="email"
            class="input"
            type="text"
            placeholder="邮箱">
        </view>
        <view class="input-group">
          <input
            v-model="password"
            class="input"
            :type="showPassword ? 'text' : 'password'"
            placeholder="密码"
          >
          <text class="toggle-pwd" @click="showPassword = !showPassword">
            <!-- 使用 Lucide 眼睛/闭眼图标,符合主流 APP 密码框视觉规范 -->
            <text v-if="showPassword" class="luc luc-eye-off" />
            <text v-else class="luc luc-eye" />
          </text>
        </view>
      </view>

      <view class="btn btn-primary login-btn" :class="{ disabled: !canSubmit }" @click="onLogin">
        {{ $t('auth.login') }}
      </view>

      <view v-if="showSocial" class="divider">
        <view class="line" />
        <text class="or">{{ $t('auth.otherLoginMethods') }}</text>
        <view class="line" />
      </view>

      <view v-if="showSocial" class="social-buttons">
        <view v-if="config.socialGoogleEnabled" class="social-btn" @click="onSocial('google')">
          <image src="/static/icons/google.svg" class="social-icon-img" />
        </view>
        <view v-if="config.socialAppleEnabled" class="social-btn" @click="onSocial('apple')">
          <text class="social-icon-text">Apple</text>
        </view>
        <!-- 其余渠道保留占位 -->
        <view
          v-if="!config.socialGoogleEnabled && !config.socialAppleEnabled"
          class="social-btn"
          @click="onSocialMore"
        >
          <text class="social-icon-text">···</text>
        </view>
      </view>

      <view class="register-link">
        还没有账号？
        <text class="link" @click="goRegister">立即注册</text>
      </view>
    </view>

    <view
      v-if="showCountryPicker"
      class="country-picker-overlay"
      @click="showCountryPicker = false"
    >
      <view class="country-picker" @click.stop>
        <text class="picker-title">{{ $t('auth.selectCountry') }}</text>
        <scroll-view scroll-y class="country-list">
          <view
            v-for="c in countries"
            :key="c.code"
            class="country-item"
            @click="selectCountry(c)">
            <text>{{ c.flag }} {{ $t(`auth.countryNames.${c.code}`) }}</text>
            <text class="country-dial">{{ c.dial }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { sendPhoneCode } from '@/api/user'
import { config } from '@/utils/config'
import { i18n } from '@/i18n'

export default {
  data() {
    return {
      // 注入配置便于模板使用(避免 computed 多次调用)
      config,
      // 至少有一个渠道启用时才展示"其他登录方式"区块
      showSocial: config.socialGoogleEnabled || config.socialAppleEnabled,
      activeTab: 'phone',
      // tabs 改为 computed,跟随 locale 切换
      phone: '',
      email: '',
      password: '',
      smsCode: '',
      showPassword: false,
      codeCountdown: 0,
      codeTimer: null,
      countryCode: '+1',
      showCountryPicker: false,
      // countries 仅保留 code + dial + flag,name 改由 $t() 渲染
      countries: [
        { code: 'US', dial: '+1', flag: '🇺🇸' },
        { code: 'CA', dial: '+1', flag: '🇨🇦' },
        { code: 'GB', dial: '+44', flag: '🇬🇧' },
        { code: 'AU', dial: '+61', flag: '🇦🇺' },
        { code: 'DE', dial: '+49', flag: '🇩🇪' },
        { code: 'FR', dial: '+33', flag: '🇫🇷' },
        { code: 'JP', dial: '+81', flag: '🇯🇵' },
        { code: 'KR', dial: '+82', flag: '🇰🇷' },
        { code: 'SG', dial: '+65', flag: '🇸🇬' },
        { code: 'CN', dial: '+86', flag: '🇨🇳' },
      ],
      // locale 版本号:locale 切换时自增,触发 computed 重算
      localeVersion: 0,
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
    tabs() {
      void this.localeVersion
      return [
        { value: 'phone', label: i18n.t('auth.tabPhone') },
        { value: 'email', label: i18n.t('auth.tabEmail') },
      ]
    },
  },

  onLoad() {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
    if (this.codeTimer) clearInterval(this.codeTimer)
  },

  methods: {
    async onLogin() {
      if (!this.canSubmit) return
      uni.showLoading({ title: '登录中...', mask: true })
      try {
        if (this.activeTab === 'phone') {
          // 手机号 + 验证码登录：直接走 loginByPhone,未注册时后端自动创建账号
          const fullPhone = this.countryCode + this.phone
          // 通过专门的手机登录函数 loginByPhone 调用后端,拿到 accessToken/refreshToken 后注入 store
          const { loginByPhone } = await import('@/api/user')
          const result = await loginByPhone(fullPhone, this.smsCode)
          // 手动写入 store:模拟 login() 成功路径
          this.userStore.token = result.accessToken
          this.userStore.refreshToken = result.refreshToken
          const { setStorage, STORAGE_KEYS } = await import('@/utils/storage')
          setStorage(STORAGE_KEYS.TOKEN, result.accessToken)
          setStorage('moyuyo_refresh_token', result.refreshToken)
          await this.userStore.fetchProfile()
        } else {
          const credentials = { username: this.email, password: this.password }
          const result = await this.userStore.login(credentials)
          if (result?.requiresTwoFactor) {
            uni.hideLoading()
            uni.navigateTo({ url: '/pages/user/two-factor' })
            return
          }
        }
        uni.hideLoading()
        uni.showToast({ title: '登录成功', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home' }), 800)
      } catch (e) {
        uni.hideLoading()
        // 修复:把后端真实错误消息完整透出(避免 "Request failed (403)" 这种掩盖问题)
        const msg = e?.message || '登录失败'
        uni.showToast({ title: msg, icon: 'none', duration: 3000 })
        // 调试日志:console 可见原始错误对象,便于排查 403/400
        console.error('[login] error:', e)
      }
    },

    async onSendCode() {
      if (this.codeCountdown > 0) return
      if (this.phone.length < 8) {
        uni.showToast({ title: '请输入手机号', icon: 'none' })
        return
      }
      const fullPhone = this.countryCode + this.phone
      try {
        // 调用真实 SMS 发送接口；成功后启动前端倒计时
        await sendPhoneCode(fullPhone, 'LOGIN')
        this.codeCountdown = 60
        this.codeTimer = setInterval(() => {
          this.codeCountdown -= 1
          if (this.codeCountdown <= 0 && this.codeTimer) {
            clearInterval(this.codeTimer)
            this.codeTimer = null
          }
        }, 1000)
        uni.showToast({ title: i18n.t('auth.codeSent'), icon: 'success' })
      } catch (e) {
        // 后端限流/短信故障时把 message 透传
        const msg = e?.message || '发送失败,请稍后再试'
        uni.showToast({ title: msg, icon: 'none', duration: 3000 })
        console.error('[login] sendPhoneCode error:', e)
      }
    },

    selectCountry(c) {
      this.countryCode = c.dial
      this.showCountryPicker = false
    },

    onSocial(provider) {
      // Google：H5 走 OAuth Web Flow，跳到后端授权入口
      if (provider === 'google') {
        if (config.socialGoogleUrl) {
          // #ifdef H5
          if (typeof window !== 'undefined') {
            window.location.href = config.socialGoogleUrl
            return
          }
          // #endif
          // 原生端：走 web-view 容器打开
          uni.navigateTo({
            url: `/pages/webview/document?url=${encodeURIComponent(config.socialGoogleUrl)}&title=Google 登录`,
          })
        } else {
          uni.showToast({ title: 'Google 登录未配置', icon: 'none' })
        }
        return
      }
      // Apple：原生端走 uni.login({ provider: 'apple' })，H5 暂无 Apple JS SDK
      if (provider === 'apple') {
        // #ifdef APP-PLUS || MP-WEIXIN
        uni.login({
          provider: 'apple',
          success: (res) => {
            uni.showToast({ title: 'Apple 登录待接入', icon: 'none' })
          },
          fail: () => {
            uni.showToast({ title: i18n.t('auth.appleLoginFailed'), icon: 'none' })
          },
        })
        // #endif
        // #ifdef H5
        uni.showToast({ title: 'H5 暂不支持 Apple 登录', icon: 'none' })
        // #endif
        return
      }
      uni.showToast({ title: `${provider} 登录即将上线`, icon: 'none' })
    },

    onSocialMore() {
      // 仅在没有任何已配置的社会化渠道时才会显示"···"占位按钮
      uni.showToast({ title: i18n.t('auth.moreLoginMethods'), icon: 'none' })
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
  /* 调整为图标按钮:加大点击热区,图标更醒目 */
  padding: 8rpx;
  font-size: 36rpx;
  line-height: 1;
}

.login-btn {
  /* 主流 APP(微信/淘宝/小红书)登录按钮规范:
     大圆角胶囊 + 品牌渐变 + 微阴影抬起 + 按压缩放反馈 + 顶部高光提亮 */
  /* 改为块级 + 100% 宽度,铺满登录卡片,符合主流 APP 登录按钮的视觉占比 */
  display: flex;
  width: 100%;
  padding: 28rpx 0;
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  margin-top: 24rpx;
  border-radius: 999rpx;
  /* 改用页面品牌主色 Sand Gold 渐变,与全站一致 */
  background: linear-gradient(135deg, #e8ddb5 0%, #dbc98a 50%);
  color: #2e2b29;
  box-shadow:
    0 8rpx 20rpx rgba(219, 201, 138, 0.45),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition:
    transform 0.15s ease,
    box-shadow 0.2s ease,
    opacity 0.2s ease;
}

.login-btn:active:not(.disabled) {
  /* 按下时缩小并降低阴影,模拟物理按压感 */
  transform: scale(0.98);
  box-shadow:
    0 4rpx 10rpx rgba(219, 201, 138, 0.3),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
}

.login-btn.disabled {
  /* 禁用态:灰化 + 去除阴影 + 不可点击光标 */
  opacity: 0.45;
  box-shadow: none;
  cursor: not-allowed;
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
  /* 与上方社交按钮区拉开距离,整体向下微调 */
  margin-top: 32rpx;
}

.link {
  /* 改用页面品牌主色 */
  color: var(--color-primary-dark);
  font-weight: 600;
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
