<template>
  <view class="phone">
    <!-- 顶部导航栏:与其他账号类页面保持一致的 custom 导航样式 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('phone.title') }}</text>
    </view>

    <view class="content">
      <!-- 当前手机号卡片 -->
      <view class="card">
        <text class="card-label">{{ $t('phone.current') }}</text>
        <text class="card-value">
          {{ maskedPhone || $t('settings.phoneNotBound') }}
        </text>
      </view>

      <!-- 新手机号输入(国家码 + 本地号) -->
      <view class="field">
        <text class="field-label">{{ $t('phone.newPhone') }}</text>
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
            maxlength="15"
            inputmode="numeric"
            :placeholder="$t('phone.newPhonePlaceholder')"
          >
        </view>
      </view>

      <!-- 验证码输入 + 发送按钮 -->
      <view class="field">
        <text class="field-label">{{ $t('phone.code') }}</text>
        <view class="code-field">
          <input
            v-model="code"
            class="field-input"
            type="number"
            maxlength="6"
            inputmode="numeric"
            :placeholder="$t('phone.codePlaceholder')"
            :disabled="codeCountdown > 0"
          >
          <view
            class="code-btn"
            :class="{ disabled: codeCountdown > 0 || sendingCode }"
            @click="onSendCode"
          >
            <text v-if="codeCountdown === 0">{{ $t('phone.sendCode') }}</text>
            <text v-else>{{ codeCountdown }}s</text>
          </view>
        </view>
      </view>

      <!-- 提交按钮 -->
      <view class="btn btn-primary submit-btn" :class="{ disabled: !canSubmit }" @click="onSubmit">
        {{ submitting ? $t('phone.submitting') : $t('phone.submit') }}
      </view>
    </view>

    <!--
      国家选择弹层(自实现,避开 APP 端 uni.showActionSheet 文案不可改的问题)
      国家列表与 register.vue 一致,保证业务侧表现统一
    -->
    <view v-if="showCountryPicker" class="country-sheet-mask" @click="showCountryPicker = false">
      <view class="country-sheet" @click.stop>
        <view class="sheet-header">
          <text class="sheet-title">{{ $t('phone.selectCountry') }}</text>
          <view class="sheet-close" @click="showCountryPicker = false">
            <text class="luc luc-x" />
          </view>
        </view>
        <scroll-view scroll-y class="sheet-list">
          <view
            v-for="c in countries"
            :key="c.code"
            class="country-item"
            :class="{ active: c.dial === countryCode }"
            @click="selectCountry(c)"
          >
            <text class="ci-flag">{{ c.flag }}</text>
            <text class="ci-name">{{ c.name }}</text>
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
import { sendPhoneCode } from '@/api/user'

export default {
  pageTitleKey: 'pageTitle.userPhone',

  data() {
    return {
      // 新手机号(本地号,不含国家码)
      phone: '',
      // 6 位数字验证码
      code: '',
      // 国家码:默认 +1(US),对齐目标市场(欧美);若当前账号已绑定手机号则按当前号推断覆盖
      countryCode: '+1',
      // 国家选择弹窗可见性
      showCountryPicker: false,
      // 发码倒计时(秒),0 表示可重新发送
      codeCountdown: 0,
      // 发送验证码请求中(避免重复点击)
      sendingCode: false,
      // 提交请求中
      submitting: false,
      // 国家列表:与 register.vue 保持一致
      countries: [
        { code: 'US', dial: '+1', flag: '🇺🇸', name: 'United States' },
        { code: 'CA', dial: '+1', flag: '🇨🇦', name: 'Canada' },
        { code: 'GB', dial: '+44', flag: '🇬🇧', name: 'United Kingdom' },
        { code: 'AU', dial: '+61', flag: '🇦🇺', name: 'Australia' },
        { code: 'CN', dial: '+86', flag: '🇨🇳', name: '中国' },
        { code: 'JP', dial: '+81', flag: '🇯🇵', name: '日本' },
        { code: 'KR', dial: '+82', flag: '🇰🇷', name: '한국' },
        { code: 'HK', dial: '+852', flag: '🇭🇰', name: 'Hong Kong' },
      ],
      // 倒计时 setInterval 句柄(onUnload 清理)
      timer: null,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /**
     * 脱敏手机号:与 settings.vue 保持一致,中间 4 位掩码
     */
    maskedPhone() {
      const raw = this.userStore?.userInfo?.phone
      if (!raw) return ''
      const s = String(raw).replace(/\s+/g, '')
      if (s.length < 7) return s
      return `${s.slice(0, 3)}****${s.slice(-4)}`
    },
    /**
     * 当前国家码对应的国旗 emoji(根据 countryCode 实时计算)
     */
    countryFlag() {
      const found = this.countries.find((c) => c.dial === this.countryCode)
      return found?.flag || '🇺🇸'
    },
    /**
     * 完整手机号(= 国家码 + 本地号),提交 / 发码都用它
     */
    fullPhone() {
      return `${this.countryCode}${this.phone}`
    },
    /**
     * 表单可提交条件:本地号 ≥ 8 位 + 验证码 6 位 + 不在提交中
     */
    canSubmit() {
      if (this.submitting) return false
      if (!this.phone || this.phone.length < 8) return false
      if (!/^\d{6}$/.test(this.code || '')) return false
      return true
    },
  },

  onLoad() {
    // 若当前账号已绑定手机号,推断国家码 → 让用户在同一栏内直接修改尾号
    const raw = this.userStore?.userInfo?.phone
    if (raw && typeof raw === 'string') {
      const s = raw.replace(/\s+/g, '')
      // 取最长匹配的国家码(避免 +1 +86 等短前缀误匹配)
      const match = this.countries
        .slice()
        .sort((a, b) => b.dial.length - a.dial.length)
        .find((c) => s.startsWith(c.dial))
      if (match) {
        this.countryCode = match.dial
        this.phone = s.slice(match.dial.length)
      }
    }
  },

  onUnload() {
    // 清理倒计时 timer,避免内存泄漏
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    /** 选择国家:更新区号 */
    selectCountry(c) {
      this.countryCode = c.dial
      this.showCountryPicker = false
    },

    /**
     * 发送验证码(purpose=CHANGE_PHONE):
     * 1) 校验手机号基本格式
     * 2) 启动 60 秒倒计时(防连点)
     * 3) 调 sendPhoneCode(phone, 'CHANGE_PHONE')
     * 4) 失败回滚倒计时,允许用户重试
     */
    onSendCode() {
      if (this.codeCountdown > 0 || this.sendingCode) return
      if (!this.phone || this.phone.length < 8) {
        uni.showToast({ title: i18n.t('phone.phoneRequired'), icon: 'none' })
        return
      }
      this.sendingCode = true
      this.codeCountdown = 60
      const timer = setInterval(() => {
        this.codeCountdown -= 1
        if (this.codeCountdown <= 0) {
          clearInterval(timer)
          this.timer = null
        }
      }, 1000)
      this.timer = timer
      sendPhoneCode(this.fullPhone, 'CHANGE_PHONE')
        .then(() => {
          uni.showToast({ title: i18n.t('phone.codeSent'), icon: 'success' })
        })
        .catch((e) => {
          // 失败:回滚倒计时,允许立即重试
          clearInterval(timer)
          this.timer = null
          this.codeCountdown = 0
          uni.showToast({
            title: e.message || i18n.t('phone.codeSendFailed'),
            icon: 'none',
          })
        })
        .finally(() => {
          this.sendingCode = false
        })
    },

    /**
     * 提交:PUT /api/v1/users/me/phone(由 store.changePhone 统一调)
     * 成功后 store 已自动刷新 userInfo.phone,直接 toast + 返回上一级
     */
    async onSubmit() {
      if (!this.canSubmit) {
        uni.showToast({ title: i18n.t('phone.checkInput'), icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await this.userStore.changePhone(this.fullPhone, this.code)
        uni.showToast({ title: i18n.t('phone.submitted'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.showToast({
          title: e.message || i18n.t('phone.failed'),
          icon: 'none',
        })
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.phone {
  min-height: 100vh;
  background: var(--color-background);
}

/* 顶部导航栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: calc(88rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  padding-top: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  top: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

/* 内容区 */
.content {
  padding: 32rpx 32rpx 64rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

/* 当前手机号卡片 */
.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.card-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.card-value {
  font-size: 40rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: 0.02em;
}

/* 表单字段:与 register.vue 风格一致 */
.field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.field-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  padding-left: 8rpx;
}

.field-input {
  display: block;
  width: 100%;
  height: 92rpx;
  padding: 0 24rpx;
  font-size: var(--font-size-base);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  box-sizing: border-box;
}

/* 手机号行:国家码 + 本地号横排 */
.phone-field {
  display: flex;
  align-items: stretch;
  height: 92rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.country-picker {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 0 20rpx;
  border-right: 1rpx solid var(--color-divider);
  flex-shrink: 0;
}

.country-flag {
  font-size: 32rpx;
  line-height: 1;
}

.country-dial {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.phone-input {
  flex: 1;
  height: 100%;
  border: none;
  background: transparent;
}

.chevron {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

/* 验证码行:输入框 + 发送按钮横排 */
.code-field {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.code-field .field-input {
  flex: 1;
}

.code-btn {
  flex-shrink: 0;
  height: 92rpx;
  padding: 0 28rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: var(--radius-md);
}

.code-btn.disabled {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

/* 提交按钮 */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.btn-primary {
  background: var(--color-primary);
  color: var(--color-text);
}

.submit-btn.disabled {
  opacity: 0.5;
}

/* ====== 国家选择弹层 ====== */
.country-sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.country-sheet {
  width: 100%;
  max-height: 70vh;
  background: var(--color-surface);
  border-top-left-radius: 24rpx;
  border-top-right-radius: 24rpx;
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  flex-direction: column;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx 16rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.sheet-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.sheet-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
}

.sheet-list {
  max-height: 60vh;
}

.country-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 32rpx;
}

.country-item.active {
  background: rgba(0, 122, 255, 0.08);
}

.ci-flag {
  font-size: 32rpx;
}

.ci-name {
  flex: 1;
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.ci-dial {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
</style>
