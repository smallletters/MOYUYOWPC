<template>
  <view class="check-in">
    <view class="content">
      <view class="calendar-card">
        <view class="month-header">
          <view class="month-btn" @click="prevMonth">
            <text class="month-arrow luc-arrow-left" />
          </view>
          <text class="month-title">
            {{ $t('checkIn.monthTitle', { year: currentYear, month: currentMonth }) }}
          </text>
          <view class="month-btn" @click="nextMonth">
            <text class="month-arrow luc-chevron-right" />
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
                <text v-else-if="cell.today" class="today-text">{{ $t('checkIn.todayMark') }}</text>
                <text v-else class="day-num">{{ cell.day }}</text>
              </view>
            </block>
            <view v-else class="day-empty" />
          </view>
        </view>
      </view>

      <view class="streak-card">
        <view class="streak-header">
          <text class="streak-star luc-star" />
          <text class="streak-title">{{ $t('checkIn.streakTitle', { days: streak }) }}</text>
        </view>
        <text class="streak-hint">{{ $t('checkIn.streakHint') }}</text>
        <view class="streak-bar">
          <view class="streak-track">
            <view class="streak-fill" :style="{ width: (weeklyProgress / 7) * 100 + '%' }" />
          </view>
          <view class="streak-labels">
            <text class="streak-label">{{ $t('checkIn.weekProgress') }}</text>
            <text class="streak-count">{{ weeklyProgress }} / 7</text>
          </view>
        </view>
      </view>

      <view class="reward-card">
        <text class="reward-title">{{ $t('checkIn.weekRewardTitle') }}</text>
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
              <text v-else class="reward-day">{{ $t('checkIn.dayLabel', { day: idx + 1 }) }}</text>
            </view>
            <text class="reward-label">{{ $t('checkIn.dayLabel', { day: idx + 1 }) }}</text>
            <view class="reward-points-row">
              <text class="reward-points">+{{ reward.points }}</text>
              <text v-if="reward.x2" class="reward-x2">(x2)</text>
            </view>
          </view>
        </view>
      </view>

      <view class="check-in-btn" :class="{ checked: isCheckedIn }" @click="onCheckIn">
        <text>{{ isCheckedIn ? $t('checkIn.checked') : $t('checkIn.checkInNow') }}</text>
      </view>

      <view v-if="showSuccess" class="success-section">
        <view class="success-icon-wrap">
          <view class="success-icon-anim">
            <text class="success-check luc-check" />
          </view>
        </view>
        <text class="success-title">{{ $t('checkIn.successTitle') }}</text>
        <text class="success-points">
          {{ $t('checkIn.successPoints', { points: earnedPoints })
          }}{{ earnedX2 ? $t('checkIn.x2Suffix') : '' }}
        </text>
      </view>

      <text class="rules-text">{{ $t('checkIn.makeupRule') }}</text>
    </view>
  </view>
</template>

<script>
import { pointsApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userCheckIn',

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
      localeVersion: 0,
      unsubLocale: null,
      // 已签到日期集合（yyyy-MM-dd，服务端返回，按月份变化）
      checkedDates: [],
    }
  },

  computed: {
    // 星期表头（随语言切换刷新）
    weekdays() {
      void this.localeVersion
      return i18n.t('checkIn.weekdays')
    },

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
        // 用完整日期串比较，避免切换月份时"同一天号"被误标为已签到
        const isChecked = this.checkedDates.includes(this.toDateKey(currentYear, currentMonth, d))

        cells.push({
          day: d,
          today: isToday,
          future: isFuture,
          checked: isChecked,
        })
      }

      return cells
    },

    /** 7 天签到周期的进度（连续天数按 7 天封顶，用于进度条与"x/7"文案） */
    weeklyProgress() {
      return Math.min(this.streak, 7)
    },

    /**
     * 7 天签到奖励进度，与后端倍率规则保持一致（连续 7 天起 ×2）：
     * 第 1~6 天 +5 积分，第 7 天 +10 积分（x2）
     * claimed=已领取的格子，current=当前进行到的格子
     */
    weeklyRewards() {
      const done = this.weeklyProgress
      return Array.from({ length: 7 }, (_, i) => ({
        points: i === 6 ? 10 : 5,
        x2: i === 6,
        claimed: i < done,
        current: i === done && done < 7,
      }))
    },
  },

  onLoad() {
    this.loadCheckinStatus()
  },

  created() {
    // 订阅语言变化，刷新星期表头等依赖本地化的内容
    this.unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  beforeUnmount() {
    if (this.unsubLocale) this.unsubLocale()
  },

  methods: {
    /** 生成 yyyy-MM-dd 日期串（与后端 dates 字段格式一致） */
    toDateKey(year, month, day) {
      return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    },

    /**
     * 拉取当前展示月份的签到日历。
     * 数据全部来自服务端（已签到日期 / 连续天数 / 今天是否已签），
     * 不再用"最近 50 条积分流水"自行推算，避免流水被任务奖励等挤掉导致日历或连续天数不准。
     */
    async loadCheckinStatus() {
      const { currentYear, currentMonth } = this
      const month = `${currentYear}-${String(currentMonth).padStart(2, '0')}`
      try {
        const data = (await pointsApi.getCheckinCalendar({ month })) || {}
        // 连续快速切月会有多个请求在飞，丢弃过期响应，避免日历数据与标题月份对不上
        if (this.currentYear !== currentYear || this.currentMonth !== currentMonth) {
          return
        }
        this.checkedDates = Array.isArray(data.dates) ? data.dates : []
        this.streak = data.streak || 0
        // "今天是否已签到"只与当月视图有关，切到历史月份时不能改按钮状态
        const now = new Date()
        if (currentYear === now.getFullYear() && currentMonth === now.getMonth() + 1) {
          this.isCheckedIn = !!data.checkedToday
        }
      } catch {
        this.checkedDates = []
      }
    },

    prevMonth() {
      if (this.currentMonth === 1) {
        this.currentYear--
        this.currentMonth = 12
      } else {
        this.currentMonth--
      }
      // 换月后重新拉该月的签到数据，否则日历只会显示当月数据
      this.loadCheckinStatus()
    },

    nextMonth() {
      if (this.currentMonth === 12) {
        this.currentYear++
        this.currentMonth = 1
      } else {
        this.currentMonth++
      }
      this.loadCheckinStatus()
    },

    async onCheckIn() {
      if (this.isCheckedIn) return

      try {
        // request.js 已解包,res 即 payload 本身: { points, consecutiveDays, doubleReward }
        const result = await pointsApi.checkin()
        this.showSuccess = true
        this.earnedPoints = result?.points || 5
        this.earnedX2 = !!result?.doubleReward
        // 重新拉服务端日历：当日签到状态与连续天数都以服务端为准。
        // 注意不能用签到响应里的 consecutiveDays 覆盖 streak —— 它只回溯 7 天，
        // 长连签（如 30 天）时会被截断成 8
        await this.loadCheckinStatus()
        this.isCheckedIn = true

        uni.showToast({
          title: i18n.t('checkIn.toastSuccess', { points: this.earnedPoints }),
          icon: 'success',
        })
      } catch (e) {
        // 后端返回 IllegalStateException 时前端显示具体文案
        const msg = (e && e.message) || ''
        if (msg.includes('今日已签到')) {
          this.isCheckedIn = true
          uni.showToast({ title: i18n.t('checkIn.todayChecked'), icon: 'none' })
        } else {
          uni.showToast({ title: i18n.t('checkIn.toastFailed'), icon: 'none' })
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
  /* 7 列在窄屏上很挤，压缩列间距给圆点留出空间 */
  gap: 8rpx;
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
  justify-content: center;
  /* 第 7 天是 "+10 (x2)"，窄屏放不下时换行而不是溢出格子 */
  flex-wrap: wrap;
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
