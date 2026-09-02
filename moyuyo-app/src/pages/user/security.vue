<template>
  <view class="security">
    <!-- 安全状态概览卡片(根据 userInfo + deviceCount 动态计算评分) -->
    <view class="status-card">
      <view class="status-icon">
        <text class="icon-shield luc-shield" />
      </view>
      <view class="status-info">
        <view class="status-row">
          <text class="status-title">{{ $t('security.statusTitleFmt', { level: $t(`security.level.${securityStatus.level}`) }) }}</text>
          <view class="status-badge" :class="`status-badge--${securityStatus.level}`">{{ $t(`security.badge.${securityStatus.badgeKey}`) }}</view>
        </view>
        <text class="status-desc">{{ $t('security.statusDescFmt', { state: $t(`security.state.${securityStatus.stateKey}`) }) }}</text>
        <text class="status-hint">{{ $t('security.statusHintFmt', { count: securityStatus.suggestionCount }) }}</text>
      </view>
    </view>

    <!-- 登录密码 -->
    <view class="section">
      <text class="section-label">{{ $t('security.sectionPassword') }}</text>
      <view class="row" @click="onChangePassword">
        <view class="row-left">
          <text class="row-icon luc-lock" />
          <text class="row-text">{{ $t('security.changePassword') }}</text>
        </view>
        <view class="row-right">
          <view class="badge-safe">{{ $t('security.passwordSet') }}</view>
          <text class="row-arrow luc-chevron-right" />
        </view>
      </view>
    </view>

    <!-- 两步验证 -->
    <view class="section">
      <text class="section-label">{{ $t('security.section2FA') }}</text>
      <view class="row">
        <view class="row-left">
          <text class="row-icon luc-smartphone" />
          <text class="row-text">{{ $t('security.enable2FA') }}</text>
        </view>
        <view class="toggle" :class="{ active: tfaEnabled, disabled: toggling2FA }" @click="onToggle2FA">
          <view class="toggle-knob" />
        </view>
      </view>
      <view class="divider" />
      <view class="row" @click="goTwoFactor">
        <view class="row-left">
          <text class="row-icon luc-key" />
          <text class="row-text">{{ $t('security.verifyMethod') }}</text>
        </view>
        <view class="row-right">
          <text class="row-value">{{ $t('security.totpMethod') }}</text>
          <text class="row-arrow luc-chevron-right" />
        </view>
      </view>
    </view>

    <!-- 登录设备管理 -->
    <view class="section">
      <text class="section-label">{{ $t('security.sectionDevices') }}</text>
      <view class="row" @click="goDevices">
        <view class="row-left">
          <text class="row-icon luc-laptop" />
          <text class="row-text">{{ $t('security.manageDevices') }}</text>
        </view>
        <view class="row-right">
          <text class="row-value">{{ $t('security.deviceCountFmt', { current: deviceCount, max: deviceMax }) }}</text>
          <text class="row-arrow luc-chevron-right" />
        </view>
      </view>
    </view>

    <!-- 第三方账号:
    后端 mo_user 仅保留单一 oauthProvider + oauthUid(单 provider 模型),
    不支持"邮箱注册后再去绑定 Apple / Google"。
    这里只展示真实登录方式一行 + 简短说明,避免出现"绑定按钮点了没反应"的误导。
    后续后端补多 provider 绑定接口时再扩展此区块。 -->
    <view class="section">
      <text class="section-label">{{ $t('security.sectionThirdParty') }}</text>
      <text class="section-note">{{ $t('security.oauthNote') }}</text>
      <view class="row">
        <view class="row-left">
          <text class="row-icon luc" :class="$luc(oauthMethod.icon)" />
          <text class="row-text">{{ $t('security.oauthMethodLabel', { method: oauthMethod.label }) }}</text>
        </view>
        <view class="row-right">
          <view class="badge-safe">{{ $t('security.bound') }}</view>
        </view>
      </view>
    </view>

    <!-- 开启 2FA 验证码弹窗:
      1) 打开后自动调 /2fa/send
      2) 用户输入 6 位数字 → 调 /2fa/verify → 通过后调 PUT /2fa {enabled:true}
      3) 后端 verified 缓存 + Redis 双层校验已完成"开启前的二次身份验证"语义
      4) 关闭弹窗 / 离开页面 时清理倒计时与错误状态 -->
    <view v-if="verifyModal.visible" class="modal-mask" @click.self="closeVerifyModal">
      <view class="modal-card">
        <text class="modal-title">{{ $t('security.twoFactorVerifyTitle') }}</text>
        <text class="modal-desc">{{ $t('security.twoFactorVerifyContent') }}</text>
        <text class="modal-hint">{{ $t('security.twoFactorCodeDevHint') }}</text>
        <view class="modal-input-row">
          <text class="modal-label">{{ $t('security.twoFactorVerifyCodeLabel') }}</text>
          <input
            v-model="verifyModal.code"
            class="modal-input"
            type="number"
            maxlength="6"
            :placeholder="$t('security.twoFactorVerifyCodePlaceholder')"
            :disabled="verifyModal.verifying"
            @input="onVerifyCodeInput"
          />
        </view>
        <text v-if="verifyModal.errorMsg" class="modal-error">{{ verifyModal.errorMsg }}</text>
        <view class="modal-actions">
          <view class="btn-modal-secondary" @click="closeVerifyModal">
            <text>{{ $t('security.twoFactorEnableCancel') }}</text>
          </view>
          <view
            v-if="verifyModal.countdown > 0"
            class="btn-modal-secondary btn-modal-resend disabled"
          >
            <text>{{ $t('security.twoFactorResendAfter', { seconds: verifyModal.countdown }) }}</text>
          </view>
          <view
            v-else
            class="btn-modal-secondary btn-modal-resend"
            :class="{ disabled: verifyModal.sending }"
            @click="sendVerifyCode"
          >
            <text>{{ $t('security.twoFactorResend') }}</text>
          </view>
          <view
            class="btn-modal-primary"
            :class="{ disabled: verifyModal.verifying || !isCodeValid }"
            @click="submitVerifyAndEnable"
          >
            <text>{{ $t('security.twoFactorEnableConfirm') }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { i18n } from '@/i18n'
import { listDevices } from '@/api/device'

export default {
  pageTitleKey: 'pageTitle.userSecurity',

  data() {
    return {
      // 当前已登录设备数(由 onShow 调 /api/v1/devices 拉取,page=1 size=1 取 total)
      deviceCount: 0,
      // 设备上限(用于 deviceCountFmt 模板插值;按业务规则硬编码 3,后续可改为配置)
      deviceMax: 3,
      // 2FA 切换请求进行中(防止用户连点导致重复 PUT)
      toggling2FA: false,
      // 开启 2FA 验证码弹窗状态
      verifyModal: {
        // 弹窗可见性
        visible: false,
        // 6 位验证码输入
        code: '',
        // 发送请求 /校验 PUT中(用于按钮 disabled + loading)
        sending: false,
        // 校验(verify 接口)+ 提交 toggle 中
        verifying: false,
        // 倒计时(秒);0 表示可重发
        countdown: 0,
        // 倒计时 setInterval 句柄
        timer: null,
        // 错误信息(行内展示)
        errorMsg: '',
      },
      // i18n locale 版本号:locale 切换时自增,触发依赖 i18n 的 computed 重算
      localeVersion: 0,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /**
     * 安全评分状态:综合 userInfo + deviceCount 计算
     * 信号(满分 100):
     *  - 已设置密码 +30(未登录态视为未设置)
     *  - 邮箱已验证 +15
     *  - 开启 2FA +25
     *  - 设备数 <= deviceMax +20
     *  - 设备数 <= 1 +10
     *  等级:>=80 high / >=50 medium / else low
     * 建议条数:100 - score 映射为 0/1/2/3 条
     */
    securityStatus() {
      void this.localeVersion
      const u = this.userStore.userInfo || {}
      let score = 0
      const signals = []
      if (u.id) {
        score += 30
        signals.push('password')
      } else {
        signals.push('setPassword')
      }
      if (u.emailVerified) {
        score += 15
      } else {
        signals.push('verifyEmail')
      }
      if (u.twoFactorEnabled) {
        score += 25
      } else {
        signals.push('enable2FA')
      }
      if (this.deviceCount <= this.deviceMax) {
        score += 20
      } else {
        signals.push('tooManyDevices')
      }
      if (this.deviceCount <= 1) {
        score += 10
      } else if (this.deviceCount > 2) {
        signals.push('reduceDevices')
      }
      // 等级与文案 key
      let level
      let stateKey
      let badgeKey
      if (score >= 80) {
        level = 'high'
        stateKey = 'good'
        badgeKey = 'safe'
      } else if (score >= 50) {
        level = 'medium'
        stateKey = 'normal'
        badgeKey = 'medium'
      } else {
        level = 'low'
        stateKey = 'poor'
        badgeKey = 'risk'
      }
      // 建议条数 = signals 中"未达标"的项数(最多 3)
      const suggestionCount = Math.min(3, signals.length)
      return { level, stateKey, badgeKey, score, suggestionCount, signals }
    },
    /**
     * 真实登录方式:按 userInfo.oauthProvider 推算
     * - 空 / 邮箱注册: 显示"邮箱注册" + mail 图标
     * - apple / google / facebook: 显示对应平台 + 平台图标
     * 后端 mo_user 只保留单一 oauthProvider,这里只能反映真实登录来源
     */
    oauthMethod() {
      void this.localeVersion
      const provider = (this.userStore.userInfo?.oauthProvider || '').toLowerCase()
      if (!provider) {
        return { key: 'email', icon: 'mail', label: i18n.t('security.oauthMethodEmail') }
      }
      // 已知 provider 映射到 lucide icon + i18n 文案
      const known = {
        apple: { icon: 'apple' },
        google: { icon: 'link' },
        facebook: { icon: 'user' },
        wechat: { icon: 'message-circle' },
      }
      const meta = known[provider] || { icon: 'key' }
      return {
        key: provider,
        icon: meta.icon,
        // 优先用 i18n.accountNames 复用现有 key,缺则直接显示 provider 名
        label: i18n.t(`security.accountNames.${provider}`, null) || provider.toUpperCase(),
      }
    },
    tfaEnabled: {
      get() {
        return this.userStore.userInfo?.twoFactorEnabled || false
      },
      // 只读 getter;切换走 onToggle2FA(因 store.toggle2FA 是 async,Vue computed setter 不支持)
      set() {},
    },
    /**
     * 验证码是否合法(6 位数字),用于"确认"按钮 disabled 控制
     */
    isCodeValid() {
      return /^\d{6}$/.test(this.verifyModal.code || '')
    },
  },

  // 首次进入:订阅 i18n locale,拉取真实设备数;保证 userInfo 最新(若 store 中无则拉一次)
  async onShow() {
    if (!this._unsubLocale) {
      this._unsubLocale = i18n.subscribe(() => {
        this.localeVersion += 1
      })
    }
    if (this.userStore.isLoggedIn && !this.userStore.userInfo) {
      // 极端情况:token 在但 userInfo 被清空,补一次拉取
      try {
        await this.userStore.fetchProfile()
      } catch (e) {
        console.warn('[security] fetchProfile failed', e)
      }
    }
    await this.loadDeviceCount()
  },

  onUnload() {
    // 卸载时清理 i18n 订阅与验证码弹窗的倒计时 timer,避免内存泄漏
    if (this._unsubLocale) {
      this._unsubLocale()
      this._unsubLocale = null
    }
    this.stopCountdown()
  },

  methods: {
    /**
     * 从 /api/v1/devices 取设备总数:page=1 size=1,读 IPage.total
     * 异常时保留旧值,UI 不至于瞬间归零
     */
    async loadDeviceCount() {
      if (!this.userStore.isLoggedIn) {
        this.deviceCount = 0
        return
      }
      try {
        const r = await listDevices({ page: 1, size: 1 })
        // 兼容两种返回:IPage(total) 或老版 array
        if (r && typeof r === 'object' && 'total' in r) {
          this.deviceCount = Number(r.total) || 0
        } else if (Array.isArray(r)) {
          this.deviceCount = r.length
        } else {
          this.deviceCount = 0
        }
      } catch (e) {
        console.warn('[security] loadDeviceCount failed', e)
        // 接口失败时保留旧值,避免 UI 跳动
      }
    },

    onChangePassword() {
      uni.navigateTo({ url: '/pages/user/change-password' })
    },

    /**
     * 切换 2FA 开关:
     * - 开启(toggle off -> on): 走 openVerifyModal -> 验证码弹窗 -> send/verify/PUT
     * - 关闭(toggle on -> off): 直接 PUT,无需验证码(主动关闭是预期 UX)
     * - toggling2FA 防止用户连点导致重复请求
     */
    async onToggle2FA() {
      if (this.toggling2FA) return
      if (!this.userStore.isLoggedIn) {
        uni.showToast({ title: i18n.t('security.loginRequired'), icon: 'none' })
        return
      }
      const target = !this.tfaEnabled
      if (target) {
        // 开启:打开验证码弹窗(里面完成确认 + 验证码 + PUT)
        await this.openVerifyModal()
        return
      }
      // 关闭:直接 PUT
      this.toggling2FA = true
      try {
        await this.userStore.toggle2FA(false)
        uni.showToast({ title: i18n.t('security.twoFactorOff'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: i18n.t('security.twoFactorFailed'), icon: 'none' })
        console.warn('[security] disable2FA failed', e)
      } finally {
        this.toggling2FA = false
      }
    },

    /**
     * 打开开启 2FA 的验证码弹窗:
     * 1) 弹 uni.showModal 让用户先确认"开启"意图(防止误触)
     * 2) 显示自定义 modal(支持输入),自动调 /2fa/send
     * 3) 60 秒倒计时,期间按钮置灰
     */
    async openVerifyModal() {
      const ok = await new Promise((resolve) => {
        uni.showModal({
          title: i18n.t('security.twoFactorEnableTitle'),
          content: i18n.t('security.twoFactorEnableContent'),
          confirmText: i18n.t('security.twoFactorEnableConfirm'),
          cancelText: i18n.t('security.twoFactorEnableCancel'),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!ok) return
      // 打开验证码 modal
      this.verifyModal = {
        visible: true,
        code: '',
        sending: false,
        verifying: false,
        countdown: 0,
        timer: null,
        errorMsg: '',
      }
      // 自动发送
      await this.sendVerifyCode()
    },

    closeVerifyModal() {
      // 用户主动关闭:不清缓存,因为开启并未实际发生;但要清掉倒计时 timer
      this.stopCountdown()
      this.verifyModal.visible = false
      this.verifyModal.errorMsg = ''
      this.verifyModal.code = ''
      this.verifyModal.sending = false
      this.verifyModal.verifying = false
    },

    /**
     * 输入框变化:自动清掉上次的错误提示,让用户重新尝试
     */
    onVerifyCodeInput() {
      if (this.verifyModal.errorMsg) this.verifyModal.errorMsg = ''
    },

    /** 调 /2fa/send:启动 60 秒倒计时 */
    async sendVerifyCode() {
      if (this.verifyModal.sending) return
      this.verifyModal.sending = true
      this.verifyModal.errorMsg = ''
      try {
        await this.userStore.sendTwoFactorCode()
        this.startCountdown(60)
      } catch (e) {
        this.verifyModal.errorMsg = i18n.t('security.twoFactorSendFailed')
        console.warn('[security] sendTwoFactorCode failed', e)
      } finally {
        this.verifyModal.sending = false
      }
    },

    startCountdown(seconds) {
      this.stopCountdown()
      this.verifyModal.countdown = seconds
      // setInterval 在小程序/H5 都可用;切换页面 / 关闭弹窗时务必 stopCountdown
      this.verifyModal.timer = setInterval(() => {
        this.verifyModal.countdown -= 1
        if (this.verifyModal.countdown <= 0) {
          this.stopCountdown()
        }
      }, 1000)
    },

    stopCountdown() {
      if (this.verifyModal.timer) {
        clearInterval(this.verifyModal.timer)
        this.verifyModal.timer = null
      }
    },

    /**
     * 提交验证码并开启 2FA:
     * 1) 校验 6 位格式
     * 2) 调 userStore.enable2FAWithCode(code):内部依次 send/verify/PUT
     *    (store 内部不调 PUT 之前还会再 send 一次以保证最新码,前端无需重复)
     * 3) 成功关闭弹窗 + toast 开启成功;失败在弹窗内展示错误,不关闭
     */
    async submitVerifyAndEnable() {
      if (this.verifyModal.verifying) return
      const code = (this.verifyModal.code || '').trim()
      if (!/^\d{6}$/.test(code)) {
        this.verifyModal.errorMsg = i18n.t('security.twoFactorInvalidCode')
        return
      }
      this.verifyModal.verifying = true
      this.verifyModal.errorMsg = ''
      try {
        await this.userStore.enable2FAWithCode(code)
        // 成功
        this.stopCountdown()
        this.verifyModal.visible = false
        uni.showToast({ title: i18n.t('security.twoFactorOn'), icon: 'none' })
      } catch (e) {
        // 区分错误:验证码错 / 其它服务端错误
        const msg = (e && e.message) || ''
        if (/invalid|expired|mismatch|验证码/.test(msg)) {
          this.verifyModal.errorMsg = i18n.t('security.twoFactorCodeMismatch')
        } else {
          this.verifyModal.errorMsg = i18n.t('security.twoFactorVerifyFailed')
        }
        console.warn('[security] verify/enable failed', e)
      } finally {
        this.verifyModal.verifying = false
      }
    },

    goTwoFactor() {
      uni.navigateTo({ url: '/pages/user/two-factor' })
    },

    goDevices() {
      uni.navigateTo({ url: '/pages/user/devices' })
    },

    onMergeAccount() {
      uni.showToast({ title: i18n.t('security.mergeAccountToast'), icon: 'none' })
    },

    onLockRecords() {
      uni.showToast({ title: i18n.t('security.lockRecordsToast'), icon: 'none' })
    },

    onDeleteAccount() {
      uni.showModal({
        title: i18n.t('security.deleteAccountTitle'),
        content: i18n.t('security.deleteAccountContent'),
        confirmText: i18n.t('security.deleteAccountConfirm'),
        confirmColor: '#C96E5F',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: i18n.t('security.deleteAccountDone'), icon: 'none' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.security {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

/* 安全状态概览卡片 */
.status-card {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
}

.status-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: rgba(171, 185, 173, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-shield {
  font-size: 40rpx;
}

.status-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.status-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

/* 安全评分徽章:按 level 切换色系
  - high(safe):绿色(原 .status-badge-safe 配色)
  - medium(fair):琥珀色
  - low(risk):红色 */
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
}

.status-badge--high {
  background: rgba(171, 185, 173, 0.15);
  color: var(--color-success);
}

.status-badge--medium {
  background: rgba(241, 186, 78, 0.18);
  color: #b07613;
}

.status-badge--low {
  background: rgba(201, 110, 95, 0.18);
  color: #c96e5f;
}

/* 兼容旧 class(防止其它地方引用) */
.status-badge-safe {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: rgba(171, 185, 173, 0.15);
  color: var(--color-success);
}

.status-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.status-hint {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 区块 */
.section {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.section-label {
  display: block;
  padding: 20rpx 24rpx 8rpx;
  font-size: 22rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-tertiary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

/* section 顶部说明文字:用于第三方账号区块提示"多渠道绑定暂未开放" */
.section-note {
  display: block;
  padding: 0 24rpx 16rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

/* 行项目 */
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 104rpx;
  padding: 0 24rpx;
}

.row-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex: 1;
  min-width: 0;
}

.row-icon {
  font-size: 40rpx;
  width: 40rpx;
  text-align: center;
  flex-shrink: 0;
}

.row-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.row-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.row-value {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.row-arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

/* 分隔线 */
.divider {
  height: 1rpx;
  background: var(--color-divider);
  margin-left: 80rpx;
}

/* 徽章 */
.badge-safe {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: rgba(171, 185, 173, 0.15);
  color: var(--color-success);
}

.badge-unlinked {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4rpx 16rpx;
  border-radius: var(--radius-pill);
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

/* Toggle 开关 */
.toggle {
  position: relative;
  width: 102rpx;
  height: 62rpx;
  border-radius: 999rpx;
  background: var(--color-divider);
  cursor: pointer;
  transition: background-color 0.2s ease;
  flex-shrink: 0;
}

.toggle.active {
  background: var(--color-primary);
}

/* 切换请求进行中:禁用点击 + 透明,避免用户连点造成重复请求 */
.toggle.disabled {
  opacity: 0.6;
  pointer-events: none;
}

.toggle-knob {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 54rpx;
  height: 54rpx;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s cubic-bezier(0.32, 0.72, 0, 1);
}

.toggle.active .toggle-knob {
  transform: translateX(40rpx);
}

/* ===== 验证码弹窗 ===== */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 48rpx;
}

.modal-card {
  width: 100%;
  max-width: 600rpx;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 40rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.modal-desc {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.modal-hint {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  line-height: 1.4;
}

.modal-title {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.modal-input-row {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-top: 8rpx;
}

.modal-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.modal-input {
  height: 88rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  font-size: 32rpx;
  letter-spacing: 0.2em;
  background: var(--color-background);
  color: var(--color-text);
}

.modal-error {
  font-size: 24rpx;
  color: var(--color-danger);
  line-height: 1.4;
}

.modal-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.btn-modal-primary,
.btn-modal-secondary {
  flex: 1 1 0;
  min-width: 200rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  border-radius: var(--radius-md);
  font-size: 26rpx;
  font-weight: var(--font-weight-medium);
  transition: opacity 0.15s ease;
}

.btn-modal-primary {
  background: var(--color-primary);
  color: var(--color-text);
}

.btn-modal-secondary {
  background: var(--color-background);
  color: var(--color-text);
  border: 1rpx solid var(--color-divider);
}

/* 倒计时中/不可点:降低透明度 + 禁用 pointer */
.btn-modal-resend.disabled,
.btn-modal-primary.disabled {
  opacity: 0.5;
  pointer-events: none;
}

/* 注销账号 */
.delete-section {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 104rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-top: 8rpx;
}

.delete-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-danger);
}
</style>
