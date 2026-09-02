<template>
  <!-- 美国购物APP登录页(Amazon / Target / Shein风格):
       1. 纯白背景 + 单列垂直布局,减少视觉噪音
       2. Topbar:返回键 + 居中品牌(与注册页视觉一致)
       3. Hero:大号欢迎标题 + 副标题价值主张
       4. 社交登录在表单上方(Google/Apple优先,降低登录门槛)
       5. OR分隔线 + 单列表单(label在input上方,Baymard最佳实践)
       6. Email登录表单右侧"忘记密码"快捷入口(Target标准)
       7. 全宽实心胶囊CTA按钮,高度≥56dp拇指友好
       8. 条款/信任徽章 + 底部注册入口 -->
  <view class="login">
    <scroll-view scroll-y class="page-scroll">
      <view class="page-inner">
        <!-- Hero标题区:大号欢迎 + 价值主张副标题(Target风格) -->
        <view class="hero">
          <text class="hero-title">{{ $t('auth.welcomeBack') }}</text>
          <text class="hero-subtitle">{{ $t('auth.loginBenefit') }}</text>
        </view>

        <!-- 社交登录(Google/Apple优先,美国电商标配) -->
        <view class="social-row">
          <view class="social-btn google" @click="onSocial('google')">
            <image src="/static/icons/google.svg" class="social-logo" mode="aspectFit" />
            <text class="social-label">{{ $t('auth.signInWithGoogle') }}</text>
          </view>
          <view class="social-btn apple" @click="onSocial('apple')">
            <image src="/static/icons/apple.svg" class="social-logo social-logo-apple" mode="aspectFit" />
            <text class="social-label">{{ $t('auth.signInWithApple') }}</text>
          </view>
        </view>

        <!-- OR分隔线(Amazon/Target标配) -->
        <view class="divider-or">
          <view class="divider-line" />
          <text class="divider-text">{{ $t('common.or') }}</text>
          <view class="divider-line" />
        </view>

        <view class="form">
          <!-- 登录方式切换:Segmented Control(iOS 16样式),默认Email -->
          <view class="segmented">
            <view
              v-for="t in tabs"
              :key="t.value"
              class="segmented-item"
              :class="{ active: activeTab === t.value }"
              @click="activeTab = t.value"
            >
              {{ t.label }}
            </view>
          </view>

          <!-- Phone 登录区 -->
          <view v-show="activeTab === 'phone'" class="form-section">
            <view class="field">
              <text class="field-label">{{ $t('auth.phonePlaceholder') }}</text>
              <view class="phone-field">
                <view class="country-picker" @click="showCountryPicker = true">
                  <text class="country-flag">{{ countryFlag }}</text>
                  <text class="country-dial">{{ countryCode }}</text>
                  <text class="luc luc-chevron-down chevron" />
                </view>
                <input
                  v-model="phone"
                  class="field-input phone-input"
                  type="number"
                  maxlength="11"
                  inputmode="numeric"
                  :placeholder="$t('auth.phoneInputHint')"
                >
              </view>
            </view>
            <view class="field">
              <text class="field-label">{{ $t('auth.codePlaceholder') }}</text>
              <view class="code-field">
                <input
                  v-model="smsCode"
                  class="field-input"
                  type="number"
                  maxlength="6"
                  inputmode="numeric"
                  :placeholder="$t('auth.codeInputHint')"
                >
                <view class="code-btn" :class="{ disabled: codeCountdown > 0 }" @click="onSendCode">
                  <text v-if="codeCountdown === 0">{{ $t('auth.sendCode') }}</text>
                  <text v-else>{{ codeCountdown }}s</text>
                </view>
              </view>
            </view>
          </view>

          <!-- Email 登录区 -->
          <view v-show="activeTab === 'email'" class="form-section">
            <view class="field">
              <text class="field-label">{{ $t('auth.emailPlaceholder') }}</text>
              <input
                v-model="email"
                class="field-input"
                type="email"
                :placeholder="$t('auth.emailInputHint')"
              >
            </view>
            <view class="field">
              <view class="pwd-header">
                <text class="field-label">{{ $t('auth.passwordPlaceholder') }}</text>
                <text class="forgot-link" @click="onForgot">{{ $t('auth.forgot') }}</text>
              </view>
              <view class="pwd-field">
                <input
                  v-model="password"
                  class="field-input pwd-input"
                  :type="showPassword ? 'text' : 'password'"
                  :placeholder="$t('auth.passwordHint')"
                >
                <text class="toggle-pwd" @click="showPassword = !showPassword">
                  <text v-if="showPassword" class="luc luc-eye-off" />
                  <text v-else class="luc luc-eye" />
                </text>
              </view>
            </view>
          </view>

          <!-- 主CTA:全宽实心胶囊按钮(与注册页同款) -->
          <view class="cta-btn" :class="{ disabled: !canSubmit }" @click="onLogin">
            <text class="cta-text">{{ $t('auth.signInBtn') || $t('auth.login') }}</text>
          </view>

          <!-- 条款:勾选框+超链接(Clickwrap合规,参考Shein/Amazon) -->
          <view class="terms-check" @click="termsAgreed = !termsAgreed">
            <view class="checkbox" :class="{ checked: termsAgreed }">
              <text v-if="termsAgreed" class="check-icon">✓</text>
            </view>
            <view class="terms-text-wrap">
              <text class="terms-text">{{ $t('auth.agreeLoginTerms') }}</text>
              <text class="terms-link">{{ $t('auth.termsAndPolicy') }}</text>
              <text class="terms-text">{{ $t('common.and') }}</text>
              <text class="terms-link">{{ $t('auth.privacyPolicy') }}</text>
            </view>
          </view>

          <!-- 注册入口:位于条款下方,弱色居中(Target/Shein标准) -->
          <view class="bottom-register">
            <text class="bottom-text">{{ $t('auth.noAccount') }}</text>
            <text class="bottom-link" @click="goRegister">{{ $t('auth.goRegister') }}</text>
          </view>

          <!-- 信任徽章(Amazon结账页模式) -->
          <view class="trust-row">
            <view class="trust-item">
              <text class="luc luc-shield-check trust-icon" />
              <text class="trust-text">{{ $t('auth.secureInfo') }}</text>
            </view>
            <view class="trust-item">
              <text class="luc luc-lock trust-icon" />
              <text class="trust-text">{{ $t('auth.noSpam') }}</text>
            </view>
          </view>
        </view>

        <view class="safe-bottom" />
      </view>
    </scroll-view>

    <!-- 国家选择弹层(底部sheet,iOS UIActionSheet样式,与注册页一致) -->
    <view v-if="showCountryPicker" class="country-sheet-mask" @click="showCountryPicker = false">
      <view class="country-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">{{ $t('auth.selectCountryTitle') }}</text>
          <view class="sheet-close" @click="showCountryPicker = false">
            <text class="luc luc-x" />
          </view>
        </view>
        <scroll-view scroll-y class="country-list">
          <view
            v-for="c in countries"
            :key="c.code"
            class="country-item"
            :class="{ active: c.dial === countryCode }"
            @click="selectCountry(c)"
          >
            <text class="ci-flag">{{ c.flag }}</text>
            <text class="ci-name">{{ $t(`auth.countryNames.${c.code}`) }}</text>
            <text class="ci-dial">{{ c.dial }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { sendPhoneCode, loginByPhone } from '@/api/user'
import { config } from '@/utils/config'
import { setStorage, STORAGE_KEYS } from '@/utils/storage'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userLogin',

  data() {
    return {
      // 注入配置便于模板使用(避免 computed 多次调用)
      config,
      activeTab: 'email', // 美国用户首选Email登录(符合Amazon/Target/Shein主流程)
      // tabs 改为 computed,跟随 locale 切换
      phone: '',
      email: '',
      password: '',
      smsCode: '',
      showPassword: false,
      codeCountdown: 0,
      codeTimer: null,
      countryCode: '+1', // 默认美国区号(+1)对齐目标市场
      showCountryPicker: false,
      termsAgreed: false, // 条款勾选状态(Clickwrap合规,默认不勾选)
      // countries 仅保留 code + dial + flag,name 改由 $t() 渲染
      countries: [
        // 美国市场优先排第一个(Target登录页默认美国)
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
      // Clickwrap合规:必须勾选条款才能提交
      if (!this.termsAgreed) return false
      if (this.activeTab === 'phone') {
        return this.phone.length >= 8 && this.smsCode.length >= 4
      }
      return this.email.includes('@') && this.password.length >= 8
    },
    // 根据当前countryCode查找对应国旗emoji(模板使用)
    countryFlag() {
      const found = this.countries.find((c) => c.dial === this.countryCode)
      return found?.flag || '🇺🇸'
    },
    userStore() {
      return useUserStore()
    },
    tabs() {
      void this.localeVersion
      return [
        { value: 'email', label: i18n.t('auth.tabEmail') },
        { value: 'phone', label: i18n.t('auth.tabPhone') },
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
      // Clickwrap合规:未勾选条款时提示
      if (!this.termsAgreed) {
        uni.showToast({ title: '请先同意服务条款', icon: 'none' })
        return
      }
      if (!this.canSubmit) return
      uni.showLoading({ title: '登录中...', mask: true })
      try {
        if (this.activeTab === 'phone') {
          // 手机号 + 验证码登录：直接走 loginByPhone,未注册时后端自动创建账号
          const fullPhone = this.countryCode + this.phone
          // 通过专门的手机登录函数 loginByPhone 调用后端,拿到 accessToken/refreshToken 后注入 store
          const result = await loginByPhone(fullPhone, this.smsCode)
          // 手动写入 store:模拟 login() 成功路径
          this.userStore.token = result.accessToken
          this.userStore.refreshToken = result.refreshToken
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
/* 美国购物APP登录页样式(参考Amazon/Target/Shein移动H5):
   - 纯白页面背景,无渐变(减少视觉噪音),与注册页视觉一致
   - 单列表单,label在input上方(Baymard最佳实践)
   - CTA全宽实心,高度≥96rpx,圆角999rpx(拇指区≥56dp)
   - 密码标题右侧"Forgot?"快捷链接(Target标准)
   - 辅助文案、条款12-13sp弱灰,与CTA形成层级 */
.login {
  min-height: 100vh;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}

/* 可滚动区:占满剩余高度,与底部注册入口解耦 */
.page-scroll {
  flex: 1;
  height: 0; /* flex子项高度设0,保证内部scroll-y在uni-app中生效 */
}

.page-inner {
  padding: 40rpx 32rpx 0;
}

/* Hero标题:大号欢迎+价值主张(Target登录页模式) */
.hero {
  margin-bottom: 40rpx;
}

.hero-title {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  line-height: 1.2;
  color: #1d1d1f;
  margin-bottom: 12rpx;
  letter-spacing: -0.5rpx;
}

.hero-subtitle {
  display: block;
  font-size: 28rpx;
  line-height: 1.5;
  color: #6e6e73;
}

/* 社交登录按钮:全宽白底描边(Shein/Temu美国站点标准) */
.social-row {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.social-btn {
  height: 92rpx;
  border-radius: 999rpx;
  border: 2rpx solid #e5e5ea;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  transition: all 0.15s ease;
}

.social-btn:active {
  background: #f7f7f9;
  transform: scale(0.99);
}

.social-btn.google {
  color: #1d1d1f;
}

.social-btn.apple {
  background: #000000;
  border-color: #000000;
  color: #ffffff;
}

.social-logo {
  width: 36rpx;
  height: 36rpx;
}

/* Apple 图标在黑色按钮上需要显示为白色。
 * lucide svg 是 stroke="currentColor",用 <image> 加载后 currentColor 不生效,
 * 改用 filter:invert 把单色 SVG 翻成白色(0=全黑 → invert 后全白;实际效果等同 mask:white). */
.social-logo-apple {
  /* 100% 反相 + 100% 转灰,可把任何单色 SVG 变白 */
  filter: brightness(0) invert(1);
}

.social-label {
  font-size: 28rpx;
  font-weight: 600;
}

/* OR分隔线(Amazon标准) */
.divider-or {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 32rpx;
}

.divider-line {
  flex: 1;
  height: 1rpx;
  background: #e5e5ea;
}

.divider-text {
  font-size: 24rpx;
  color: #8e8e93;
  text-transform: uppercase;
  letter-spacing: 1rpx;
}

/* 表单:单列+垂直gap,字段之间呼吸感充足 */
.form {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

/* 登录方式切换:Segmented Control(iOS 16样式) */
.segmented {
  display: flex;
  background: #f2f2f7;
  border-radius: 14rpx;
  padding: 4rpx;
}

.segmented-item {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  font-size: 26rpx;
  color: #1d1d1f;
  border-radius: 10rpx;
  font-weight: 500;
  transition: all 0.2s ease;
}

.segmented-item.active {
  background: #ffffff;
  box-shadow: 0 3rpx 8rpx rgba(0, 0, 0, 0.08);
  color: #1d1d1f;
  font-weight: 700;
}

/* 每个field:label在上,input在下(Baymard Mobile Form研究) */
.field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.field-label {
  font-size: 26rpx;
  font-weight: 600;
  color: #1d1d1f;
  line-height: 1.4;
}

.field-input {
  height: 92rpx;
  padding: 0 24rpx;
  font-size: 30rpx;
  color: #1d1d1f;
  background: #ffffff;
  border: 2rpx solid #d1d1d6;
  border-radius: 16rpx;
  outline: none;
  box-sizing: border-box;
}

.field-input:focus {
  border-color: var(--color-primary, #007aff);
  box-shadow: 0 0 0 4rpx rgba(0, 122, 255, 0.12);
}

/* 手机号字段:国旗+区号+下拉箭头+号码输入 */
.phone-field {
  display: flex;
  align-items: stretch;
  height: 92rpx;
  border: 2rpx solid #d1d1d6;
  border-radius: 16rpx;
  overflow: hidden;
  background: #ffffff;
}

.phone-field:focus-within {
  border-color: var(--color-primary, #007aff);
  box-shadow: 0 0 0 4rpx rgba(0, 122, 255, 0.12);
}

.country-picker {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 0 16rpx 0 20rpx;
  border-right: 2rpx solid #e5e5ea;
  background: #fafafa;
}

.country-flag {
  font-size: 32rpx;
  line-height: 1;
}

.country-dial {
  font-size: 28rpx;
  font-weight: 600;
  color: #1d1d1f;
}

.chevron {
  font-size: 18rpx;
  color: #8e8e93;
  margin-left: 2rpx;
}

.phone-input {
  flex: 1;
  height: 100%;
  border: none;
  border-radius: 0;
  padding: 0 20rpx;
}

/* 验证码+发送按钮:输入框满宽,按钮贴右 */
.code-field {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 92rpx;
}

.code-field .field-input {
  flex: 1;
  height: 100%;
}

.code-btn {
  flex-shrink: 0;
  height: 92rpx;
  padding: 0 28rpx;
  border-radius: 16rpx;
  background: var(--color-primary, #007aff);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.code-btn.disabled {
  background: #c7c7cc;
}

/* 密码标题行:label居左 + "忘记密码"link居右(Target标准) */
.pwd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.forgot-link {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-primary, #007aff);
}

/* 密码显示/隐藏切换:眼睛图标加大热区 */
.pwd-field {
  position: relative;
  height: 92rpx;
}

.pwd-input {
  width: 100%;
  height: 100%;
  padding-right: 80rpx;
}

.toggle-pwd {
  position: absolute;
  right: 16rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: #8e8e93;
}

/* 主CTA按钮:全宽实心胶囊(Amazon/Target风格),与注册页同款 */
.cta-btn {
  margin-top: 8rpx;
  height: 104rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #e8ddb5 0%, #dbc98a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 10rpx 24rpx rgba(219, 201, 138, 0.42),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.6);
  transition: all 0.15s ease;
}

.cta-btn:active:not(.disabled) {
  transform: scale(0.985);
  box-shadow: 0 4rpx 14rpx rgba(219, 201, 138, 0.3);
}

.cta-text {
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  color: #2e2b29;
}

.cta-btn.disabled {
  background: #e5e5ea;
  box-shadow: none;
}

.cta-btn.disabled .cta-text {
  color: #8e8e93;
}

/* 条款区:CTA下方12sp小字居中,不抢主流程注意力 */
/* 条款勾选区(Clickwrap合规) */
.terms-check {
  margin-top: 24rpx;
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 0 8rpx;
}

.checkbox {
  width: 36rpx;
  height: 36rpx;
  border: 3rpx solid #c7c7cc;
  border-radius: 8rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 4rpx;
}

.checkbox.checked {
  background: var(--color-primary, #007aff);
  border-color: var(--color-primary, #007aff);
}

.check-icon {
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
}

.terms-text-wrap {
  flex: 1;
}

.terms-text {
  font-size: 22rpx;
  line-height: 1.6;
  color: #8e8e93;
}

.terms-link {
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--color-primary, #007aff);
  font-weight: 600;
}

/* 信任徽章行:2列安全提示(Amazon结账页模式) */
.trust-row {
  margin-top: 32rpx;
  padding: 20rpx 24rpx;
  background: #f7f7f9;
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.trust-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.trust-icon {
  font-size: 30rpx;
  color: #34c759;
  line-height: 1;
}

.trust-text {
  font-size: 24rpx;
  color: #3c3c43;
  line-height: 1.4;
}

/* 底部注册入口:弱色分隔+居中文字(Target/Shein标准) */
.bottom-register {
  margin: 56rpx 0 24rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid #f0f0f3;
  text-align: center;
}

.bottom-text {
  font-size: 26rpx;
  color: #6e6e73;
}

.bottom-link {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-primary, #007aff);
  margin-left: 6rpx;
}

.safe-bottom {
  height: calc(32rpx + env(safe-area-inset-bottom));
}

/* 国家选择弹层:底部sheet(iOS 16 UIActionSheet模式),与注册页一致 */
.country-sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.country-sheet {
  width: 100%;
  max-height: 70vh;
  background: #ffffff;
  border-radius: 28rpx 28rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet-header {
  display: flex;
  align-items: center;
  padding: 28rpx 28rpx 20rpx;
  border-bottom: 1rpx solid #f0f0f3;
}

.sheet-title {
  flex: 1;
  text-align: center;
  font-size: 30rpx;
  font-weight: 700;
  color: #1d1d1f;
}

.sheet-close {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #8e8e93;
}

.country-list {
  flex: 1;
  max-height: 60vh;
  padding: 8rpx 0;
}

.country-item {
  display: flex;
  align-items: center;
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid #f2f2f7;
}

.country-item.active {
  background: rgba(0, 122, 255, 0.06);
}

.ci-flag {
  font-size: 32rpx;
  width: 52rpx;
  margin-right: 16rpx;
}

.ci-name {
  flex: 1;
  font-size: 28rpx;
  color: #1d1d1f;
  font-weight: 500;
}

.ci-dial {
  font-size: 26rpx;
  color: #6e6e73;
  font-weight: 600;
}
</style>
