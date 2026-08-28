<template>
  <view class="withdraw">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">{{ $t('withdraw.title') }}</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <!-- 余额卡片(与钱包/充值页同款品牌渐变) -->
      <view class="balance-card">
        <view class="balance-bg" />
        <view class="balance-content">
          <text class="balance-label">{{ $t('withdraw.balanceLabel') }}</text>
          <text class="balance-amount">{{ currencySymbol }}{{ balanceFormatted }}</text>
          <text class="balance-note">{{ $t('withdraw.balanceNote') }}</text>
        </view>
      </view>

      <!-- 提现金额输入 -->
      <view class="section">
        <text class="section-title">{{ $t('withdraw.sectionAmount') }}</text>
        <view class="amount-input-wrap">
          <text class="currency-sign">{{ currencySymbol }}</text>
          <input
            v-model="amount"
            class="amount-field"
            type="digit"
            :placeholder="$t('withdraw.inputPlaceholder')"
            :maxlength="maxAmountLen"
            @input="onAmountInput"
          >
        </view>
        <view class="amount-actions">
          <view
            v-for="opt in quickAmounts"
            :key="opt"
            class="quick-amount"
            :class="{ active: amount === String(opt) }"
            @click="onQuickPick(opt)"
          >
            <text>{{ currencySymbol }}{{ opt }}</text>
          </view>
          <view class="quick-amount" @click="onWithdrawAll">
            <text>{{ $t('withdraw.quickAll') }}</text>
          </view>
        </view>
        <text class="amount-hint">
          {{
            $t('withdraw.hint', {
              minAmount: `${currencySymbol}${minAmount}`,
              maxAmount: maxAmountLabel,
            })
          }}
        </text>
      </view>

      <!-- 提现账户 -->
      <view class="section">
        <text class="section-title">{{ $t('withdraw.accountTitle') }}</text>
        <view class="account-item">
          <text class="account-icon luc luc-banknote" />
          <view class="account-info">
            <text class="account-name">{{ $t('withdraw.accountName') }}</text>
            <text class="account-desc">{{ $t('withdraw.accountDesc') }}</text>
          </view>
        </view>
      </view>

      <!-- 提交按钮 -->
      <view
        class="submit-btn"
        :class="{
          disabled: submitting || (verified && !canSubmit),
          'is-verify': !verified && !submitting,
        }"
        @click="onSubmit"
      >
        <text>
          {{
            submitting
              ? $t('common.loading')
              : verified
                ? $t('withdraw.submit')
                : $t('withdraw.verifyButton')
          }}
        </text>
      </view>
      <!-- 禁用原因提示:按钮不可点击时给用户清晰反馈 -->
      <!-- 未实名时按钮本身就是认证引导,不显示'请先完成实名认证'红字以免重复 -->
      <text v-if="verified && !canSubmit && !submitting" class="submit-reason">
        {{ submitDisabledReason }}
      </text>

      <text class="footer-tip">{{ $t('withdraw.footerTip') }}</text>
    </view>
  </view>
</template>

<script>
import { memberApi } from '@/api'
import { i18n } from '@/i18n'

// 单笔提现最低限额与最高限额:风控规则,防止误操作与恶意套现
const MIN_AMOUNT = 10
const MAX_SINGLE_AMOUNT = 5000

export default {
  data() {
    return {
      amount: '',
      submitting: false,
      currentBalance: 0,
      minAmount: MIN_AMOUNT,
      maxAmount: MAX_SINGLE_AMOUNT,
      // 实名认证状态:占位读取,后端返回 user.verified
      // 安全默认:false。等后端返回 verified 字段时再覆盖,避免后端未实现时所有用户被放行
      verified: false,
      // 提现账户占位:实际项目应从用户绑定信息读取
      accountName: '',
      accountDesc: '',
      quickAmounts: [50, 100, 200, 500],
      // locale 版本号:locale 切换时自增,触发 computed 重算
      localeVersion: 0,
    }
  },

  computed: {
    balanceFormatted() {
      const n = Number(this.currentBalance) || 0
      return n.toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    },
    /**
     * 当前语言货币符号:locale 切换时通过 localeVersion 触发重算
     */
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
    /**
     * 最大金额的本地化标签(用于 hint 文案插值)
     */
    maxAmountLabel() {
      void this.localeVersion
      return `${this.currencySymbol}${this.maxAmount.toLocaleString('en-US')}`
    },
    /**
     * 输入长度上限:整数位 + 小数点 + 2 位小数,防止输入超长
     */
    maxAmountLen() {
      const cap = Math.min(this.currentBalance, this.maxAmount)
      const maxInt = String(Math.floor(Math.max(cap, 0))).length
      return maxInt + 3
    },
    canSubmit() {
      if (!this.verified) return false
      const v = Number(this.amount)
      return (
        Number.isFinite(v) && v >= this.minAmount && v <= this.maxAmount && v <= this.currentBalance
      )
    },
    /**
     * 提交按钮的不可用原因:用于展示禁用文案(i18n)
     */
    submitDisabledReason() {
      void this.localeVersion
      if (!this.verified) return i18n.t('withdraw.reasonNotVerified')
      const v = Number(this.amount)
      if (!Number.isFinite(v) || v <= 0) return i18n.t('withdraw.reasonEmptyAmount')
      if (v < this.minAmount) {
        return i18n.t('withdraw.reasonBelowMin', { min: `${this.currencySymbol}${this.minAmount}` })
      }
      if (v > this.maxAmount) {
        return i18n.t('withdraw.reasonAboveMax', {
          max: `${this.currencySymbol}${this.maxAmount.toLocaleString('en-US')}`,
        })
      }
      if (v > this.currentBalance) return i18n.t('withdraw.reasonOverBalance')
      return ''
    },
  },

  onLoad() {
    this.loadBalance()
    // 订阅 locale 变化
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadBalance() {
      try {
        // showError: false 防止与本页 catch Toast 重复
        const data = await memberApi.getWallet({}, { showError: false })
        this.currentBalance = data?.balance || 0
        // 占位:后端 getWallet 返回 verified 字段时同步
        if (typeof data?.verified === 'boolean') this.verified = data.verified
      } catch {
        // 静默失败:用户仍可输入,提交时再做余额校验
      }
    },

    onAmountInput() {
      // 限制仅数字 + 小数点(uni-app type=digit 已过滤大部分非法字符)
      this.amount = String(this.amount).replace(/[^\d.]/g, '')
    },

    onQuickPick(v) {
      // 快速金额不得超过当前余额与单笔上限中的较小值
      const cap = Math.min(this.currentBalance, this.maxAmount)
      this.amount = v <= cap ? String(v) : String(Math.floor(cap))
    },

    onWithdrawAll() {
      if (this.currentBalance <= 0) {
        uni.showToast({ title: i18n.t('withdraw.toastNoBalance'), icon: 'none' })
        return
      }
      const cap = Math.min(this.currentBalance, this.maxAmount)
      this.amount = String(cap.toFixed(2))
    },

    /**
     * 点击认证占位:实际项目跳转到实名认证页
     */
    goVerify() {
      uni.showModal({
        title: i18n.t('withdraw.verifyTitle'),
        content: i18n.t('withdraw.verifyContent'),
        confirmText: i18n.t('withdraw.verifyAction'),
        success: (res) => {
          if (res.confirm) {
            // 占位:实际跳转到实名认证页面
            uni.navigateTo({ url: '/pages/user/settings' })
          }
        },
      })
    },

    async onSubmit() {
      if (this.submitting) return
      if (!this.verified) {
        this.goVerify()
        return
      }
      if (!this.canSubmit) {
        uni.showToast({
          title: this.submitDisabledReason || i18n.t('withdraw.failFallback'),
          icon: 'none',
        })
        return
      }
      const amount = Number(this.amount)
      this.submitting = true
      try {
        // 调用真实提现接口
        await memberApi.withdraw(amount)
        uni.showToast({ title: i18n.t('withdraw.success'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.showToast({ title: e?.message || i18n.t('withdraw.failFallback'), icon: 'none' })
      } finally {
        this.submitting = false
      }
    },

    goBack() {
      uni.navigateBack()
    },
  },
}
</script>

<style lang="scss" scoped>
.withdraw {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
}

.header-btn:first-child {
  left: 16rpx;
}

.header-btn:last-child {
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.content {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

/* 余额卡(品牌主色渐变,与 wallet/balance-recharge 同款) */
.balance-card {
  position: relative;
  overflow: hidden;
  border-radius: 24rpx;
  min-height: 220rpx;
  box-shadow: 0 8rpx 32rpx rgba(219, 201, 138, 0.35);
}

.balance-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #e8ddb5 0%, #dbc98a 50%, #c9b47a 100%);
}

.balance-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 80% 20%, rgba(255, 255, 255, 0.25) 0%, transparent 60%),
    radial-gradient(ellipse at 20% 80%, rgba(184, 166, 107, 0.25) 0%, transparent 50%);
}

.balance-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 32rpx;
}

.balance-label {
  font-size: 28rpx;
  color: rgba(46, 43, 41, 0.7);
}

.balance-amount {
  font-size: 72rpx;
  font-weight: var(--font-weight-semibold);
  color: #2e2b29;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.balance-note {
  font-size: 22rpx;
  color: rgba(46, 43, 41, 0.7);
}

/* 区块 */
.section {
  background: #ffffff;
  border: 1rpx solid #f2f2f7;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

/* 金额输入 */
.amount-input-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #f7f5f0;
  border-radius: 16rpx;
  padding: 24rpx;
}

.currency-sign {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}

.amount-field {
  flex: 1;
  font-size: 40rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

/* 快捷金额 */
.amount-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.quick-amount {
  flex: 1;
  min-width: 140rpx;
  padding: 16rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text);
  background: #f7f5f0;
  border-radius: 999rpx;
  transition: all 0.15s ease;
}

.quick-amount:active {
  transform: scale(0.97);
}

.quick-amount.active {
  background: var(--color-primary);
  color: #2e2b29;
  font-weight: 600;
}

.amount-hint {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 账户项 */
.account-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  background: #f7f5f0;
  border-radius: 16rpx;
}

.account-icon {
  font-size: 40rpx;
  color: var(--color-primary-dark);
}

.account-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.account-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.account-desc {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 提交按钮(品牌主色渐变,与其他主操作按钮同款) */
.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 88rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #e8ddb5 0%, #dbc98a 100%);
  color: #2e2b29;
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  box-shadow:
    0 8rpx 20rpx rgba(219, 201, 138, 0.4),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.5);
  transition: all 0.18s ease;
}

.submit-btn:active:not(.disabled) {
  transform: scale(0.98);
}

.submit-btn.disabled {
  opacity: 0.45;
  box-shadow: none;
  cursor: not-allowed;
}

/* 未实名态:保持可点击,品牌渐变 + 阴影,清晰提示这是引导认证的按钮 */
.submit-btn.is-verify {
  background: linear-gradient(135deg, #f0a86e 0%, #e8895a 100%);
  color: #ffffff;
  box-shadow:
    0 8rpx 20rpx rgba(232, 137, 90, 0.4),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4);
}

.footer-tip {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 禁用原因提示:位于按钮下方,告诉用户为什么按钮是灰的 */
.submit-reason {
  display: block;
  text-align: center;
  margin-top: -16rpx;
  font-size: 24rpx;
  color: var(--color-danger);
}
</style>
