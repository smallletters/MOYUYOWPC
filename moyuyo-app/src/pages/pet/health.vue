<template>
  <view class="health">
    <view class="header">
      <text class="title">Health Calendar</text>
      <text class="subtitle">{{ petName }}'s Care Schedule</text>
    </view>

    <view class="calendar card">
      <view class="calendar-header">
        <text class="month">{{ currentYear }}-{{ String(currentMonth + 1).padStart(2, '0') }}</text>
        <view class="nav">
          <text @click="prevMonth"><text class="luc luc-arrow-left" /></text>
          <text @click="nextMonth"><text class="luc luc-chevron-right" /></text>
        </view>
      </view>
      <view class="weekdays">
        <text v-for="d in ['S', 'M', 'T', 'W', 'T', 'F', 'S']" :key="d" class="weekday">
          {{ d }}
        </text>
      </view>
      <view class="days">
        <view
          v-for="(day, i) in days"
          :key="i"
          class="day"
          :class="{ 'has-event': day.events?.length, today: day.isToday, other: !day.current }"
          @click="onDayClick(day)"
        >
          <text>{{ day.date }}</text>
          <view v-if="day.events?.length" class="event-dot" />
        </view>
      </view>
    </view>

    <view class="card reminders">
      <text class="card-title">Upcoming Reminders</text>
      <view v-for="r in upcomingReminders" :key="r.id" class="reminder-item">
        <text class="reminder-icon luc" :class="$luc(r.icon)" />
        <view class="reminder-info">
          <text class="reminder-title">{{ r.title }}</text>
          <text class="reminder-date">{{ r.date }}</text>
        </view>
        <text class="reminder-countdown">{{ r.countdown }}</text>
      </view>
      <view v-if="upcomingReminders.length === 0" class="empty-reminders">
        No upcoming reminders
      </view>
    </view>
  </view>
</template>
<script>
import { petApi } from '@/api'

const REMINDER_META = {
  BATH: { icon: 'bath', title: 'Bathing' },
  VACCINE: { icon: 'syringe', title: 'Vaccine' },
  DEWORM: { icon: 'pill', title: 'Deworming' },
  EXAM: { icon: 'stethoscope', title: 'Checkup' },
}

export default {
  data() {
    const now = new Date()
    return {
      petId: null,
      petName: 'Pet',
      reminders: [],
      currentYear: now.getFullYear(),
      currentMonth: now.getMonth(),
      today: now,
    }
  },

  computed: {
    days() {
      const firstDay = new Date(this.currentYear, this.currentMonth, 1)
      const lastDay = new Date(this.currentYear, this.currentMonth + 1, 0)
      const startDay = firstDay.getDay()
      const totalDays = lastDay.getDate()
      const days = []
      const prevLast = new Date(this.currentYear, this.currentMonth, 0).getDate()
      for (let i = startDay - 1; i >= 0; i--) {
        days.push({ date: prevLast - i, current: false, events: null })
      }
      for (let i = 1; i <= totalDays; i++) {
        const key = `${this.currentYear}-${String(this.currentMonth + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`
        const isToday = this.isToday(i)
        days.push({ date: i, current: true, isToday, events: this.buildEventsForDate(key) })
      }
      const remaining = 42 - days.length
      for (let i = 1; i <= remaining; i++) {
        days.push({ date: i, current: false, events: null })
      }
      return days
    },
    upcomingReminders() {
      return this.reminders
        .filter((r) => r.enabled !== false)
        .map((r) => {
          const meta = REMINDER_META[r.reminderType] || { icon: 'map-pin', title: r.reminderType }
          const next = r.nextDate ? new Date(r.nextDate) : null
          const diff = next ? Math.ceil((next - this.today) / (1000 * 60 * 60 * 24)) : '-'
          return {
            id: r.id,
            icon: meta.icon,
            title: meta.title,
            date: r.nextDate || '-',
            countdown: diff > 0 ? `${diff} days` : diff === 0 ? 'Today' : 'Overdue',
          }
        })
        .sort((a, b) => (a.date > b.date ? 1 : -1))
    },
  },

  onLoad(query) {
    this.petId = query.petId || null
    this.loadData()
  },

  methods: {
    async loadData() {
      if (!this.petId) return
      try {
        const pet = await petApi.getPetDetail(this.petId)
        if (pet) this.petName = pet.name
        this.reminders = await petApi.getReminders(this.petId)
      } catch (e) {
        console.warn('[health] load failed', e)
      }
    },

    buildEventsForDate(key) {
      const events = []
      for (const r of this.reminders) {
        if (r.nextDate === key && r.enabled !== false) {
          const meta = REMINDER_META[r.reminderType] || { title: r.reminderType }
          events.push({ type: r.reminderType, title: meta.title })
        }
      }
      return events.length > 0 ? events : null
    },

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
    },
  },
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
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
}
.subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}
.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.month {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}
.nav {
  display: flex;
  gap: 24rpx;
  font-size: 36rpx;
  color: var(--color-text-secondary);
}
.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  margin-bottom: 8rpx;
}
.weekday {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  padding: 8rpx 0;
}
.days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
}
.day {
  padding: 12rpx 0;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  position: relative;
}
.day.other {
  color: var(--color-text-tertiary);
}
.day.today {
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
}
.day.has-event {
  cursor: pointer;
}
.event-dot {
  width: 6rpx;
  height: 6rpx;
  background: var(--color-primary);
  border-radius: 50%;
  margin: 4rpx auto 0;
}
.reminders {
}
.card-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  display: block;
}
.reminder-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}
.reminder-item:last-child {
  border-bottom: none;
}
.reminder-icon {
  font-size: 28rpx;
  width: 40rpx;
  text-align: center;
}
.reminder-info {
  flex: 1;
}
.reminder-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}
.reminder-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.reminder-countdown {
  font-size: var(--font-size-xs);
  color: var(--color-primary);
}
.empty-reminders {
  text-align: center;
  padding: 24rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
