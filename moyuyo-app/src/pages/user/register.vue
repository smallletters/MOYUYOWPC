<template>
  <!-- 美国购物APP注册页布局(Amazon / Target / Shein风格):
       1. 纯白背景 + 单列表单,减少视觉噪音
       2. 顶部品牌标识+简短价值主张
       3. 社交登录按钮在表单最上方(降低注册门槛)
       4. 单列表单,label在input上方,符合Baymard研究推荐
       5. CTA全宽实心胶囊按钮,移动端拇指友好
       6. 条款/隐私协议放在CTA下方(小字+弱色),不阻挡注册主流程
       7. 底部登录入口,分隔线+弱色文案 -->
  <view class="register">
    <scroll-view scroll-y class="page-scroll">
      <view class="page-inner">
        <!-- 标题区:明确价值主张(参考Target注册页) -->
        <view class="hero">
          <text class="hero-title">{{ $t('auth.createAccount') }}</text>
          <text class="hero-subtitle">{{ $t('auth.accountBenefit') }}</text>
        </view>

        <!-- 社交登录(Google / Apple优先,美国电商标配) -->
        <view class="social-row">
          <view class="social-btn google" @click="onSocial('google')">
            <image src="/static/icons/google.svg" class="social-logo" mode="aspectFit" />
            <text class="social-label">{{ $t('auth.signUpWithGoogle') }}</text>
          </view>
          <view class="social-btn apple" @click="onSocial('apple')">
            <image
              src="/static/icons/apple.svg"
              class="social-logo social-logo-apple"
              mode="aspectFit"
            />
            <text class="social-label">{{ $t('auth.signUpWithApple') }}</text>
          </view>
        </view>

        <!-- OR分隔线(Amazon/Target标配) -->
        <view class="divider-or">
          <view class="divider-line" />
          <text class="divider-text">{{ $t('common.or') }}</text>
          <view class="divider-line" />
        </view>

        <view class="form">
          <!-- Nickname:label在上方,减少用户记忆负担 -->
          <view class="field">
            <text class="field-label">{{ $t('auth.setNickname') }}</text>
            <input
              v-model="nickname"
              class="field-input"
              type="text"
              maxlength="20"
              :placeholder="$t('auth.nicknamePlaceholder')"
            >
          </view>

          <!-- 注册方式切换(移动端segmented控件) -->
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

          <!-- Phone Section -->
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

          <!-- Email Section -->
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
              <text class="field-label">{{ $t('auth.codePlaceholder') }}</text>
              <view class="code-field">
                <input
                  v-model="emailCode"
                  class="field-input"
                  type="number"
                  maxlength="6"
                  inputmode="numeric"
                  :placeholder="$t('auth.codeInputHint')"
                >
                <view
                  class="code-btn"
                  :class="{ disabled: emailCodeCountdown > 0 }"
                  @click="onSendEmailCode"
                >
                  <text v-if="emailCodeCountdown === 0">{{ $t('auth.sendCode') }}</text>
                  <text v-else>{{ emailCodeCountdown }}s</text>
                </view>
              </view>
            </view>
          </view>

          <!-- Password + Confirm -->
          <view class="field">
            <text class="field-label">{{ $t('auth.setPassword') }}</text>
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
            <text class="helper-text">{{ $t('auth.passwordRule') }}</text>
          </view>

          <view class="field">
            <text class="field-label">{{ $t('auth.confirmPassword') }}</text>
            <view class="pwd-field">
              <input
                v-model="confirmPassword"
                class="field-input pwd-input"
                :type="showConfirm ? 'text' : 'password'"
                :placeholder="$t('auth.confirmPasswordHint')"
              >
              <text class="toggle-pwd" @click="showConfirm = !showConfirm">
                <text v-if="showConfirm" class="luc luc-eye-off" />
                <text v-else class="luc luc-eye" />
              </text>
            </view>
          </view>

          <!-- 宠物偏好(折叠为chip组,label居左,弱分隔) -->
          <view class="field">
            <text class="field-label">{{ $t('auth.petPreference') }}</text>
            <view class="chip-row">
              <view
                v-for="p in petTypes"
                :key="p.value"
                class="pet-chip"
                :class="{ active: petType === p.value }"
                @click="petType = p.value"
              >
                <text class="chip-emoji">{{ p.emoji }}</text>
                <text class="chip-label">{{ $t(`auth.petTypes.${p.value}`) }}</text>
              </view>
            </view>
          </view>

          <!-- 主CTA:全宽实心胶囊,拇指友好(高度≥56dp) -->
          <view class="cta-btn" :class="{ disabled: !canSubmit }" @click="onRegister">
            <text class="cta-text">{{ $t('auth.createAccountBtn') || $t('auth.register') }}</text>
          </view>

          <!-- 条款:CTA下方小字+弱色,不抢夺注意力(Shein风格) -->
          <view class="terms">
            <text class="terms-text">{{ $t('auth.agreeTerms') }}</text>
            <text class="terms-link" @click.stop="openTerms('terms')">
              {{ $t('auth.termsAndPolicy') }}
            </text>
            <text class="terms-text">{{ $t('common.and') }}</text>
            <text class="terms-link" @click.stop="openTerms('privacy')">
              {{ $t('auth.privacyPolicy') }}
            </text>
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
      </view>

      <!-- 底部登录入口 -->
      <view class="bottom-login">
        <text class="bottom-login-text">{{ $t('auth.hasAccount') }}</text>
        <text class="bottom-login-link" @click="goLogin">{{ $t('auth.goLogin') }}</text>
      </view>
      <view class="safe-bottom" />
    </scroll-view>

    <!-- 国家选择弹层(保留原逻辑) -->
    <view v-if="showCountryPicker" class="country-sheet-mask" @click="showCountryPicker = false">
      <view class="country-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">{{ $t('auth.selectCountry') }}</text>
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
            <text class="ci-name">{{ c.code }}</text>
            <text class="ci-dial">{{ c.dial }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>
<script>
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'
import { sendEmailVerification, confirmEmailVerification, sendPhoneCode } from '@/api/user'

export default {
  pageTitleKey: 'pageTitle.userRegister',

  data() {
    return {
      localeVersion: 0,
      activeTab: 'email', // 美国用户首选Email注册(符合Amazon/Target/Shein主流程)
      nickname: '',
      phone: '',
      email: '',
      smsCode: '',
      emailCode: '',
      password: '',
      confirmPassword: '',
      showPassword: false,
      showConfirm: false,
      countryCode: '+1', // 默认美国区号(+1)对齐目标市场
      showCountryPicker: false,
      codeCountdown: 0,
      emailCodeCountdown: 0,
      petType: 'dog',
      petTypes: [
        { value: 'dog', emoji: '🐶' },
        { value: 'cat', emoji: '🐱' },
        { value: 'other', emoji: '🐾' },
      ],
      countries: [
        // 美国市场优先排第一个(Target注册页默认美国)
        { code: 'US', dial: '+1', flag: '🇺🇸' },
        { code: 'CA', dial: '+1', flag: '🇨🇦' },
        { code: 'GB', dial: '+44', flag: '🇬🇧' },
        { code: 'AU', dial: '+61', flag: '🇦🇺' },
        { code: 'CN', dial: '+86', flag: '🇨🇳' },
        { code: 'JP', dial: '+81', flag: '🇯🇵' },
        { code: 'KR', dial: '+82', flag: '🇰🇷' },
        { code: 'HK', dial: '+852', flag: '🇭🇰' },
      ],
    }
  },

  computed: {
    // 根据当前countryCode查找对应国旗emoji(模板使用)
    countryFlag() {
      const found = this.countries.find((c) => c.dial === this.countryCode)
      return found?.flag || '🇺🇸'
    },
    tabs() {
      void this.localeVersion // 依赖 localeVersion 以便切换语言时刷新
      return [
        { value: 'email', label: i18n.t('auth.emailRegister') },
        { value: 'phone', label: i18n.t('auth.phoneRegister') },
      ]
    },
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

  onLoad() {
    // 订阅语言切换,刷新 tabs/petTypes 等本地化文案
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },
  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
    if (this._emailCodeTimer) clearInterval(this._emailCodeTimer)
    if (this._smsCodeTimer) clearInterval(this._smsCodeTimer)
  },

  methods: {
    async onRegister() {
      if (!this.canSubmit) {
        if (this.password !== this.confirmPassword) {
          uni.showToast({ title: i18n.t('auth.passwordMismatch'), icon: 'none' })
          return
        }
        return
      }
      uni.showLoading({ title: i18n.t('auth.registering'), mask: true })
      try {
        const isPhone = this.activeTab === 'phone'
        await this.userStore.register({
          nickname: this.nickname,
          email: isPhone ? null : this.email,
          phone: isPhone ? this.countryCode + this.phone : null,
          // 手机号注册必传短信验证码(后端 verifyPhoneCode 校验)
          smsCode: isPhone ? this.smsCode : null,
          password: this.password,
          petType: this.petType,
        })
        uni.hideLoading()
        uni.showToast({ title: i18n.t('auth.registerSuccess'), icon: 'success' })
        // 注册成功:reLaunch到首页 tab,清空注册/登录栈,避免用户按返回又回到登录页
        // (Amazon/Target/Shein 标准做法)
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/tabbar/home' })
        }, 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || i18n.t('auth.registerFailed'), icon: 'none' })
      }
    },

    onSendCode() {
      if (this.codeCountdown > 0) return
      if (!this.phone || this.phone.length < 8) {
        uni.showToast({ title: i18n.t('auth.phoneRequired'), icon: 'none' })
        return
      }
      // 启动倒计时(先禁用按钮,防止重复点击)
      this.codeCountdown = 60
      const timer = setInterval(() => {
        this.codeCountdown -= 1
        if (this.codeCountdown <= 0) {
          clearInterval(timer)
          this._smsCodeTimer = null
        }
      }, 1000)
      this._smsCodeTimer = timer
      // 真发短信:调 POST /api/v1/auth/phone/send-code,
      // purpose=REGISTER 让后端写入 mo_sms_code 时区分用途。
      // 失败时回滚倒计时,允许用户重试。
      const fullPhone = this.countryCode + this.phone
      sendPhoneCode(fullPhone, 'REGISTER')
        .then(() => {
          uni.showToast({ title: i18n.t('auth.codeSent'), icon: 'success' })
        })
        .catch((e) => {
          clearInterval(timer)
          this.codeCountdown = 0
          uni.showToast({ title: e.message || i18n.t('auth.codeSendFailed'), icon: 'none' })
        })
    },

    async onSendEmailCode() {
      if (this.emailCodeCountdown > 0) return
      if (!this.email?.includes('@')) {
        uni.showToast({ title: i18n.t('auth.invalidEmail'), icon: 'none' })
        return
      }
      // 启动倒计时(先禁用按钮,防止重复点击)
      this.emailCodeCountdown = 60
      const timer = setInterval(() => {
        this.emailCodeCountdown -= 1
        if (this.emailCodeCountdown <= 0) {
          clearInterval(timer)
          this._emailCodeTimer = null
        }
      }, 1000)
      this._emailCodeTimer = timer
      try {
        // 调用后端 POST /api/v1/auth/email/verify,真实验证码邮件发送
        await sendEmailVerification(this.email)
        uni.showToast({ title: i18n.t('auth.codeSent'), icon: 'success' })
      } catch (e) {
        // 失败时重置倒计时,允许用户重试
        clearInterval(timer)
        this.emailCodeCountdown = 0
        uni.showToast({ title: e.message || i18n.t('auth.codeSendFailed'), icon: 'none' })
      }
    },

    // 选择国家:更新区号(同时模板countryFlag会自动更新)
    selectCountry(c) {
      this.countryCode = c.dial
      this.showCountryPicker = false
    },

    // 社交登录:复用登录页逻辑,保持一致的授权入口
    onSocial(provider) {
      if (provider === 'google') {
        uni.showToast({
          title: i18n.t('auth.socialComingSoon', { provider: 'Google' }),
          icon: 'none',
        })
        return
      }
      if (provider === 'apple') {
        uni.showToast({
          title: i18n.t('auth.socialComingSoon', { provider: 'Apple' }),
          icon: 'none',
        })
        return
      }
      uni.showToast({
        title: `${provider} ${i18n.t('auth.socialComingSoon', { provider: '' })}`,
        icon: 'none',
      })
    },

    goLogin() {
      // 登录页与注册页互跳使用reLaunch避免栈叠加(美国电商标准做法)
      uni.navigateTo({ url: '/pages/user/login' })
    },

    // 打开服务条款 / 隐私政策(type=terms|privacy|qualification|license)
    openTerms(type) {
      uni.navigateTo({ url: `/pages/user/terms-document?type=${type}` })
    },
  },
}
</script>

<style lang="scss" scoped>
/* 美国购物APP注册页样式(参考Amazon/Target/Shein移动H5):
   - 纯白页面背景,无渐变(减少视觉噪音)
   - 单列表单,label在input上方(Baymard最佳实践:移动端Fitts法则)
   - 输入框边框1px圆角,聚焦高亮(Apple HIG 风格)
   - CTA全宽实心,高度≥96rpx,圆角999rpx(拇指区域≥72rpx)
   - 辅助文案、条款采用12-13sp弱灰,与CTA形成层级 */
.register {
  min-height: 100vh;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
}

/* 可滚动区:占满剩余高度,与底部登录入口解耦 */
.page-scroll {
  flex: 1;
  height: 0; /* flex子项高度0,使其内部scroll生效 */
}

.page-inner {
  padding: 40rpx 32rpx 0;
}

/* Hero标题:大号+加粗+价值主张副标题(Target注册页模式) */
.hero {
  margin-bottom: 40rpx;
}

.hero-title {
  display: block;
  font-size: 52rpx;
  font-weight: 800;
  line-height: 1.2;
  color: var(--color-text);
  margin-bottom: 12rpx;
  letter-spacing: -0.5rpx;
}

.hero-subtitle {
  display: block;
  font-size: 28rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
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
  border: 2rpx solid var(--color-divider);
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  transition: all 0.15s ease;
}

.social-btn:active {
  background: var(--color-fill-hi);
  transform: scale(0.99);
}

.social-btn.google {
  color: var(--color-text);
}

.social-btn.apple {
  background: var(--color-text);
  border-color: var(--color-text);
  color: var(--color-surface);
}

.social-logo {
  width: 36rpx;
  height: 36rpx;
}

/* Apple 图标在黑色按钮上需要显示为白色。
 * lucide svg 是 stroke="currentColor",用 <image> 加载后 currentColor 不生效,
 * 改用 filter:invert 把单色 SVG 翻成白色(0=全黑 → invert 后全白). */
.social-logo-apple {
  filter: brightness(0) invert(1);
}

@media (prefers-color-scheme: dark) {
  :root:not(.theme-light) .social-logo-apple {
    filter: brightness(0);
  }
}
.theme-dark .social-logo-apple {
  filter: brightness(0);
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
  background: var(--color-divider);
}

.divider-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 1rpx;
}

/* 表单:单列+垂直gap,字段之间呼吸感充足 */
.form {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
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
  color: var(--color-text);
  line-height: 1.4;
}

.field-input {
  height: 92rpx;
  padding: 0 24rpx;
  font-size: 30rpx;
  color: var(--color-text);
  background: var(--color-surface);
  border: 2rpx solid var(--color-fill-dim);
  border-radius: 16rpx;
  /* iOS焦点态:移除系统蓝框,自定义outline */
  outline: none;
  box-sizing: border-box;
}

.field-input:focus {
  border-color: var(--color-primary, #007aff);
  background: var(--color-surface);
  box-shadow: 0 0 0 4rpx rgba(0, 122, 255, 0.12);
}

/* 辅助文本(密码规则等) */
.helper-text {
  display: block;
  margin-top: -12rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: var(--color-text-tertiary);
}

/* 手机号字段:国旗+区号+下拉箭头+号码输入 */
.phone-field {
  display: flex;
  align-items: stretch;
  height: 92rpx;
  border: 2rpx solid var(--color-fill-dim);
  border-radius: 16rpx;
  overflow: hidden;
  background: var(--color-surface);
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
  border-right: 2rpx solid var(--color-divider);
  background: var(--background-50);
}

.country-flag {
  font-size: 32rpx;
  line-height: 1;
}

.country-dial {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.chevron {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
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
  color: var(--primary-foreground);
  font-size: 26rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.code-btn.disabled {
  background: var(--color-fill-dim-strong);
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
  color: var(--color-text-tertiary);
}

/* 注册方式切换:Segmented Control(iOS 16样式) */
.segmented {
  display: flex;
  background: var(--color-fill, #f2f2f7);
  border-radius: 14rpx;
  padding: 4rpx;
}

.segmented-item {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  font-size: 26rpx;
  color: var(--color-text);
  border-radius: 10rpx;
  font-weight: 500;
  transition: all 0.2s ease;
}

.segmented-item.active {
  background: var(--color-fill-raised);
  box-shadow: var(--shadow-card-sm);
  color: var(--color-text);
  font-weight: 700;
}

/* 宠物偏好chip行:水平排列,两端对齐,选中反色 */
.chip-row {
  display: flex;
  gap: 12rpx;
}

.pet-chip {
  flex: 1;
  height: 84rpx;
  border-radius: 16rpx;
  border: 2rpx solid var(--color-divider);
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  transition: all 0.2s ease;
}

.pet-chip.active {
  border-color: var(--color-primary, #007aff);
  background: rgba(0, 122, 255, 0.08);
}

.chip-emoji {
  font-size: 32rpx;
  line-height: 1;
}

.chip-label {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text);
}

.pet-chip.active .chip-label {
  color: var(--color-primary, #007aff);
}

/* 主CTA按钮:全宽实心胶囊(Amazon/Target风格) */
.cta-btn {
  margin-top: 8rpx;
  height: 104rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-primary) 100%);
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
  color: var(--primary-foreground);
}

.cta-btn.disabled {
  background: var(--color-fill-dim);
  box-shadow: none;
}

.cta-btn.disabled .cta-text {
  color: var(--color-text-tertiary);
}

/* 条款区:CTA下方12sp小字居中,不抢主流程注意力 */
.terms {
  margin-top: 24rpx;
  text-align: center;
  padding: 0 8rpx;
}

.terms-text {
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--color-text-tertiary);
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
  background: var(--color-fill-hi);
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
  color: var(--color-text);
  line-height: 1.4;
}

/* 底部登录入口:弱色分隔+居中文字(Target/Shein标准) */
.bottom-login {
  margin: 56rpx 32rpx 24rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid var(--color-divider);
  text-align: center;
}

.bottom-login-text {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.bottom-login-link {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-primary, #007aff);
  margin-left: 6rpx;
}

.safe-bottom {
  height: calc(32rpx + env(safe-area-inset-bottom));
}

/* 国家选择弹层:底部sheet(iOS 16 UIActionSheet模式) */
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
  background: var(--color-surface);
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
  border-bottom: 1rpx solid var(--color-divider);
}

.sheet-title {
  flex: 1;
  text-align: center;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--color-text);
}

.sheet-close {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: var(--color-text-tertiary);
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
  border-bottom: 1rpx solid var(--color-border, #f2f2f7);
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
  color: var(--color-text);
  font-weight: 500;
}

.ci-dial {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  font-weight: 600;
}
</style>
