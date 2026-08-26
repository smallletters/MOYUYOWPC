<template>
  <view class="health-calendar">
    <!-- 顶部导航 -->
    <view class="header">
      <view class="header-inner">
        <view class="header-btn" @click="onBack">
          <text class="header-btn-icon"><text class="luc luc-arrow-left"></text></text>
        </view>
        <text class="header-title">健康日历</text>
        <view class="header-btn header-btn-primary" @click="onAddReminder">
          <text class="header-btn-icon header-btn-icon-white">+</text>
        </view>
      </view>
    </view>

    <!-- 宠物切换 -->
    <view class="pet-scroll">
      <view class="pet-scroll-inner">
        <view
          v-for="pet in petList"
          :key="pet.id"
          class="pet-item"
          @click="onSwitchPet(pet)">
          <view class="pet-avatar" :class="{ 'pet-avatar-active': pet.active }">
            <text class="pet-avatar-emoji">{{ pet.emoji }}</text>
          </view>
          <text class="pet-name-label" :class="{ 'pet-name-active': pet.active }">
            {{ pet.name }}
          </text>
        </view>
        <view class="pet-item" @click="onAddPet">
          <view class="pet-avatar pet-avatar-add">
            <text class="pet-add-icon">+</text>
          </view>
          <text class="pet-name-label pet-name-add">添加</text>
        </view>
      </view>
    </view>

    <!-- 日历卡片 -->
    <view class="calendar-card">
      <!-- 月份切换 -->
      <view class="calendar-header">
        <view class="cal-nav-btn" @click="prevMonth">
          <text class="cal-nav-arrow"><text class="luc luc-arrow-left"></text></text>
        </view>
        <text class="cal-month-label">{{ currentYear }}年{{ currentMonth + 1 }}月</text>
        <view class="cal-nav-btn" @click="nextMonth">
          <text class="cal-nav-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
      </view>

      <!-- 星期标题 -->
      <view class="cal-weekdays">
        <view
          v-for="d in weekdays"
          :key="d.label"
          class="cal-weekday"
          :class="{ 'cal-weekend': d.isWeekend }"
        >
          <text class="cal-weekday-text">{{ d.label }}</text>
        </view>
      </view>

      <!-- 日期网格 -->
      <view class="cal-grid">
        <view
          v-for="(day, dIdx) in calendarDays"
          :key="dIdx"
          class="cal-cell"
          :class="{
            'cal-cell-other': !day.isCurrent,
            'cal-cell-today': day.isToday,
            'cal-cell-selected': day.isSelected,
          }"
          @click="onSelectDay(day)"
        >
          <text class="cal-day-num" :class="{ 'cal-day-num-selected': day.isSelected }">
            {{ day.date }}
          </text>
          <view v-if="day.dots && day.dots.length > 0" class="cal-dots">
            <view
              v-for="(dot, dotIdx) in day.dots"
              :key="dotIdx"
              class="cal-dot"
              :class="'dot-' + dot"
            />
          </view>
        </view>
      </view>

      <!-- 图例 -->
      <view class="cal-legend">
        <view v-for="item in legendItems" :key="item.type" class="legend-item">
          <view class="legend-dot" :class="'dot-' + item.type" />
          <text class="legend-text">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <!-- 选中日期详情 -->
    <view class="detail-card">
      <view class="detail-header">
        <view class="detail-title-row">
          <text class="detail-date">{{ selectedDateLabel }}</text>
          <text v-if="selectedDay.isToday" class="detail-today-tag">今天</text>
        </view>
        <text class="detail-pet-name">{{ currentPetName }}</text>
      </view>

      <view v-if="selectedEvents.length > 0" class="detail-list">
        <view v-for="(evt, eIdx) in selectedEvents" :key="eIdx" class="detail-item">
          <view class="detail-item-icon" :class="'detail-icon-' + evt.type">
            <text class="detail-icon-emoji luc" :class="$luc(evt.icon)"></text>
          </view>
          <view class="detail-item-info">
            <view class="detail-item-top">
              <view class="detail-item-tag" :class="'tag-' + evt.type">
                <text class="detail-item-tag-text">{{ evt.typeLabel }}</text>
              </view>
              <text class="detail-item-time">{{ evt.time }}</text>
            </view>
            <text class="detail-item-desc">{{ evt.desc }}</text>
          </view>
        </view>
      </view>

      <view v-else class="detail-empty">
        <text class="detail-empty-text">暂无记录</text>
      </view>
    </view>

    <!-- 近期提醒 -->
    <view class="reminder-card">
      <view class="reminder-header">
        <text class="reminder-title">近期提醒</text>
        <text class="reminder-more">查看全部</text>
      </view>

      <view class="reminder-list">
        <view
          v-for="(rem, rIdx) in upcomingReminders"
          :key="rIdx"
          class="reminder-item"
          :class="{ 'reminder-overdue': rem.overdue }"
        >
          <view class="reminder-icon-wrap" :class="'reminder-icon-' + rem.type">
            <text class="reminder-icon-emoji luc" :class="$luc(rem.icon)"></text>
          </view>
          <view class="reminder-info">
            <view class="reminder-info-top">
              <view class="reminder-tag" :class="'tag-' + rem.type">
                <text class="reminder-tag-text">{{ rem.typeLabel }}</text>
              </view>
              <text class="reminder-status" :class="{ 'reminder-status-overdue': rem.overdue }">
                {{ rem.statusText }}
              </text>
            </view>
            <text class="reminder-desc">预计日期：{{ rem.date }} · {{ rem.desc }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 本月统计 -->
    <view class="stat-card">
      <text class="stat-title">本月概览</text>
      <view class="stat-grid">
        <view
          v-for="stat in monthlyStats"
          :key="stat.type"
          class="stat-item"
          :style="{ background: stat.bgColor }"
        >
          <text class="stat-number" :style="{ color: stat.color }">{{ stat.count }}</text>
          <text class="stat-label" :style="{ color: stat.color }">{{ stat.label }}</text>
        </view>
      </view>
    </view>

    <!-- 添加提醒弹窗 -->
    <view v-if="showReminderModal" class="modal-overlay" @click="onCloseReminderModal">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-cancel" @click="onCloseReminderModal">取消</text>
          <text class="modal-title">添加提醒</text>
          <text class="modal-submit" @click="onSubmitReminder">保存</text>
        </view>
        <view class="modal-body">
          <view class="form-group">
            <text class="form-label">提醒类型</text>
            <view class="type-select-list">
              <view
                v-for="item in legendItems"
                :key="item.type"
                class="type-select-item"
                :class="{ 'type-select-active': reminderForm.type === item.type }"
                @click="reminderForm.type = item.type"
              >
                <view class="type-select-dot" :class="'dot-' + item.type" />
                <text class="type-select-text">{{ item.label }}</text>
              </view>
            </view>
          </view>
          <view class="form-group">
            <text class="form-label">日期</text>
            <picker mode="date" :value="reminderForm.date" @change="onDateChange">
              <view class="form-picker">
                <text class="form-picker-text">{{ reminderForm.date || '请选择日期' }}</text>
                <text class="form-picker-arrow"><text class="luc luc-chevron-right"></text></text>
              </view>
            </picker>
          </view>
          <view class="form-group">
            <text class="form-label">备注</text>
            <input
              v-model="reminderForm.note"
              class="form-input"
              placeholder="添加备注信息（选填）"
            >
          </view>
        </view>
      </view>
    </view>
  </view>
</template><script>
import { petApi } from '@/api'

const WEEKDAYS = [
  { label: '日', isWeekend: true },
  { label: '一', isWeekend: false },
  { label: '二', isWeekend: false },
  { label: '三', isWeekend: false },
  { label: '四', isWeekend: false },
  { label: '五', isWeekend: false },
  { label: '六', isWeekend: true },
]

const LEGEND_ITEMS = [
  { type: 'bath', label: '洗澡', color: 'var(--color-primary)' },
  { type: 'vaccine', label: '疫苗', color: 'var(--color-success)' },
  { type: 'deworm', label: '驱虫', color: 'var(--color-warning)' },
  { type: 'checkup', label: '体检', color: 'var(--color-info)' },
  { type: 'diary', label: '日记', color: 'var(--color-warm)' },
]

const TYPE_ICONS = {
  bath: 'bath',
  vaccine: 'syringe',
  deworm: 'pill',
  checkup: 'stethoscope',
  diary: 'edit-3',
}

export default {
  data() {
    const now = new Date()
    return {
      petId: null,
      currentYear: now.getFullYear(),
      currentMonth: now.getMonth(),
      today: now,
      weekdays: WEEKDAYS,
      legendItems: LEGEND_ITEMS,
      selectedDate: now.getDate(),
      showReminderModal: false,
      reminderForm: {
        type: 'bath',
        date: '',
        note: '',
      },
      currentPetName: '',
      petList: [],
      reminders: [],
      selectedEvents: [],
    }
  },

  computed: {
    eventMap() {
      const map = {}
      for (const r of this.reminders) {
        if (!r.nextDate) continue
        if (!map[r.nextDate]) map[r.nextDate] = []
        map[r.nextDate].push(r.reminderType || r.type)
      }
      return map
    },

    calendarDays() {
      const firstDay = new Date(this.currentYear, this.currentMonth, 1)
      const lastDay = new Date(this.currentYear, this.currentMonth + 1, 0)
      const startWeekday = firstDay.getDay()
      const totalDays = lastDay.getDate()
      const prevLastDay = new Date(this.currentYear, this.currentMonth, 0).getDate()
      const days = []
      for (let i = startWeekday - 1; i >= 0; i--) {
        const d = prevLastDay - i
        days.push({
          date: d,
          isCurrent: false,
          isToday: false,
          isSelected: false,
          dots: null,
          fullDate: `${this.currentYear}-${this.currentMonth}-${d}`,
        })
      }
      for (let i = 1; i <= totalDays; i++) {
        const isToday = this.isToday(i)
        const fullDate = `${this.currentYear}-${this.currentMonth + 1}-${i}`
        const isSelected = i === this.selectedDate && !isToday
        days.push({
          date: i,
          isCurrent: true,
          isToday,
          isSelected,
          dots: this.eventMap[fullDate] || null,
          fullDate,
        })
      }
      const remaining = 42 - days.length
      for (let i = 1; i <= remaining; i++) {
        days.push({
          date: i,
          isCurrent: false,
          isToday: false,
          isSelected: false,
          dots: null,
          fullDate: `${this.currentYear}-${this.currentMonth + 2}-${i}`,
        })
      }
      return days
    },

    selectedDay() {
      return (
        this.calendarDays.find((d) => d.isCurrent && d.date === this.selectedDate) || {
          isToday: false,
        }
      )
    },

    selectedDateLabel() {
      return `${this.currentMonth + 1}月${this.selectedDate}日`
    },

    upcomingReminders() {
      return this.reminders.slice(0, 4).map((r) => {
        const type = r.reminderType || r.type || 'bath'
        const legend = LEGEND_ITEMS.find((l) => l.type === type)
        const nextDate = r.nextDate ? new Date(r.nextDate) : null
        const diff = nextDate ? Math.ceil((nextDate - this.today) / (1000 * 60 * 60 * 24)) : 0
        return {
          type,
          icon: TYPE_ICONS[type] || 'map-pin',
          typeLabel: legend ? legend.label : type,
          date: r.nextDate || '-',
          desc: r.note || r.desc || '',
          statusText:
            diff < 0 ? `已过期 ${Math.abs(diff)} 天` : diff === 0 ? '今天' : `还剩 ${diff} 天`,
          overdue: diff < 0,
        }
      })
    },

    monthlyStats() {
      const counts = {}
      for (const r of this.reminders) {
        if (!r.nextDate) continue
        const type = r.reminderType || r.type
        counts[type] = (counts[type] || 0) + 1
      }
      return Object.entries(counts).map(([type, count]) => {
        const legend = LEGEND_ITEMS.find((l) => l.type === type)
        return {
          type,
          label: legend ? legend.label : type,
          count,
          color: legend ? legend.color : 'var(--color-text)',
          bgColor: `${legend ? legend.color : 'var(--color-divider)'}15`,
        }
      })
    },
  },

  onLoad(query) {
    this.petId = query.petId || null
    this.loadData()
  },

  methods: {
    onBack() {
      uni.navigateBack()
    },

    async loadData() {
      try {
        const [pets, reminders] = await Promise.all([
          petApi.getPets(),
          this.petId ? petApi.getReminders(this.petId) : Promise.resolve([]),
        ])
        this.petList = pets.map((p) => ({
          ...p,
          active: p.id === this.petId,
        }))
        this.reminders = reminders || []
        const activePet = this.petList.find((p) => p.active) || this.petList[0]
        if (activePet) {
          this.currentPetName = activePet.name
          if (!this.petId) {
            this.petId = activePet.id
            this.loadData()
          }
        }
      } catch (e) {
        console.warn('[health-calendar] load failed', e)
        this.reminders = []
      }
    },

    onSwitchPet(pet) {
      this.petList.forEach((p) => {
        p.active = false
      })
      pet.active = true
      this.currentPetName = pet.name
      this.petId = pet.id
      this.selectedDate = this.today.getDate()
      this.loadData()
    },

    onAddPet() {
      uni.showToast({ title: '添加宠物', icon: 'none' })
    },

    prevMonth() {
      if (this.currentMonth === 0) {
        this.currentMonth = 11
        this.currentYear -= 1
      } else {
        this.currentMonth -= 1
      }
      this.selectedDate = 1
      this.updateSelectedEvents()
    },

    nextMonth() {
      if (this.currentMonth === 11) {
        this.currentMonth = 0
        this.currentYear += 1
      } else {
        this.currentMonth += 1
      }
      this.selectedDate = 1
      this.updateSelectedEvents()
    },

    isToday(date) {
      return (
        date === this.today.getDate() &&
        this.currentMonth === this.today.getMonth() &&
        this.currentYear === this.today.getFullYear()
      )
    },

    onSelectDay(day) {
      if (!day.isCurrent) return
      this.selectedDate = day.date
      this.updateSelectedEvents()
    },

    updateSelectedEvents() {
      const fullDate = `${this.currentYear}-${this.currentMonth + 1}-${this.selectedDate}`
      const types = this.eventMap[fullDate]
      if (types && types.length > 0) {
        this.selectedEvents = types.map((t) => {
          const legend = LEGEND_ITEMS.find((l) => l.type === t)
          return {
            type: t,
            icon: TYPE_ICONS[t] || 'edit-3',
            typeLabel: legend ? legend.label : t,
            time: '10:00',
            desc: `${legend ? legend.label : t}记录`,
          }
        })
      } else {
        this.selectedEvents = []
      }
    },

    onAddReminder() {
      this.reminderForm = {
        type: 'bath',
        date: '',
        note: '',
      }
      this.showReminderModal = true
    },

    onCloseReminderModal() {
      this.showReminderModal = false
    },

    async onSubmitReminder() {
      if (!this.reminderForm.date) {
        uni.showToast({ title: '请选择日期', icon: 'none' })
        return
      }
      try {
        await petApi.updateReminder(this.petId, null, {
          reminderType: this.reminderForm.type.toUpperCase(),
          nextDate: this.reminderForm.date,
          note: this.reminderForm.note,
          enabled: true,
        })
        uni.showToast({ title: '提醒已添加', icon: 'success' })
        this.showReminderModal = false
        this.loadData()
      } catch (e) {
        console.warn('[health-calendar] add reminder failed', e)
        uni.showToast({ title: '添加失败', icon: 'none' })
      }
    },

    onDateChange(e) {
      this.reminderForm.date = e.detail.value
    },
  },
}
</script>

<style lang="scss" scoped>
.health-calendar {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
}

// 顶部导航
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
}

.header-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-surface);
}

.header-btn-primary {
  background: var(--color-primary);
}

.header-btn-icon {
  font-size: 40rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-bold);
}

.header-btn-icon-white {
  color: #ffffff;
}

// 宠物横向滚动
.pet-scroll {
  padding: 16rpx 24rpx 8rpx;
  overflow-x: auto;
}

.pet-scroll-inner {
  display: flex;
  gap: 24rpx;
}

.pet-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.pet-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
  border: 2rpx solid var(--color-divider);
  transition:
    border-color 0.2s,
    transform 0.2s;
}

.pet-avatar-active {
  border-color: var(--color-primary);
  transform: scale(1.08);
}

.pet-avatar-add {
  border: 2rpx dashed var(--color-divider);
}

.pet-avatar-emoji {
  font-size: 36rpx;
}

.pet-add-icon {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
}

.pet-name-label {
  font-size: 20rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-tertiary);
}

.pet-name-active {
  color: var(--color-primary);
}

.pet-name-add {
  color: var(--color-text-tertiary);
}

// 日历卡片
.calendar-card {
  margin: 16rpx 24rpx 0;
  padding: 28rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.cal-nav-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-divider);
}

.cal-nav-arrow {
  font-size: 32rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}

.cal-month-label {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

// 星期
.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 8rpx;
}

.cal-weekday {
  text-align: center;
  padding: 8rpx 0;
}

.cal-weekend .cal-weekday-text {
  color: var(--color-danger);
}

.cal-weekday-text {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

// 日期网格
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4rpx;
}

.cal-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  position: relative;
}

.cal-cell-other .cal-day-num {
  color: var(--color-text-tertiary);
}

.cal-cell-today {
  position: relative;
}

.cal-cell-today::after {
  content: '';
  position: absolute;
  bottom: 4rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: var(--color-primary);
}

.cal-cell-selected {
  background: var(--color-primary);
}

.cal-cell-selected .cal-day-num {
  color: #ffffff;
  font-weight: var(--font-weight-semibold);
}

.cal-day-num {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  line-height: 1;
}

.cal-day-num-selected {
  color: #ffffff;
}

// 圆点指示器
.cal-dots {
  display: flex;
  gap: 2rpx;
  margin-top: 2rpx;
  height: 8rpx;
  align-items: center;
}

.cal-dot {
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-bath {
  background: var(--color-primary);
}
.dot-vaccine {
  background: var(--color-success);
}
.dot-deworm {
  background: var(--color-warning);
}
.dot-checkup {
  background: var(--color-info);
}
.dot-diary {
  background: var(--color-warm);
}

// 图例
.cal-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--color-divider);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.legend-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}

.legend-text {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
}

// 详情卡片
.detail-card {
  margin: 16rpx 24rpx 0;
  padding: 28rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.detail-date {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.detail-today-tag {
  font-size: var(--font-size-xs);
  padding: 4rpx 12rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.detail-pet-name {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.detail-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 20rpx;
  background: var(--color-background);
  border-radius: var(--radius-md);
}

.detail-item-icon {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.detail-icon-deworm {
  background: rgba(230, 185, 122, 0.15);
}

.detail-icon-weight {
  background: var(--color-primary-light);
}

.detail-icon-bath {
  background: var(--color-primary-light);
}

.detail-icon-vaccine {
  background: rgba(171, 185, 173, 0.2);
}

.detail-icon-checkup {
  background: rgba(143, 168, 182, 0.2);
}

.detail-icon-diary {
  background: rgba(217, 180, 176, 0.2);
}

.detail-icon-emoji {
  font-size: 28rpx;
}

.detail-item-info {
  flex: 1;
  min-width: 0;
}

.detail-item-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 4rpx;
}

.detail-item-tag {
  padding: 4rpx 12rpx;
  border-radius: var(--radius-sm);
}

.detail-item-tag-text {
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
}

.tag-deworm {
  background: rgba(230, 185, 122, 0.2);
  color: var(--color-warning);
}

.tag-weight {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.tag-bath {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.tag-vaccine {
  background: rgba(171, 185, 173, 0.2);
  color: var(--color-success);
}

.tag-checkup {
  background: rgba(143, 168, 182, 0.2);
  color: var(--color-info);
}

.tag-diary {
  background: rgba(217, 180, 176, 0.2);
  color: var(--color-warm);
}

.detail-item-time {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
}

.detail-item-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  display: block;
}

.detail-empty {
  padding: 32rpx 0;
  text-align: center;
}

.detail-empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

// 提醒卡片
.reminder-card {
  margin: 16rpx 24rpx 0;
  padding: 28rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.reminder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.reminder-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.reminder-more {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.reminder-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.reminder-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  border-radius: var(--radius-md);
  background: var(--color-background);
}

.reminder-overdue {
  background: rgba(201, 110, 95, 0.08);
  border-left: 4rpx solid var(--color-danger);
}

.reminder-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.reminder-icon-bath {
  background: rgba(201, 110, 95, 0.12);
}

.reminder-icon-vaccine {
  background: rgba(171, 185, 173, 0.2);
}

.reminder-icon-deworm {
  background: rgba(230, 185, 122, 0.15);
}

.reminder-icon-checkup {
  background: rgba(143, 168, 182, 0.15);
}

.reminder-icon-emoji {
  font-size: 28rpx;
}

.reminder-info {
  flex: 1;
  min-width: 0;
}

.reminder-info-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4rpx;
}

.reminder-tag {
  padding: 4rpx 12rpx;
  border-radius: var(--radius-sm);
}

.reminder-tag-text {
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
}

.reminder-status {
  font-size: 18rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
}

.reminder-status-overdue {
  color: var(--color-danger);
}

.reminder-desc {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  display: block;
}

// 统计卡片
.stat-card {
  margin: 16rpx 24rpx 0;
  padding: 28rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.stat-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  display: block;
  margin-bottom: 20rpx;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 16rpx;
  border-radius: var(--radius-md);
}

.stat-number {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  line-height: 1;
}

.stat-label {
  font-size: 18rpx;
  font-weight: var(--font-weight-medium);
}

// 弹窗
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
}

.modal-content {
  width: 100%;
  max-height: 70vh;
  background: var(--color-surface);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.modal-cancel {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.modal-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.modal-submit {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.modal-body {
  padding: 32rpx;
}

.form-group {
  margin-bottom: 32rpx;
}

.form-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  display: block;
  margin-bottom: 16rpx;
}

.type-select-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.type-select-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border-radius: var(--radius-pill);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.type-select-active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.type-select-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
}

.type-select-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}

.form-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.form-picker-text {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.form-picker-arrow {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  font-size: var(--font-size-sm);
  color: var(--color-text);
  background: var(--color-surface);
  box-sizing: border-box;
}
</style>
