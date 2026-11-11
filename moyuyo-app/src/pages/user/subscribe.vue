<template>
  <view class="subscribe">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('subscribeService.title') }}</text>
      <view class="header-spacer" />
    </view>

    <!-- 当前订阅概览 -->
    <view v-if="currentPlan" class="overview-card">
      <view class="overview-left">
        <text class="overview-label">{{ $t('subscribeService.currentLabel') }}</text>
        <text class="overview-plan">{{ currentPlan.label }}</text>
        <text class="overview-date">
          {{ $t('subscribeService.nextBilling', { date: currentPlan.nextBilling }) }}
        </text>
      </view>
      <view class="overview-right">
        <text class="overview-price">{{ currentPlan.price }}</text>
      </view>
    </view>

    <scroll-view class="content" scroll-y>
      <!-- 套餐卡片区 -->
      <view class="plans-section">
        <view class="section-title">{{ $t('subscribeService.selectPlan') }}</view>
        <view class="plans-row">
          <view
            v-for="plan in plans"
            :key="plan.key"
            class="plan-card"
            :class="{ active: selectedPlan === plan.key, 'plan-highlight': plan.highlight }"
            @click="selectedPlan = plan.key"
          >
            <view v-if="plan.badge" class="plan-badge">{{ plan.badge }}</view>
            <text class="plan-name">{{ plan.name }}</text>
            <text class="plan-price">{{ plan.price }}</text>
            <text class="plan-period">/{{ plan.period }}</text>
            <text v-if="plan.original" class="plan-original">
              {{ $t('subscribeService.originalPrice', { price: plan.original }) }}
            </text>
            <view v-if="plan.save" class="plan-save">
              <text class="plan-save-text">
                {{ $t('subscribeService.saveAmount', { amount: plan.save }) }}
              </text>
            </view>
          </view>
        </view>
        <!-- 订阅按钮 -->
        <button
          class="subscribe-btn"
          :class="{ subscribed: currentPlan && selectedPlan === currentPlan.key }"
          @click="onSubscribe"
        >
          {{
            currentPlan && selectedPlan === currentPlan.key
              ? $t('subscribeService.currentPlan')
              : $t('subscribeService.subscribeNow')
          }}
        </button>
      </view>

      <!-- 自动续费开关 -->
      <view class="toggle-row">
        <text class="toggle-label">{{ $t('subscribeService.autoRenew') }}</text>
        <switch
          class="toggle-switch"
          :checked="autoRenew"
          color="var(--color-primary)"
          @change="onToggleAutoRenew"
        />
      </view>

      <!-- 功能对比 -->
      <view class="feature-section">
        <view class="section-title">{{ $t('subscribeService.featureCompare') }}</view>
        <view class="feature-table">
          <view class="feature-header">
            <text class="feature-cell feature-label">{{ $t('subscribeService.feature') }}</text>
            <text class="feature-cell feature-head">{{ $t('subscribeService.freeTier') }}</text>
            <text class="feature-cell feature-head feature-premium">
              {{ $t('subscribeService.premiumTier') }}
            </text>
          </view>
          <view v-for="item in featureItems" :key="item.key" class="feature-row">
            <text class="feature-cell feature-label">{{ item.label }}</text>
            <text
              class="feature-cell"
              :class="{ 'feature-yes': item.free, 'feature-no': !item.free }"
            >
              {{ item.free ? '✓' : '—' }}
            </text>
            <text class="feature-cell feature-premium feature-yes">
              <text class="luc luc-check" />
            </text>
          </view>
        </view>
      </view>

      <!-- 历史订阅 -->
      <view class="history-section">
        <view class="section-title">{{ $t('subscribeService.historyTitle') }}</view>
        <view v-if="historyList.length > 0" class="history-list">
          <view v-for="item in historyList" :key="item.id" class="history-item">
            <view class="history-left">
              <text class="history-plan">{{ item.plan }}</text>
              <text class="history-date">{{ item.date }}</text>
            </view>
            <view class="history-right">
              <text class="history-amount">{{ item.amount }}</text>
              <text class="history-status" :class="'status-' + item.status">
                {{ item.statusText }}
              </text>
            </view>
          </view>
        </view>
        <view v-else class="history-empty">
          <text class="history-empty-text">{{ $t('subscribeService.historyEmpty') }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { subscribeApi, orderApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userSubscribe',

  data() {
    return {
      // 后端原始订阅记录（UserSubscriptionEntity：planId/endTime/autoRenew/status）
      currentSub: null,
      selectedPlan: '',
      autoRenew: true,
      plans: [],
      // 语言版本号：语言切换时触发模板重渲染
      localeVersion: 0,
      featureList: [
        { key: 'browse', free: true },
        { key: 'community', free: true },
        { key: 'health', free: true },
        { key: 'discount', free: false },
        { key: 'shipping', free: false },
        { key: 'support', free: false },
        { key: 'exclusive', free: false },
        { key: 'birthday', free: false },
      ],
      historyList: [],
      // 是否已跳转支付：支付返回本页（onShow）时重新拉取订阅状态
      payPending: false,
      // 支付前基线：回跳后据此判断订阅是否真的发生变化，避免回调未到/用户取消时误报成功
      payBaseline: { planId: '', endTime: '', historyCount: 0 },
    }
  },

  computed: {
    // 功能对比清单（label 随语言切换）
    featureItems() {
      void this.localeVersion
      return this.featureList.map((item) => ({
        key: item.key,
        free: item.free,
        label: i18n.t('subscribeService.features.' + item.key),
      }))
    },
    // 当前订阅展示模型：把后端订阅记录映射为页面字段（名称/价格取自方案列表）
    currentPlan() {
      if (!this.currentSub) return null
      const plan = this.plans.find((p) => String(p.id) === String(this.currentSub.planId))
      return {
        key: String(this.currentSub.planId),
        label: plan ? plan.name : '',
        price: this.formatPrice(plan ? plan.price : null),
        nextBilling: this.formatDate(this.currentSub.endTime),
      }
    },
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  onLoad() {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    // 历史记录需要方案名，等方案列表加载完再拉取
    this.loadPlans().then(() => this.loadHistory())
    this.loadMySubscription()
  },

  onShow() {
    // 从 Stripe Checkout 返回：重新拉取订阅状态，支付回调成功后即可看到最新有效期
    if (this.payPending) {
      this.payPending = false
      this.refreshAfterPay()
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadPlans() {
      try {
        const res = await subscribeApi.getSubscribePlans()
        // 后端返回 SubscribePlanEntity（id/name/price/durationMonths），映射为卡片展示模型
        const list = Array.isArray(res) ? res : []
        this.plans = list.map((p) => ({
          id: p.id,
          key: String(p.id),
          name: p.name,
          price: this.formatPrice(p.price),
          period: i18n.t('subscribeService.months', { n: p.durationMonths }),
        }))
        if (this.plans.length > 0 && !this.selectedPlan) {
          this.selectedPlan = this.plans[0].key
        }
      } catch (err) {
        uni.showToast({ title: i18n.t('subscribeService.loadFailed'), icon: 'none' })
      }
    },

    async loadMySubscription() {
      try {
        const res = await subscribeApi.getMySubscription()
        // 后端"无有效订阅时回退最新一条历史"可能返回 EXPIRED/CANCELLED，只有 ACTIVE 才算当前订阅
        const sub = res || null
        this.currentSub = sub && sub.status === 'ACTIVE' ? sub : null
        if (this.currentSub) {
          this.selectedPlan = String(this.currentSub.planId)
          this.autoRenew = this.currentSub.autoRenew !== false
        }
      } catch (err) {
        console.error('加载订阅信息失败', err)
      }
    },

    /** 订阅历史：后端返回 planId/action/amount/createTime，这里映射为展示模型 */
    async loadHistory() {
      try {
        const res = await subscribeApi.getSubscribeHistory()
        const list = Array.isArray(res) ? res : []
        this.historyList = list.map((h) => {
          const plan = this.plans.find((p) => String(p.id) === String(h.planId))
          const isRenew = h.action === 'RENEW'
          return {
            id: h.id,
            plan: plan ? plan.name : i18n.t('subscribeService.historyUnknownPlan'),
            date: this.formatDate(h.createTime),
            amount: this.formatPrice(h.amount),
            status: isRenew ? 'renew' : 'subscribe',
            statusText: i18n.t(
              isRenew ? 'subscribeService.historyRenew' : 'subscribeService.historySubscribe',
            ),
          }
        })
      } catch (err) {
        console.error('加载订阅历史失败', err)
      }
    },

    async onSubscribe() {
      if (this.currentPlan && this.selectedPlan === this.currentPlan.key) {
        uni.showToast({ title: i18n.t('subscribeService.alreadyCurrent'), icon: 'none' })
        return
      }
      try {
        // 1) 创建订阅订单（待支付），由服务端按方案定价，前端不传金额
        const order = await subscribeApi.subscribe(this.selectedPlan)
        if (!order || !order.orderNo) {
          throw new Error(i18n.t('subscribeService.subscribeFailed'))
        }
        // 2) 复用订单支付接口发起 Stripe Checkout（渠道/参数与订单支付页保持一致）
        const client = this.buildPayClient()
        const pay = await orderApi.createPayment({
          orderNo: order.orderNo,
          payChannel: 'STRIPE',
          payMethod: 'CARD',
          returnUrl: this.buildReturnUrl(),
          clientType: client.clientType,
          schemeBase: client.schemeBase,
        })
        // 3) 跳转支付页；支付完成返回本页后由 onShow 重新拉取订阅状态
        if (pay && pay.sessionUrl) {
          // 记录支付前基线，回跳后据此判断订阅是否真的开通/续期
          this.payBaseline = {
            planId: this.currentSub ? this.currentSub.planId : '',
            endTime: this.currentSub ? this.currentSub.endTime : '',
            historyCount: this.historyList.length,
          }
          this.payPending = true
          this.openPayUrl(pay.sessionUrl)
          return
        }
        throw new Error(i18n.t('subscribeService.subscribeFailed'))
      } catch (err) {
        uni.showToast({
          title: err.message || i18n.t('subscribeService.subscribeFailed'),
          icon: 'none',
        })
      }
    },

    // 支付返回本页：重新拉取订阅与历史，确认订阅真的发生变化后再提示成功。
    // 支付回调可能晚于页面回跳，最多轮询 3 次，避免"回调未到/用户取消"被误报为订阅成功。
    async refreshAfterPay() {
      const before = this.payBaseline
      for (let i = 0; i < 3; i++) {
        await Promise.all([this.loadMySubscription(), this.loadHistory()])
        if (this.subscriptionChanged(before)) {
          uni.showToast({ title: i18n.t('subscribeService.subscribeSuccess'), icon: 'success' })
          return
        }
        if (i < 2) await new Promise((resolve) => setTimeout(resolve, 1500))
      }
    },

    /** 订阅是否发生变化：方案/到期时间变化，或新增了一条订阅历史（覆盖长期有效订阅续费） */
    subscriptionChanged(before) {
      if (!before) return false
      if (this.historyList.length > (before.historyCount || 0)) return true
      if (!this.currentSub) return false
      return (
        String(this.currentSub.planId || '') !== String(before.planId || '') ||
        String(this.currentSub.endTime || '') !== String(before.endTime || '')
      )
    },

    // 支付客户端类型：APP 端用自定义 scheme 回跳，H5 走中转页
    buildPayClient() {
      let clientType = 'H5'
      // #ifdef APP-PLUS
      clientType = 'APP'
      // #endif
      return {
        clientType,
        schemeBase: clientType === 'APP' ? 'moyuyo://pay/return' : '',
      }
    },

    // 支付回跳地址：与订单支付页一致，H5 取当前域名
    buildReturnUrl() {
      return typeof window !== 'undefined' ? window.location.origin : ''
    },

    // 打开 Stripe Checkout：APP 用系统浏览器，H5 整页跳转
    openPayUrl(url) {
      // #ifdef APP-PLUS
      plus.runtime.openURL(url)
      // #endif
      // #ifdef H5
      window.location.href = url
      // #endif
    },

    // 自动续费开关：变更后同步服务端，失败时回滚，避免界面与服务端状态不一致
    async onToggleAutoRenew(e) {
      const next = e.detail.value
      const previous = this.autoRenew
      this.autoRenew = next
      // 尚未订阅时只保留本地值，订阅时后端默认开启自动续费
      if (!this.currentSub) return
      try {
        await subscribeApi.toggleAutoRenew(next)
      } catch (err) {
        this.autoRenew = previous
        uni.showToast({
          title: err.message || i18n.t('subscribeService.subscribeFailed'),
          icon: 'none',
        })
      }
    },

    formatPrice(value) {
      if (value === null || value === undefined || value === '') return ''
      const num = Number(value)
      return Number.isNaN(num) ? String(value) : `${i18n.currencySymbol}${num.toFixed(2)}`
    },

    // 后端 LocalDateTime 序列化为 2026-11-11T10:00:00，这里截断为 2026-11-11 10:00
    formatDate(value) {
      if (!value) return ''
      return String(value).replace('T', ' ').slice(0, 16)
    },
  },
}
</script>

<style lang="scss" scoped>
.subscribe {
  min-height: 100vh;
  background: var(--color-background);
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 顶部状态栏安全区：取 --status-bar-height 与 env(safe-area-inset-top) 较大值，避免 iOS 双重计算；第二条为老 WebView 兜底 */
  height: calc(88rpx + var(--status-bar-height, 0px));
  height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
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
}

.header-spacer {
  width: 72rpx;
}

.content {
  width: 100%;
  box-sizing: border-box;
  padding: 0 32rpx 48rpx;
}

.overview-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24rpx 32rpx 0;
  padding: 32rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.overview-left {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.overview-label {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.overview-plan {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.overview-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.overview-price {
  font-size: 36rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 24rpx;
}

.plans-section {
  margin-top: 32rpx;
}

.plans-row {
  display: flex;
  gap: 16rpx;
}

.plan-card {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32rpx 16rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 2rpx solid var(--color-divider);
  transition: all 0.2s;
}

.plan-card.active {
  border-color: var(--color-primary);
}

.plan-card.plan-highlight {
  border-color: var(--color-primary);
  background: linear-gradient(180deg, var(--color-primary-light), var(--color-surface));
}

.plan-badge {
  position: absolute;
  top: -16rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
}

.plan-name {
  font-size: 26rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.plan-price {
  font-size: 40rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  line-height: 1;
}

.plan-period {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.plan-original {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
  margin-top: 8rpx;
}

.plan-save {
  margin-top: 12rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: var(--color-primary-light);
}

.plan-save-text {
  font-size: 20rpx;
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-medium);
}

.subscribe-btn {
  width: 100%;
  height: 96rpx;
  margin-top: 32rpx;
  border-radius: 48rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.subscribe-btn.subscribed {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

.subscribe-btn::after {
  border: none;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 32rpx;
  padding: 24rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.toggle-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.toggle-switch {
  transform: scale(0.8);
}

.feature-section {
  margin-top: 40rpx;
}

.feature-table {
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.feature-header,
.feature-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  border-bottom: 1rpx solid var(--color-divider);
}

.feature-row:last-child {
  border-bottom: none;
}

.feature-cell {
  padding: 24rpx 16rpx;
  font-size: 26rpx;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-label {
  text-align: left;
  color: var(--color-text);
  justify-content: flex-start;
}

.feature-head {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.feature-premium {
  background: var(--color-primary-light);
}

.feature-yes {
  color: var(--color-primary);
  font-weight: var(--font-weight-bold);
}

.feature-no {
  color: var(--color-text-tertiary);
}

.history-section {
  margin-top: 40rpx;
  padding-bottom: 48rpx;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.history-left {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.history-plan {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.history-date {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.history-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.history-amount {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.history-status {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
}

.status-cancelled {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

.status-subscribe,
.status-renew {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.history-empty {
  display: flex;
  justify-content: center;
  padding: 48rpx 0;
}

.history-empty-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
</style>
