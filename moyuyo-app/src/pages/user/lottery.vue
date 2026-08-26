<template>
  <view class="lottery">
    <view class="nav-header dark">
      <view class="nav-back" @click="goBack">
        <text class="back-icon light"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title light">幸运抽奖</text>
      <view class="nav-placeholder" />
    </view>

    <scroll-view class="scroll" scroll-y>
      <!-- 抽奖活动列表 -->
      <view v-if="lotteries.length > 1" class="lottery-tabs">
        <view
          v-for="lt in lotteries"
          :key="lt.id"
          class="lottery-tab"
          :class="{ active: currentLottery && currentLottery.id === lt.id }"
          @click="onSelectLottery(lt)"
        >
          <text class="lottery-tab-name">{{ lt.name }}</text>
          <text v-if="lt.pointsCost > 0" class="lottery-tab-cost">{{ lt.pointsCost }}/次</text>
        </view>
      </view>

      <view class="chances-card">
        <view class="chances-left">
          <view class="chances-icon">
            <text class="ticket-icon"><text class="luc luc-ticket"></text></text>
          </view>
          <view class="chances-info">
            <text class="chances-num">
              今日已用 {{ statsTodayUsed }} / {{ currentLottery ? currentLottery.dailyFree : 0 }} 次免费
            </text>
            <text class="chances-hint">
              {{ currentLottery && currentLottery.pointsCost > 0
                ? `免费用完后每次扣 ${currentLottery.pointsCost} 积分`
                : '今日免费' }}
            </text>
          </view>
        </view>
        <text class="chances-arrow"><text class="luc luc-chevron-right"></text></text>
      </view>

      <view class="wheel-section">
        <view class="wheel-container">
          <view class="wheel-outer-ring" />
          <view class="wheel-pointer" />
          <view class="wheel-body" :style="{ transform: 'rotate(' + rotation + 'deg)' }">
            <view class="wheel-labels">
              <text
                v-for="(prize, idx) in prizes"
                :key="idx"
                class="wheel-label"
                :style="{
                  transform: 'rotate(' + (idx * (360 / Math.max(prizes.length, 1)) + (360 / Math.max(prizes.length, 1) / 2)) + 'deg)',
                  transformOrigin: '0% 50%',
                }"
              >
                {{ prize }}
              </text>
            </view>
          </view>
          <view class="wheel-center" @click="onSpin">
            <text class="center-text">{{ isSpinning ? '...' : '开始' }}</text>
          </view>
        </view>
        <text class="wheel-tip">点击「开始」按钮进行抽奖</text>
      </view>

      <!-- 奖品列表 -->
      <view class="prize-list-section">
        <text class="section-title"><text class="luc luc-gift"></text> 本次活动奖品</text>
        <view class="prize-grid">
          <view class="prize-item">
            <view class="prize-icon-wrap"><text class="prize-icon"><text class="luc luc-gift"></text></text></view>
            <text class="prize-name">{{ currentLottery ? currentLottery.prizeName : '—' }}</text>
          </view>
          <view class="prize-item">
            <view class="prize-icon-wrap"><text class="prize-icon"><text class="luc luc-ticket"></text></text></view>
            <text class="prize-name">免费 {{ currentLottery ? currentLottery.dailyFree : 0 }} 次/天</text>
          </view>
          <view class="prize-item">
            <view class="prize-icon-wrap"><text class="prize-icon"><text class="luc luc-calendar"></text></text></view>
            <text class="prize-name">{{ currentLottery ? formatTime(currentLottery.endTime) : '长期' }}</text>
          </view>
          <view class="prize-item">
            <view class="prize-icon-wrap"><text class="prize-icon"><text class="luc luc-trending-up"></text></text></view>
            <text class="prize-name">概率 {{ currentLottery ? formatProb(currentLottery.probability) : '—' }}</text>
          </view>
        </view>
      </view>

      <view class="history-section">
        <view class="history-header" @click="showHistory = !showHistory">
          <view class="history-title-row">
            <text class="history-title"><text class="luc luc-clock"></text> 抽奖记录</text>
            <text class="history-count">{{ spinHistory.length }}</text>
          </view>
          <text class="history-arrow" :class="{ open: showHistory }">▼</text>
        </view>
        <view v-if="showHistory" class="history-list">
          <view v-for="(record, idx) in spinHistory" :key="record.id || idx" class="history-item">
            <view class="history-icon-wrap" :style="{ background: record.won ? '#d9b4b0' : '#e5e5ea' }">
              <text class="history-icon luc" :class="$luc(record.won  ?  'trophy'  :  'ticket')"></text>
            </view>
            <view class="history-info">
              <text class="history-name">{{ record.prizeName || '未中奖' }}</text>
              <text class="history-meta">
                {{ record.usedFreeSpin ? '免费' : `扣 ${record.pointsSpent} 积分` }}
                · {{ formatDate(record.createTime) }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 中奖弹窗 -->
    <view v-if="showPrizeModal" class="modal-overlay" @click="showPrizeModal = false">
      <view class="modal-content" @click.stop>
        <view class="modal-icon-wrap" :style="{ background: spinResult && spinResult.won ? '#dbc98a' : '#e5e5ea' }">
          <text class="modal-icon luc" :class="$luc(spinResult && spinResult.won ? 'trophy' : 'ticket')"></text>
        </view>
        <text class="modal-title">
          {{ spinResult && spinResult.won ? '恭喜中奖' : '差一点就中了' }}
        </text>
        <text class="modal-desc">
          {{ spinResult ? spinResult.prizeName : '' }}
          <block v-if="spinResult && !spinResult.usedFreeSpin && spinResult.pointsSpent > 0">
            （消耗 {{ spinResult.pointsSpent }} 积分）
          </block>
        </text>
        <view class="modal-btn" @click="showPrizeModal = false">收下奖品</view>
      </view>
    </view>
  </view>
</template>

<script>
import { lotteryApi } from '@/api'

export default {
  data() {
    return {
      // 后端抽奖活动列表（每次抽奖对应一个活动）
      lotteries: [],
      currentLottery: null,
      prizes: [],
      rotation: 0,
      isSpinning: false,
      statsTodayUsed: 0,
      showHistory: false,
      showPrizeModal: false,
      spinResult: null,
      spinHistory: [],
    }
  },

  onShow() {
    this.loadLotteries()
    this.loadHistory()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadLotteries() {
      try {
        const res = await lotteryApi.getLotteries()
        const list = (res && res.data) || []
        this.lotteries = list
        this.currentLottery = list[0] || null
        // 凑 8 个转盘槽位让转盘好看
        this.prizes = this.currentLottery
          ? this.makePrizes(this.currentLottery.prizeName)
          : []
      } catch (err) {
        console.warn('[lottery] load failed', err)
      }
    },

    /**
     * 转盘槽位：1 个主奖 + N-1 个"未中奖"占位（凑足 8 段）
     */
    makePrizes(mainPrizeName) {
      const slotCount = 8
      const result = []
      for (let i = 0; i < slotCount; i++) {
        result.push(i === 0 ? (mainPrizeName || '神秘奖品') : '谢谢参与')
      }
      return result
    },

    onSelectLottery(lt) {
      this.currentLottery = lt
      this.prizes = this.makePrizes(lt.prizeName)
    },

    async loadHistory() {
      try {
        const res = await lotteryApi.getLotteryHistory()
        const records = (res && res.data) || []
        this.spinHistory = records
        const today = new Date().toDateString()
        this.statsTodayUsed = records.filter((r) => {
          if (!r.createTime || !r.usedFreeSpin) return false
          return new Date(r.createTime).toDateString() === today
        }).length
      } catch (err) {
        console.warn('[lottery] load history failed', err)
      }
    },

    async onSpin() {
      if (this.isSpinning) return
      if (!this.currentLottery) {
        uni.showToast({ title: '当前没有可用的抽奖活动', icon: 'none' })
        return
      }
      this.isSpinning = true

      try {
        // 后端返回 LotteryRecordEntity { id, usedFreeSpin, pointsSpent, won, prizeName, createTime }
        const res = await lotteryApi.spinLottery(this.currentLottery.id)
        const record = (res && res.data) || {}

        // 中奖槽位计算：won=true → 转到主奖品(prizes[0])，否则转到"谢谢参与"
        const targetIdx = record.won ? 0 : 3
        const slotAngle = 360 / Math.max(this.prizes.length, 1)
        const targetAngle = 360 * 5 + (360 - (targetIdx * slotAngle + slotAngle / 2))
        this.rotation = this.rotation + targetAngle

        setTimeout(() => {
          this.isSpinning = false
          this.spinResult = record
          this.showPrizeModal = true
          this.loadHistory() // 刷新今日免费次数
        }, 4200)
      } catch (err) {
        this.isSpinning = false
        uni.showToast({
          title: (err && err.message) || '抽奖失败',
          icon: 'none',
        })
      }
    },

    formatProb(p) {
      if (p == null) return '—'
      const n = Number(p)
      if (isNaN(n)) return '—'
      return (n * 100).toFixed(1) + '%'
    },

    formatTime(t) {
      if (!t) return '长期'
      const d = new Date(t)
      if (isNaN(d.getTime())) return '长期'
      return `${d.getMonth() + 1}/${d.getDate()}`
    },

    formatDate(t) {
      if (!t) return ''
      const d = new Date(t)
      if (isNaN(d.getTime())) return ''
      return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },
  },
}
</script>

<style lang="scss" scoped>
.lottery {
  min-height: 100vh;
  background: var(--color-background);
}

.nav-header.dark {
  background: var(--color-text);
  border-bottom: none;
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.back-icon.light {
  color: #fff;
}

.nav-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.nav-title.light {
  color: #fff;
}

.nav-placeholder {
  width: 60rpx;
}

.scroll {
  height: calc(100vh - 88rpx);
  padding-bottom: 48rpx;
}

.lottery-tabs {
  display: flex;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  overflow-x: auto;
  background: var(--color-background);
}

.lottery-tab {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 200rpx;
}

.lottery-tab.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.lottery-tab-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.lottery-tab-cost {
  font-size: var(--font-size-xs);
  opacity: 0.8;
}

.chances-card {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-dark), var(--color-primary));
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chances-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.chances-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.ticket-icon {
  font-size: 32rpx;
}

.chances-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.chances-num {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: #fff;
}

.chances-hint {
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.75);
}

.chances-arrow {
  font-size: 32rpx;
  color: #fff;
}

.wheel-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx;
}

.wheel-container {
  position: relative;
  width: 520rpx;
  height: 520rpx;
}

.wheel-outer-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 8rpx solid var(--color-primary);
  box-shadow:
    0 0 0 12rpx var(--color-primary-light),
    0 0 0 20rpx var(--color-primary-light),
    var(--shadow-lg);
}

.wheel-pointer {
  position: absolute;
  top: -4rpx;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  width: 0;
  height: 0;
  border-left: 24rpx solid transparent;
  border-right: 24rpx solid transparent;
  border-top: 48rpx solid var(--color-primary);
  filter: drop-shadow(0 2rpx 4rpx rgba(0, 0, 0, 0.2));
}

.wheel-body {
  position: absolute;
  inset: 8rpx;
  border-radius: 50%;
  overflow: hidden;
  background: conic-gradient(
    #dbc98a 0deg 45deg,
    #abb9ad 45deg 90deg,
    #e6b97a 90deg 135deg,
    #b38a5a 135deg 180deg,
    #af8f6f 180deg 225deg,
    #c96e5f 225deg 270deg,
    #8fa8b6 270deg 315deg,
    #d9b4b0 315deg 360deg
  );
  transition: transform 4s cubic-bezier(0.17, 0.67, 0.12, 0.99);
}

.wheel-labels {
  position: absolute;
  inset: 0;
}

.wheel-label {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 50%;
  height: 4rpx;
  display: flex;
  align-items: center;
  font-size: 18rpx;
  font-weight: var(--font-weight-bold);
  color: #fff;
  text-shadow: 0 1rpx 2rpx rgba(0, 0, 0, 0.3);
  padding-left: 40%;
  white-space: nowrap;
}

.wheel-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 20;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: var(--color-primary);
  border: 6rpx solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 24rpx rgba(219, 201, 138, 0.4);
}

.center-text {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

.wheel-tip {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 24rpx;
  text-align: center;
}

.prize-list-section {
  padding: 0 24rpx 24rpx;
}

.section-title {
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.prize-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
}

.prize-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.prize-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.prize-icon {
  font-size: 32rpx;
}

.prize-name {
  font-size: 20rpx;
  color: var(--color-text-secondary);
  text-align: center;
}

.history-section {
  margin: 0 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1rpx solid var(--color-divider);
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
}

.history-title-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.history-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.history-count {
  font-size: var(--font-size-xs);
  padding: 2rpx 12rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.history-arrow {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  transition: transform 0.2s;
}

.history-arrow.open {
  transform: rotate(180deg);
}

.history-list {
  padding: 0 24rpx 24rpx;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.history-item:last-child {
  border-bottom: none;
}

.history-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-icon {
  font-size: 28rpx;
}

.history-name {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.history-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.history-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.history-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-content {
  width: 80%;
  max-width: 560rpx;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 48rpx;
  text-align: center;
}

.modal-icon-wrap {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24rpx;
}

.modal-icon {
  font-size: 56rpx;
}

.modal-title {
  display: block;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.modal-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: 32rpx;
}

.modal-btn {
  height: 80rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
</style>
