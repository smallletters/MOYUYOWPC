<template>
  <view class="coupon-center">
    <!-- 深色顶部导航栏 -->
    <view class="nav-bar">
      <view class="nav-btn" @click="goBack">
        <text class="nav-back"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="nav-title">领券中心</text>
      <view class="nav-btn" />
    </view>

    <!-- 限时抢券倒计时横幅 -->
    <view v-if="totalSeconds > 0" class="countdown-banner">
      <view class="countdown-left">
        <text class="countdown-icon"><text class="luc luc-zap" /></text>
        <text class="countdown-label">限时领券</text>
      </view>
      <view class="countdown-right">
        <text class="countdown-hint">剩余</text>
        <view class="countdown-timer">
          <text class="timer-block">{{ countdownStr.h }}</text>
          <text class="timer-colon">:</text>
          <text class="timer-block">{{ countdownStr.m }}</text>
          <text class="timer-colon">:</text>
          <text class="timer-block">{{ countdownStr.s }}</text>
        </view>
      </view>
    </view>

    <!-- 优惠券分类 Tab -->
    <scroll-view class="tab-bar" scroll-x show-scrollbar="false">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ 'tab-active': activeTab === tab.value }"
        @click="onTabChange(tab.value)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </scroll-view>

    <!-- 优惠券列表（使用 uview-plus u-coupon 组件） -->
    <view class="coupon-list">
      <u-coupon
        v-for="coupon in filteredCoupons"
        :key="coupon.id"
        :amount="coupon.amount"
        :unit="coupon.unit"
        :title="coupon.name"
        :desc="coupon.typeLabel"
        :limit="coupon.condition"
        :time="coupon.validity"
        :type="coupon.themeType"
        shape="round"
        :disabled="coupon.claimed"
        :action-text="coupon.claimed ? coupon.claimedText : coupon.actionText"
        @click="onClaim(coupon)"
      />

      <!-- 空状态 -->
      <view v-if="filteredCoupons.length === 0" class="empty-tip">
        <text>暂无可领取的优惠券</text>
      </view>
    </view>

    <!-- 底部汇总栏 -->
    <view class="bottom-bar">
      <view class="bottom-summary">
        <text class="summary-icon"><text class="luc luc-ticket" /></text>
        <text class="summary-text">
          {{ $t('couponCenter.summary', { count: claimedCount }) }}
        </text>
      </view>
      <view class="use-btn" @click="onUse">
        <text>{{ $t('couponCenter.useNow') }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { couponApi } from '@/api'
import { i18n } from '@/i18n'

// category -> uview-plus up-coupon type 映射
const CATEGORY_TO_THEME = {
  discount: 'primary', // 满减券
  cash: 'error', // 现金券
  shipping: 'info', // 运费券
  gift: 'warning', // 礼品券
  flash: 'error', // 限时
  new: 'success', // 新人
  member: 'warning', // 会员
}

// 显式注册 uview-plus Coupon 组件
// easycom "^u-(.*)" 匹配 <u-xxx> -> 找 uview-plus/components/u-xxx/u-xxx.vue
// 官方组件名是 <up-coupon> 但实际文件路径是 u-coupon/u-coupon.vue（easycom 命名约定），
// 这里直接 import 实际路径,绕开 easycom 解析歧义。
import uCoupon from 'uview-plus/components/u-coupon/u-coupon.vue'

export default {
  components: { uCoupon },
  data() {
    return {
      activeTab: 'all',
      // 倒计时初始为 0,首屏不显示,等接口返回 endTime 后再启动
      totalSeconds: 0,
      timerId: null,
      coupons: [],
      localeVersion: 0,
    }
  },

  computed: {
    filteredCoupons() {
      if (this.activeTab === 'all') return this.coupons
      return this.coupons.filter((c) => c.category === this.activeTab)
    },

    claimedCount() {
      return this.coupons.filter((c) => c.claimed).length
    },

    countdownStr() {
      const h = Math.floor(this.totalSeconds / 3600)
      const m = Math.floor((this.totalSeconds % 3600) / 60)
      const s = this.totalSeconds % 60
      return {
        h: String(h).padStart(2, '0'),
        m: String(m).padStart(2, '0'),
        s: String(s).padStart(2, '0'),
      }
    },

    // 货币符号：从全局配置取值,CNY→¥,USD→$
    currencySymbol() {
      // process.env.VITE_CURRENCY 在编译时注入;运行期取不到时回退到 CNY
      // H5 端 window.__MOYUYO_CONFIG__ 在 index.html 注入
      const env = (typeof process !== 'undefined' && process.env && process.env.VITE_CURRENCY) || ''
      const win =
        (typeof window !== 'undefined' &&
          window.__MOYUYO_CONFIG__ &&
          window.__MOYUYO_CONFIG__.VITE_CURRENCY) ||
        ''
      const cur = env || win || 'CNY'
      return cur === 'USD' ? '$' : '¥'
    },

    tabs() {
      void this.localeVersion
      return [
        { label: i18n.t('couponCenter.tabs.all'), value: 'all' },
        { label: i18n.t('couponCenter.tabs.new'), value: 'new' },
        { label: i18n.t('couponCenter.tabs.category'), value: 'category' },
        { label: i18n.t('couponCenter.tabs.discount'), value: 'discount' },
        { label: i18n.t('couponCenter.tabs.flash'), value: 'flash' },
        { label: i18n.t('couponCenter.tabs.member'), value: 'member' },
      ]
    },
  },

  onLoad() {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.startCountdown()
    this.loadCoupons()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
    if (this.timerId) {
      clearInterval(this.timerId)
      this.timerId = null
    }
  },

  methods: {
    async loadCoupons() {
      try {
        // 后端返回 Page<CouponEntity> { records, total, size, current }
        // 字段：id / name / description / type(AMOUNT|PERCENT) /
        //      discountValue / minOrderAmount / maxDiscountAmount /
        //      totalCount / claimedCount / startTime / endTime / active / claimedByMe
        console.log('[coupon-center] loadCoupons start')
        const page = await couponApi.getAvailableCoupons()
        console.log('[coupon-center] raw response:', JSON.stringify(page).slice(0, 500))
        const list = Array.isArray(page?.records)
          ? page.records
          : Array.isArray(page?.items)
            ? page.items
            : []
        console.log('[coupon-center] parsed list.length:', list.length)
        // 用最近的 endTime 作为倒计时基准(若无则不启用倒计时)
        const farthestEnd = list
          .map((c) => c.endTime)
          .filter(Boolean)
          .sort()
          .pop()
        this.applyCountdownFromServer(farthestEnd)
        const normalized = list.map((c) => this.normalizeCoupon(c))
        console.log('[coupon-center] normalized count:', normalized.length)
        this.coupons = normalized
      } catch (e) {
        // 接口失败时清空列表,展示空状态
        console.error('[coupon-center] loadCoupons failed:', e)
        this.coupons = []
      }
    },

    /**
     * 将后端 CouponEntity 归一为页面展示模型
     * - AMOUNT 券：amount=discountValue, unit=¥/$（取当前货币符号）
     * - PERCENT 券：amount=discountValue(去掉小数), unit=折（兼容 up-coupon 展示）
     *   PERCENT 若希望显示"8.5折"则需后端预格式化；这里采用 up-coupon 默认折扣样式时
     *   传入 amount=8.5 unit="折"（前端本地格式化）
     * - condition 由 minOrderAmount 拼装
     * - validity 由 startTime + endTime 拼装
     * - claimed 由 claimedByMe 决定（需后端在 list 接口 join 当前用户的 user_coupon）
     */
    normalizeCoupon(c) {
      const isPercent = (c.type || '').toUpperCase() === 'PERCENT'
      const rawValue = Number(c.discountValue || 0)
      // AMOUNT 取纯数字交给 up-coupon 渲染主金额;
      // PERCENT 用 10 - value/10 得到"几折"语义(8.5折 = 打85折)
      let amount
      let unit
      if (isPercent) {
        amount = String(rawValue).replace(/\.0+$/, '')
        unit = '折'
      } else {
        amount = String(rawValue).replace(/[^\d.]/g, '')
        unit = this.currencySymbol
      }
      // typeLabel 给 up-coupon 的 desc 区域作为副标题,优先 description 字段
      const typeLabel = c.description || (isPercent ? '折扣券' : '满减券')
      // condition 拼装
      const minAmt = Number(c.minOrderAmount || 0)
      const condition = minAmt > 0 ? `满${this.formatMoney(minAmt)}可用` : '无门槛'
      // validity 拼装:优先显示结束日期;若 startTime 存在则一并显示
      const validity = this.formatValidity(c.startTime, c.endTime)
      const category = this.inferCategory(c, typeLabel)
      return {
        id: c.id,
        category,
        typeLabel,
        amount,
        unit,
        name: c.name || '',
        condition,
        validity,
        claimed: Boolean(c.claimedByMe || c.claimed),
        themeType: CATEGORY_TO_THEME[category] || 'primary',
        actionText: i18n.t('couponCenter.goClaim') || '立即领取',
        claimedText: i18n.t('couponCenter.claimed') || '已领取',
      }
    },

    // 货币符号已移到 computed,这里只保留金额/日期格式化辅助方法
    formatMoney(v) {
      return `${this.currencySymbol}${Number(v).toFixed(2)}`
    },

    formatValidity(start, end) {
      const fmt = (t) => {
        if (!t) return ''
        // 后端返 LocalDateTime 形如 "2026-12-31T23:59:59" 或 "2026-12-31 23:59:59"
        const d = new Date(t.replace(' ', 'T'))
        if (Number.isNaN(d.getTime())) return ''
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      }
      const s = fmt(start)
      const e = fmt(end)
      if (s && e) return `${s} 至 ${e}`
      if (e) return `至 ${e} 前使用`
      if (s) return `${s} 起使用`
      return '长期有效'
    },

    // 历史数据兜底:从 description / typeLabel 文本中识别分类
    inferCategory(c, typeLabel = '') {
      const t = (typeLabel || '').toLowerCase()
      if (t.includes('运费')) return 'shipping'
      if (t.includes('现金')) return 'cash'
      if (t.includes('礼品') || t.includes('赠')) return 'gift'
      if (t.includes('限时') || t.includes('秒杀')) return 'flash'
      if (t.includes('新人')) return 'new'
      if (t.includes('会员') || t.includes('vip')) return 'member'
      // 按 type 简单归类
      if ((c.type || '').toUpperCase() === 'PERCENT') return 'discount'
      return 'discount'
    },

    /** 将后端 endTime 转成倒计时秒数,无 endTime 时不启用倒计时 */
    applyCountdownFromServer(endTime) {
      if (!endTime) {
        // 后端无 endTime 时,关闭倒计时避免给用户制造虚假紧迫感
        this.totalSeconds = 0
        return
      }
      const diff = Math.floor((new Date(endTime).getTime() - Date.now()) / 1000)
      this.totalSeconds = Math.max(0, diff)
    },

    goBack() {
      uni.navigateBack()
    },

    onTabChange(value) {
      this.activeTab = value
    },

    async onClaim(coupon) {
      if (coupon.claimed) return
      try {
        await couponApi.claimCoupon(coupon.id)
        coupon.claimed = true
        uni.showToast({ title: '领取成功', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '领取失败，请重试', icon: 'none' })
      }
    },

    onUse() {
      // 跳到已领取的优惠券页(已注册),而非 toast 占位
      uni.navigateTo({ url: '/pages/user/coupons' })
    },

    startCountdown() {
      this.timerId = setInterval(() => {
        if (this.totalSeconds > 0) {
          this.totalSeconds--
        }
      }, 1000)
    },
  },
}
</script>

<style lang="scss" scoped>
.coupon-center {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 128rpx;
}

/* 优惠券卡片:由 uview-plus u-coupon 组件自带样式渲染;此处只保留列表间距 */
.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 0 32rpx 24rpx;
}

.coupon-list >>> .up-coupon {
  /* 让卡片在 H5 端有合理内边距 */
  margin: 0;
}

.empty-tip {
  padding: 96rpx 0;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

/* 深色导航栏 */
.nav-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-text);
}

.nav-btn {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  color: var(--color-surface);
}

.nav-btn:first-child {
  left: 16rpx;
}

.nav-btn:last-child {
  right: 16rpx;
}

.nav-back {
  font-size: 48rpx;
  line-height: 1;
}

.nav-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-surface);
}

/* 倒计时横幅 */
.countdown-banner {
  margin: 24rpx 32rpx;
  padding: 24rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
}

.countdown-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.countdown-icon {
  font-size: 32rpx;
  animation: pulse-glow 2s ease-in-out infinite;
}

.countdown-label {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-surface);
}

.countdown-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.countdown-hint {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.7);
}

.countdown-timer {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.timer-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 48rpx;
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.6);
  color: var(--color-surface);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  font-variant-numeric: tabular-nums;
}

.timer-colon {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-surface);
}

@keyframes pulse-glow {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

/* Tab 栏 */
.tab-bar {
  display: flex;
  flex-wrap: nowrap;
  padding: 0 32rpx 16rpx;
  white-space: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.tab-bar::-webkit-scrollbar {
  display: none;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 28rpx;
  margin-right: 16rpx;
  border-radius: var(--radius-pill);
  background: var(--color-divider);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.tab-item.tab-active {
  background: var(--color-primary);
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

/* 底部汇总栏 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 112rpx;
  padding: 0 40rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  padding-bottom: env(safe-area-inset-bottom);
}

.bottom-summary {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.summary-icon {
  font-size: 32rpx;
}

.summary-text {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.summary-count {
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
}

.use-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 44rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  transition: all 0.15s ease;
}

.use-btn:active {
  transform: scale(0.96);
}
</style>
