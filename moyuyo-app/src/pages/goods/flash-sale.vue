<template>
  <view class="flash-sale">
    <!-- 顶部导航栏 -->



    <!-- 秒杀头部横幅：倒计时基于当前进行中活动最近一条 endTime -->
    <view class="flash-banner">
      <view class="banner-left">
        <text class="banner-title">限时秒杀</text>
        <text class="banner-sub">超值好物 限量抢购</text>
      </view>
      <view v-if="activeSession === 'ongoing' && ongoingCountdownLabel" class="banner-right">
        <text class="countdown-label">本场距结束</text>
        <view class="countdown">
          <text class="countdown-digit">{{ ongoingCountdownLabel.h }}</text>
          <text class="countdown-sep">:</text>
          <text class="countdown-digit">{{ ongoingCountdownLabel.m }}</text>
          <text class="countdown-sep">:</text>
          <text class="countdown-digit">{{ ongoingCountdownLabel.s }}</text>
        </view>
      </view>
      <view v-else-if="activeSession === 'upcoming' && nextCountdownLabel" class="banner-right">
        <text class="countdown-label">距下一场开始</text>
        <view class="countdown">
          <text class="countdown-digit">{{ nextCountdownLabel.h }}</text>
          <text class="countdown-sep">:</text>
          <text class="countdown-digit">{{ nextCountdownLabel.m }}</text>
          <text class="countdown-sep">:</text>
          <text class="countdown-digit">{{ nextCountdownLabel.s }}</text>
        </view>
      </view>
    </view>

    <!-- 场次切换：根据各状态真实数量显示角标 -->
    <view class="session-tabs">
      <view
        v-for="session in sessions"
        :key="session.id"
        class="session-tab"
        :class="{ active: activeSession === session.id }"
        :style="{
          background:
            activeSession === session.id ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.12)',
          color: activeSession === session.id ? '#ffffff' : 'rgba(255,255,255,0.7)',
        }"
        @tap="onSwitchSession(session.id)"
      >
        <text
          class="session-dot"
          :style="{ background: activeSession === session.id ? '#ffffff' : 'transparent' }"
        />
        <text>{{ session.label }}</text>
        <text v-if="session.count > 0" class="session-count">{{ session.count }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 秒杀商品网格 -->
      <view v-if="flashProducts.length === 0" class="empty-tip">
        <text>暂无{{ sessionEmptyLabel }}的活动</text>
      </view>
      <view v-else class="product-grid">
        <view
          v-for="product in flashProducts"
          :key="product.id"
          class="product-card"
          :class="{ soldOut: product.soldOut }"
          :style="{ opacity: product.soldOut ? 0.55 : 1 }"
          @tap="handleProductTap(product)"
        >
          <view class="product-image">
            <image
              v-if="product.image"
              :src="product.image"
              class="product-img"
              mode="aspectFill"
            />
            <view v-else class="img-placeholder" :style="{ background: product.color }" />
            <view class="flash-badge">秒杀</view>
            <view v-if="product.soldOut" class="sold-out-overlay">
              <text>已售罄</text>
            </view>
          </view>
          <view class="product-info">
            <text class="product-name">{{ product.name }}</text>
            <view class="price-row">
              <text class="flash-price">{{ currencySymbol }}{{ product.flashPrice }}</text>
              <text v-if="product.originalPrice" class="original-price">
                {{ currencySymbol }}{{ product.originalPrice }}
              </text>
            </view>
            <view class="progress-bar">
              <view class="progress-fill" :style="{ width: product.soldPercent + '%' }" />
              <text class="progress-text">已抢 {{ product.soldPercent }}%</text>
            </view>
            <view class="product-actions">
              <view
                class="buy-btn"
                :class="{
                  disabled:
                    product.soldOut || product.status === 'upcoming' || product.status === 'ended',
                }"
                @tap.stop="handleFlashBuy(product)"
              >
                <text>{{ product.actionLabel }}</text>
              </view>
              <text class="limit-text">每人限{{ product.limit }}件</text>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script>
import { flashSaleApi } from '@/api'

/**
 * 场次 id 与后端活动状态对应：
 * - upcoming: 尚未开始（startTime > now）
 * - ongoing:  进行中（startTime <= now <= endTime 且 active=true）
 * - ended:    已结束（endTime < now 或 active=false）
 */
const SESSION_LIST = [
  { id: 'ongoing', label: '进行中', status: '抢购中' },
  { id: 'upcoming', label: '即将开始', status: '预告' },
  { id: 'ended', label: '已结束', status: '已结束' },
]

export default {
  pageTitleKey: 'pageTitle.goodsFlashSale',

  data() {
    return {
      activeSession: 'ongoing',
      sessions: SESSION_LIST.map((s) => ({ ...s, count: 0 })),
      // 三个状态的活动分别缓存,避免切换 tab 时重复请求
      cache: { ongoing: [], upcoming: [], ended: [] },
      flashProducts: [],
      timerId: null,
      // 倒计时原始秒数,每秒 -1
      ongoingCountdownSec: 0,
      nextCountdownSec: 0,
    }
  },

  computed: {
    sessionEmptyLabel() {
      const found = this.sessions.find((s) => s.id === this.activeSession)
      return found ? found.label : ''
    },
    // 货币符号：从编译期注入的 VITE_CURRENCY 推导
    currencySymbol() {
      const env = (typeof process !== 'undefined' && process.env && process.env.VITE_CURRENCY) || ''
      const win =
        (typeof window !== 'undefined' &&
          window.__MOYUYO_CONFIG__ &&
          window.__MOYUYO_CONFIG__.VITE_CURRENCY) ||
        ''
      const cur = env || win || 'CNY'
      return cur === 'USD' ? '$' : '¥'
    },
    ongoingCountdownLabel() {
      return this.formatHMS(this.ongoingCountdownSec)
    },
    nextCountdownLabel() {
      return this.formatHMS(this.nextCountdownSec)
    },
  },

  onLoad() {
    this.loadAll()
    this.startCountdownTick()
  },

  onUnload() {
    if (this.timerId) {
      clearInterval(this.timerId)
      this.timerId = null
    }
  },

  methods: {
    /**
     * 并行拉取三场数据,保证切换 tab 时无 loading 闪烁
     * 后端只有 /api/v1/flash-sales 一个接口,按 session 参数过滤;
     * 若后端无 session 参数则前端按 startTime/endTime 自行归类。
     */
    async loadAll() {
      await Promise.all([
        this.loadSession('ongoing'),
        this.loadSession('upcoming'),
        this.loadSession('ended'),
      ])
      this.applyActive()
      this.updateSessionCounts()
      this.refreshCountdowns()
    },

    async loadSession(sessionId) {
      try {
        const res = await flashSaleApi.getFlashSales({ session: sessionId, size: 50 })
        const list = this.extractList(res)
        // 若后端忽略 session 参数,前端按时间归类
        const now = Date.now()
        const filtered = list.filter((p) => this.sessionOf(p, now) === sessionId)
        this.cache[sessionId] = filtered.map((p) => this.normalize(p))
        if (this.cache[sessionId].length === 0 && list.length > 0) {
          // 兜底:若 session 参数没生效,保留后端原数据用于展示
          this.cache[sessionId] = list.map((p) => this.normalize(p))
        }
      } catch (e) {
        this.cache[sessionId] = []
      }
    },

    extractList(res) {
      // request.js 已解包 data 字段;后端 Page 形状: { records, total, ... }
      if (Array.isArray(res)) return res
      if (res && Array.isArray(res.records)) return res.records
      if (res && Array.isArray(res.items)) return res.items
      if (res && Array.isArray(res.data)) return res.data
      return []
    },

    /**
     * 根据 startTime/endTime 判断活动所属场次
     */
    sessionOf(p, nowMs) {
      const start = p.startTime ? new Date(p.startTime.replace(' ', 'T')).getTime() : null
      const end = p.endTime ? new Date(p.endTime.replace(' ', 'T')).getTime() : null
      if (!p.active && end && nowMs > end) return 'ended'
      if (end && nowMs > end) return 'ended'
      if (start && nowMs < start) return 'upcoming'
      if (!p.active) return 'ended'
      return 'ongoing'
    },

    normalize(p) {
      const total = Number(p.totalStock || 0)
      const sold = Number(p.soldStock || 0)
      const soldPercent = total > 0 ? Math.min(100, Math.round((sold / total) * 100)) : 0
      const stockLeft = Math.max(0, total - sold)
      const status = this.sessionOf(p, Date.now())
      const actionLabel =
        status === 'upcoming'
          ? '即将开始'
          : status === 'ended'
            ? '已结束'
            : stockLeft <= 0
              ? '已售罄'
              : '抢购'
      return {
        id: p.id,
        name: p.name || '限时秒杀',
        flashPrice: Number(p.flashPrice || 0).toFixed(2),
        originalPrice: Number(p.originalPrice || 0).toFixed(2),
        soldPercent,
        limit: Number(p.limitPerUser || 1),
        soldOut: stockLeft <= 0,
        status,
        actionLabel,
        image: '',
        color: 'linear-gradient(135deg, #cfe5ff, #007aff)',
      }
    },

    onSwitchSession(id) {
      if (this.activeSession === id) return
      this.activeSession = id
      this.applyActive()
      this.refreshCountdowns()
    },

    applyActive() {
      this.flashProducts = this.cache[this.activeSession] || []
    },

    updateSessionCounts() {
      this.sessions = SESSION_LIST.map((s) => ({
        ...s,
        count: (this.cache[s.id] || []).length,
      }))
    },

    /**
     * 倒计时刷新:每 1s 重新计算剩余秒数
     * - 进行中场:取当前展示列表里 endTime 最近的
     * - 即将开始场:取当前展示列表里 startTime 最近的
     */
    refreshCountdowns() {
      const list = this.cache[this.activeSession] || []
      const now = Date.now()
      if (this.activeSession === 'ongoing') {
        const endTimes = list
          .map((p) => p.endTime)
          .filter(Boolean)
          .sort()
        if (endTimes.length > 0) {
          this.ongoingCountdownSec = Math.max(
            0,
            Math.floor((new Date(endTimes[0]).getTime() - now) / 1000),
          )
        } else {
          this.ongoingCountdownSec = 0
        }
      } else if (this.activeSession === 'upcoming') {
        const startTimes = list
          .map((p) => p.startTime)
          .filter(Boolean)
          .sort()
        if (startTimes.length > 0) {
          this.nextCountdownSec = Math.max(
            0,
            Math.floor((new Date(startTimes[0]).getTime() - now) / 1000),
          )
        } else {
          this.nextCountdownSec = 0
        }
      }
    },

    startCountdownTick() {
      this.timerId = setInterval(() => {
        if (this.activeSession === 'ongoing' && this.ongoingCountdownSec > 0) {
          this.ongoingCountdownSec--
        } else if (this.activeSession === 'upcoming' && this.nextCountdownSec > 0) {
          this.nextCountdownSec--
        }
      }, 1000)
    },

    formatHMS(totalSec) {
      if (!totalSec || totalSec <= 0) return null
      const h = Math.floor(totalSec / 3600)
      const m = Math.floor((totalSec % 3600) / 60)
      const s = totalSec % 60
      const pad = (n) => String(n).padStart(2, '0')
      return { h: pad(h), m: pad(m), s: pad(s) }
    },
    toggleNotify() {
      uni.showToast({ title: '已设置开抢提醒', icon: 'success' })
    },

    handleProductTap(product) {
      if (product.status === 'upcoming') {
        uni.showToast({ title: '活动即将开始，敬请期待', icon: 'none' })
        return
      }
      if (product.status === 'ended' || product.soldOut) {
        return
      }
      uni.navigateTo({ url: `/pages/goods/flash-sale-detail?id=${product.id}` })
    },

    async handleFlashBuy(product) {
      if (product.status === 'upcoming') {
        uni.showToast({ title: '活动尚未开始', icon: 'none' })
        return
      }
      if (product.status === 'ended' || product.soldOut) return
      try {
        await flashSaleApi.buyFlashSale(product.id, 1)
        uni.showToast({ title: `抢购「${product.name}」成功！`, icon: 'success' })
        // 抢购成功后减少本地库存显示
        const idx = this.flashProducts.findIndex((p) => p.id === product.id)
        if (idx >= 0) {
          const updated = { ...this.flashProducts[idx] }
          const total = Number(updated.soldPercent) * 0 + 100 // 保持 totalStock 不变,只动百分比
          // 简化:用本地 soldStock 累加
          updated.soldPercent = Math.min(100, Number(updated.soldPercent) + 1)
          this.flashProducts.splice(idx, 1, updated)
        }
      } catch (e) {
        uni.showToast({ title: e.message || '抢购失败，请重试', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.flash-sale {
  min-height: 100vh;
  background: linear-gradient(180deg, #ff4757 0%, #ff6b81 35%, #f6f2ee 35%, #f6f2ee 100%);
}
.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  height: 88rpx;
  color: #fff;
}
.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon,
.notify-icon {
  font-size: 40rpx;
  color: #fff;
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
}
.flash-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 32rpx 32rpx;
  color: #fff;
}
.banner-title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
}
.banner-sub {
  display: block;
  font-size: 24rpx;
  opacity: 0.85;
  margin-top: 8rpx;
}
.banner-right {
  text-align: right;
}
.countdown-label {
  display: block;
  font-size: 22rpx;
  opacity: 0.9;
  margin-bottom: 8rpx;
}
.countdown {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6rpx;
}
.countdown-digit {
  display: inline-block;
  min-width: 40rpx;
  padding: 4rpx 8rpx;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
  text-align: center;
  border-radius: 6rpx;
}
.countdown-sep {
  color: #fff;
  font-weight: 700;
}
.session-tabs {
  display: flex;
  gap: 12rpx;
  padding: 0 24rpx 24rpx;
}
.session-tab {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 500;
  backdrop-filter: blur(8rpx);
}
.session-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: transparent;
}
.session-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  background: rgba(255, 255, 255, 0.35);
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 700;
}
.scroll {
  height: calc(100vh - 280rpx);
  background: var(--color-background);
  border-top-left-radius: 32rpx;
  border-top-right-radius: 32rpx;
}
.empty-tip {
  text-align: center;
  padding: 120rpx 0;
  color: var(--color-text-tertiary);
  font-size: 28rpx;
}
.product-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24rpx;
  padding: 24rpx;
}
.product-card {
  display: flex;
  gap: 24rpx;
  background: var(--color-surface);
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.05);
}
.product-image {
  position: relative;
  width: 240rpx;
  height: 240rpx;
  flex-shrink: 0;
  border-radius: 16rpx;
  overflow: hidden;
  background: linear-gradient(135deg, #cfe5ff, #007aff);
}
.product-img {
  width: 100%;
  height: 100%;
}
.img-placeholder {
  width: 100%;
  height: 100%;
}
.flash-badge {
  position: absolute;
  top: 8rpx;
  left: 8rpx;
  background: #ff4757;
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.sold-out-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}
.product-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 12rpx;
}
.flash-price {
  font-size: 36rpx;
  font-weight: 700;
  color: #ff4757;
}
.original-price {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}
.progress-bar {
  position: relative;
  height: 24rpx;
  background: var(--color-divider);
  border-radius: 12rpx;
  margin-top: 16rpx;
  overflow: hidden;
}
.progress-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(90deg, #ff6b81, #ff4757);
  border-radius: 12rpx;
}
.progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1rpx 2rpx rgba(0, 0, 0, 0.4);
}
.product-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}
.buy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12rpx 32rpx;
  background: #ff4757;
  color: #fff;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 600;
}
.buy-btn.disabled {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}
.limit-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.bottom-spacer {
  height: 32rpx;
}
</style>
