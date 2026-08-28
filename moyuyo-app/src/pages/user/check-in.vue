<template>
  <view class="check-in">
    <view class="nav-header">
      <view class="nav-back" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="nav-title">每日签到</text>
      <view class="nav-placeholder" />
    </view>

    <view class="content">
      <view class="calendar-card">
        <view class="month-header">
          <view class="month-btn" @click="prevMonth">
            <text class="month-arrow"><text class="luc luc-arrow-left" /></text>
          </view>
          <text class="month-title">{{ currentYear }}年{{ currentMonth }}月</text>
          <view class="month-btn" @click="nextMonth">
            <text class="month-arrow"><text class="luc luc-chevron-right" /></text>
          </view>
        </view>

        <view class="weekday-row">
          <text v-for="day in weekdays" :key="day" class="weekday">{{ day }}</text>
        </view>

        <view class="calendar-grid">
          <view v-for="(cell, idx) in calendarCells" :key="idx" class="calendar-cell">
            <block v-if="cell.day">
              <view
                class="day-circle"
                :class="{
                  checked: cell.checked,
                  today: cell.today,
                  future: cell.future,
                }"
              >
                <text v-if="cell.checked" class="check-icon">
                  <text class="luc luc-check" />
                </text>
                <text v-else-if="cell.today" class="today-text">签</text>
                <text v-else class="day-num">{{ cell.day }}</text>
              </view>
            </block>
            <view v-else class="day-empty" />
          </view>
        </view>
      </view>

      <view class="streak-card">
        <view class="streak-header">
          <text class="streak-star"><text class="luc luc-star" /></text>
          <text class="streak-title">已连续签到 {{ streak }} 天</text>
        </view>
        <text class="streak-hint">连续 7 天签到积分 x2 倍率</text>
        <view class="streak-bar">
          <view class="streak-track">
            <view class="streak-fill" :style="{ width: (streak / 7) * 100 + '%' }" />
          </view>
          <view class="streak-labels">
            <text class="streak-label">本周进度</text>
            <text class="streak-count">{{ streak }} / 7</text>
          </view>
        </view>
      </view>

      <view class="reward-card">
        <text class="reward-title">本周签到奖励</text>
        <view class="reward-grid">
          <view v-for="(reward, idx) in weeklyRewards" :key="idx" class="reward-item">
            <view
              class="reward-circle"
              :class="{ claimed: reward.claimed, current: reward.current }"
            >
              <text v-if="reward.claimed" class="reward-check">
                <text class="luc luc-check" />
              </text>
              <text v-else-if="reward.current" class="reward-star">
                <text class="luc luc-star" />
              </text>
              <text v-else class="reward-day">{{ '第' + (idx + 1) + '天' }}</text>
            </view>
            <text class="reward-label">第{{ idx + 1 }}天</text>
            <view class="reward-points-row">
              <text class="reward-points">+{{ reward.points }}</text>
              <text v-if="reward.x2" class="reward-x2">(x2)</text>
            </view>
          </view>
        </view>
      </view>

      <view class="check-in-btn" :class="{ checked: isCheckedIn }" @click="onCheckIn">
        <text>{{ isCheckedIn ? '已签到' : '立即签到' }}</text>
      </view>

      <view v-if="showSuccess" class="success-section">
        <view class="success-icon-wrap">
          <view class="success-icon-anim">
            <text class="success-check"><text class="luc luc-check" /></text>
          </view>
        </view>
        <text class="success-title">签到成功</text>
        <text class="success-points">
          获得 +{{ earnedPoints }} 积分{{ earnedX2 ? ' (x2 倍率)' : '' }}
        </text>
      </view>

      <text class="rules-text">每月可免费补签 1 次，之后 50 积分/次</text>
    </view>
  </view>
</template>

<script>
import { pointsApi } from '@/api'

export default {
  data() {
    const now = new Date()
    return {
      currentYear: now.getFullYear(),
      currentMonth: now.getMonth() + 1,
      streak: 0,
      isCheckedIn: false,
      showSuccess: false,
      earnedPoints: 0,
      earnedX2: false,
      weekdays: ['日', '一', '二', '三', '四', '五', '六'],
      checkedDays: [],
      weeklyRewards: [],
    }
  },

  computed: {
    calendarCells() {
      const { currentYear, currentMonth } = this
      const today = new Date()
      const firstDay = new Date(currentYear, currentMonth - 1, 1)
      const lastDay = new Date(currentYear, currentMonth, 0)
      const daysInMonth = lastDay.getDate()
      const startWeekday = firstDay.getDay()

      const cells = []

      for (let i = 0; i < startWeekday; i++) {
        cells.push({ day: null })
      }

      for (let d = 1; d <= daysInMonth; d++) {
        const date = new Date(currentYear, currentMonth - 1, d)
        const isToday = date.toDateString() === today.toDateString()
        const isFuture = date > today
        const isChecked = this.checkedDays.includes(d)

        cells.push({
          day: d,
          today: isToday,
          future: isFuture,
          checked: isChecked,
        })
      }

      return cells
    },
  },

  onLoad() {
    this.loadCheckinStatus()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadCheckinStatus() {
      try {
        // 后端 Page<PointsLogEntity>：{ records, total, current, size }
        // request.js 已解包,res 即 IPage 本身
        const page = await pointsApi.getPointsLog({ page: 1, size: 50 })
        const logs = page?.records || page?.list || []
        const checkedDays = []
        const today = new Date()
        for (const log of logs) {
          if (log.type === 'CHECKIN' || log.type === 'checkin') {
            const d = new Date(log.createdAt)
            if (d.getFullYear() === today.getFullYear() && d.getMonth() === today.getMonth()) {
              checkedDays.push(d.getDate())
            }
          }
        }
        this.checkedDays = checkedDays
        this.isCheckedIn = checkedDays.includes(today.getDate())

        // 从日志计算连续签到天数
        this.streak = this.calculateStreak(logs, today)
      } catch {
        this.checkedDays = []
        this.isCheckedIn = false
      }
    },

    calculateStreak(logs, today) {
      const checkinDates = logs
        .filter((l) => l.type === 'CHECKIN' || l.type === 'checkin')
        .map((l) => {
          const d = new Date(l.createdAt)
          return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
        })
      const uniqueDates = [...new Set(checkinDates)].sort().reverse()
      let streak = 0
      const cursor = new Date(today)
      for (const dateStr of uniqueDates) {
        const expected = `${cursor.getFullYear()}-${cursor.getMonth() + 1}-${cursor.getDate()}`
        if (dateStr === expected) {
          streak++
          cursor.setDate(cursor.getDate() - 1)
        } else {
          break
        }
      }
      return streak
    },

    prevMonth() {
      if (this.currentMonth === 1) {
        this.currentYear--
        this.currentMonth = 12
      } else {
        this.currentMonth--
      }
    },

    nextMonth() {
      if (this.currentMonth === 12) {
        this.currentYear++
        this.currentMonth = 1
      } else {
        this.currentMonth++
      }
    },

    async onCheckIn() {
      if (this.isCheckedIn) return

      try {
        // request.js 已解包,res 即 payload 本身: { points, consecutiveDays, doubleReward }
        const result = await pointsApi.checkin()
        this.isCheckedIn = true
        this.showSuccess = true
        this.earnedPoints = result?.points || 5
        this.earnedX2 = !!result?.doubleReward
        if (typeof result?.consecutiveDays === 'number') {
          this.streak = result.consecutiveDays
        } else {
          this.streak += 1
        }

        const today = new Date()
        if (!this.checkedDays.includes(today.getDate())) {
          this.checkedDays.push(today.getDate())
        }

        uni.showToast({ title: `签到成功 +${this.earnedPoints} 积分`, icon: 'success' })
      } catch (e) {
        // 后端返回 IllegalStateException 时前端显示具体文案
        const msg = (e && e.message) || ''
        if (msg.includes('今日已签到')) {
          this.isCheckedIn = true
          uni.showToast({ title: '今日已签到', icon: 'none' })
        } else {
          uni.showToast({ title: '签到失败，请重试', icon: 'none' })
        }
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.check-in {
  min-height: 100vh;
  background: var(--color-background, #ffffff);
  display: flex;
  flex-direction: column;
}

.nav-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--color-background, #ffffff);
  border-bottom: 1rpx solid #e5e5ea;
  position: sticky;
  top: 0;
  z-index: 20;
}

.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.back-icon {
  font-size: 40rpx;
  color: var(--color-primary, #007aff);
  font-weight: 300;
}

.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
}

.nav-placeholder {
  width: 64rpx;
}

.content {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.calendar-card {
  background: var(--color-background, #ffffff);
  border: 1rpx solid #e5e5ea;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.month-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.month-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.month-arrow {
  font-size: 32rpx;
  color: #2c2c2e;
  font-weight: 300;
}

.month-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
}

.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
  margin-bottom: 16rpx;
}

.weekday {
  text-align: center;
  font-size: 24rpx;
  font-weight: 500;
  padding: 8rpx 0;
  color: #8e8e93;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
}

.calendar-cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-empty {
  width: 100%;
  height: 100%;
}

.day-circle {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-circle.checked {
  background: var(--color-primary, #007aff);
}

.check-icon {
  font-size: 28rpx;
  color: #ffffff;
  font-weight: 700;
}

.day-circle.today {
  border: 4rpx dashed var(--color-primary, #007aff);
  background: transparent;
}

.today-text {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-primary, #007aff);
}

.day-circle.future {
  background: transparent;
}

.day-num {
  font-size: 28rpx;
  font-weight: 500;
  color: #aeaeb2;
}

.streak-card {
  background: var(--color-background, #ffffff);
  border: 1rpx solid #e5e5ea;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.streak-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.streak-star {
  font-size: 36rpx;
}

.streak-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
}

.streak-hint {
  font-size: 28rpx;
  color: #6e6e73;
  margin-bottom: 24rpx;
}

.streak-bar {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.streak-track {
  width: 100%;
  height: 16rpx;
  border-radius: 8rpx;
  background: #f2f2f7;
  overflow: hidden;
}

.streak-fill {
  height: 100%;
  border-radius: 8rpx;
  background: var(--color-primary, #007aff);
  transition: width 0.4s ease;
}

.streak-labels {
  display: flex;
  justify-content: space-between;
}

.streak-label {
  font-size: 24rpx;
  color: #8e8e93;
}

.streak-count {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-primary, #007aff);
}

.reward-card {
  background: var(--color-background, #ffffff);
  border: 1rpx solid #e5e5ea;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.reward-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
  margin-bottom: 24rpx;
}

.reward-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 16rpx;
}

.reward-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.reward-circle {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}

.reward-circle.claimed {
  background: var(--color-primary, #007aff);
}

.reward-circle.current {
  border: 4rpx dashed var(--color-primary, #007aff);
  background: transparent;
}

.reward-circle:not(.claimed):not(.current) {
  background: transparent;
}

.reward-check {
  font-size: 28rpx;
  color: #ffffff;
  font-weight: 700;
}

.reward-star {
  font-size: 28rpx;
  color: var(--color-primary, #007aff);
}

.reward-day {
  font-size: 20rpx;
  color: #8e8e93;
  font-weight: 500;
}

.reward-label {
  font-size: 20rpx;
  font-weight: 500;
  color: #6e6e73;
}

.reward-points-row {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.reward-points {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--color-primary, #007aff);
}

.reward-x2 {
  font-size: 20rpx;
  font-weight: 700;
  color: var(--color-success, #34c759);
}

.check-in-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: 600;
  background: var(--color-primary, #007aff);
  color: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(0, 122, 255, 0.3);
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.check-in-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

.check-in-btn.checked {
  background: #e5e5ea;
  color: #8e8e93;
  box-shadow: none;
}

.success-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64rpx 0;
}

.success-icon-wrap {
  margin-bottom: 24rpx;
}

.success-icon-anim {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: var(--color-success, #34c759);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: checkInPop 0.5s ease-out forwards;
}

.success-check {
  font-size: 64rpx;
  color: #ffffff;
  font-weight: 700;
}

.success-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-success, #34c759);
  margin-bottom: 8rpx;
}

.success-points {
  font-size: 28rpx;
  color: #6e6e73;
}

.rules-text {
  text-align: center;
  font-size: 24rpx;
  color: #8e8e93;
  padding-bottom: 32rpx;
}

@keyframes checkInPop {
  0% {
    transform: scale(0.3);
    opacity: 0;
  }
  50% {
    transform: scale(1.15);
    opacity: 1;
  }
  70% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
