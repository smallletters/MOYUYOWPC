<template>
  <view class="health">
    <view class="header">
      <text class="title">Health Calendar</text>
      <text class="subtitle">{{ currentPet.name }}'s Care Schedule</text>
    </view>

    <view class="calendar card">
      <view class="calendar-header">
        <text class="month">{{ currentYear }}-{{ String(currentMonth + 1).padStart(2, '0') }}</text>
        <view class="nav">
          <text @click="prevMonth">‹</text>
          <text @click="nextMonth">›</text>
        </view>
      </view>
      <view class="weekdays">
        <text v-for="d in ['S','M','T','W','T','F','S']" :key="d" class="weekday">{{ d }}</text>
      </view>
      <view class="days">
        <view
          v-for="(day, i) in days"
          :key="i"
          class="day"
          :class="{
            'has-event': day.events?.length,
            'today': day.isToday,
            'other': !day.current
          }"
          @click="onDayClick(day)"
        >
          <text>{{ day.date }}</text>
          <view v-if="day.events?.length" class="event-dot"></view>
        </view>
      </view>
    </view>

    <!-- 未来 30 天提醒列表 -->
    <view class="card reminders">
      <text class="card-title">Upcoming Reminders</text>
      <view v-for="r in upcomingReminders" :key="r.id" class="reminder-item">
        <text class="reminder-icon">{{ r.icon }}</text>
        <view class="reminder-info">
          <text class="reminder-title">{{ r.title }}</text>
          <text class="reminder-date">{{ r.date }}</text>
        </view>
        <text class="reminder-countdown">{{ r.countdown }}</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    const now = new Date()
    return {
      currentPet: { name: 'MILO' },
      currentYear: now.getFullYear(),
      currentMonth: now.getMonth(),
      today: now,
      events: {
        // 示例事件
        [`${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate() + 5}`]: [{ type: 'bath' }],
        [`${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate() + 14}`]: [{ type: 'deworm' }],
        [`${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate() + 30}`]: [{ type: 'vaccine' }]
      }
    }
  },

  computed: {
    days() {
      const firstDay = new Date(this.currentYear, this.currentMonth, 1)
      const lastDay = new Date(this.currentYear, this.currentMonth + 1, 0)
      const startDay = firstDay.getDay()
      const totalDays = lastDay.getDate()
      const days = []

      // 上月填充
      const prevLast = new Date(this.currentYear, this.currentMonth, 0).getDate()
      for (let i = startDay - 1; i >= 0; i--) {
        days.push({ date: prevLast - i, current: false, events: null })
      }
      // 本月
      for (let i = 1; i <= totalDays; i++) {
        const key = `${this.currentYear}-${this.currentMonth + 1}-${i}`
        const isToday = this.isToday(i)
        days.push({
          date: i,
          current: true,
          isToday,
          events: this.events[key]
        })
      }
      // 下月填充
      const remaining = 42 - days.length
      for (let i = 1; i <= remaining; i++) {
        days.push({ date: i, current: false, events: null })
      }
      return days
    },
    upcomingReminders() {
      return [
        { id: 1, icon: '🛁', title: 'Bathing', date: '2026-07-14', countdown: '5 days' },
        { id: 2, icon: '💊', title: 'Deworming', date: '2026-07-23', countdown: '14 days' },
        { id: 3, icon: '💉', title: 'Vaccine', date: '2026-08-08', countdown: '30 days' }
      ]
    }
  },

  methods: {
    isToday(date) {
      return (
        date === this.today.getDate() &&
        this.currentMonth === this.today.getMonth() &&
        this.currentYear === this.today.getFullYear()
      )
    },

    prevMonth() {
      if (this.currentMonth === 0) {
        this.currentMonth = 11
        this.currentYear -= 1
      } else {
        this.currentMonth -= 1
      }
    },

    nextMonth() {
      if (this.currentMonth === 11) {
        this.currentMonth = 0
        this.currentYear += 1
      } else {
        this.currentMonth += 1
      }
    },

    onDayClick(day) {
      if (day.events?.length) {
        uni.showToast({ title: `${day.events.length} event(s)`, icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.health {
  min-height: 100vh;
  background: var(--color-background);
  padding: 32rpx 16rpx;
  padding-top: calc(32rpx + env(safe-area-inset-top));
}

.header {
  padding: 0 16rpx 24rpx;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: var(--font-weight-bold);
}

.subtitle {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.calendar {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.month {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.nav text {
  font-size: 36rpx;
  padding: 0 16rpx;
  color: var(--color-text-tertiary);
}

.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
  margin-bottom: 8rpx;
}

.weekday {
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  padding: 8rpx 0;
}

.days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8rpx;
}

.day {
  position: relative;
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-sm);
  border-radius: var(--radius-sm);
}

.day.other {
  color: var(--color-text-tertiary);
  opacity: 0.4;
}

.day.today {
  background: var(--color-primary);
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.day.has-event {
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.event-dot {
  position: absolute;
  bottom: 4rpx;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: var(--color-primary);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  display: block;
}

.reminders {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
}

.reminder-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.reminder-item:last-child {
  border-bottom: none;
}

.reminder-icon {
  font-size: 40rpx;
}

.reminder-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.reminder-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.reminder-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.reminder-countdown {
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}
</style>
