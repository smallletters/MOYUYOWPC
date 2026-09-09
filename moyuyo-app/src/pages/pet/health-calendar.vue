<template>
  <view class="health-calendar">
    <!-- 宠物切换 -->
    <view class="pet-scroll">
      <view class="pet-scroll-inner">
        <view
          v-for="pet in petList"
          :key="pet.id"
          class="pet-item"
          @click="onSwitchPet(pet)">
          <view class="pet-avatar" :class="{ 'pet-avatar-active': pet.active }">
            <!-- 后端 PetVO 只有 avatar 图片，无 emoji 字段：有图用图，无图按类型给表情兜底 -->
            <image
              v-if="pet.avatar"
              class="pet-avatar-img"
              :src="pet.avatar"
              mode="aspectFill" />
            <text v-else class="pet-avatar-emoji">{{ petEmoji(pet) }}</text>
          </view>
          <text class="pet-name-label" :class="{ 'pet-name-active': pet.active }">
            {{ pet.name }}
          </text>
        </view>
      </view>
      <!-- 添加提醒入口：置于宠物列表右侧 -->
      <view class="pet-add-btn" @click="onAddReminder">
        <text class="pet-add-icon">+</text>
      </view>
    </view>

    <!-- 日历卡片 -->
    <view class="calendar-card">
      <!-- 月份切换 -->
      <view class="calendar-header">
        <view class="cal-nav-btn" @click="prevMonth">
          <text class="cal-nav-arrow luc-arrow-left" />
        </view>
        <text class="cal-month-label">{{ currentYear }}年{{ currentMonth + 1 }}月</text>
        <view class="cal-nav-btn" @click="nextMonth">
          <text class="cal-nav-arrow luc-chevron-right" />
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
            <text class="detail-icon-emoji luc" :class="$luc(evt.icon)" />
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
            <text class="reminder-icon-emoji luc" :class="$luc(rem.icon)" />
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
                <text class="form-picker-arrow luc-chevron-right" />
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
</template>
<script>
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

// 后端 reminderType（大写）→ 页面展示 key（小写，匹配 CSS 类/图例）
// 注意：护理/体检体系后端统一用 EXAM，页面视觉沿用 checkup，避免同义提醒重复出现
const BACKEND_TYPE_KEY = {
  BATH: 'bath',
  VACCINE: 'vaccine',
  DEWORM: 'deworm',
  EXAM: 'checkup',
  CHECKUP: 'checkup', // 兼容历史遗留的 checkup 存储
  DIARY: 'diary',
}

// 页面展示 key → 后端提醒类型（大写，提交用）
const TYPE_KEY_BACKEND = {
  bath: 'BATH',
  vaccine: 'VACCINE',
  deworm: 'DEWORM',
  checkup: 'EXAM',
  diary: 'DIARY',
}

export default {
  pageTitleKey: 'pageTitle.petHealthCalendar',

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
        // 后端类型大写（EXAM 等）统一映射到页面展示 key，日历 key 用数字拼接去掉前导零
        const t = this.displayType(r.reminderType || r.type || '')
        const key = this.dateKey(r.nextDate)
        if (!map[key]) map[key] = []
        map[key].push(t)
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
        const type = this.displayType(r.reminderType || r.type || 'bath')
        const legend = LEGEND_ITEMS.find((l) => l.type === type)
        // 用「日期字符串→本地当天零点」计算天数差，避免 UTC 解析导致的日期偏移
        const nextMid = r.nextDate ? this.localMidnight(r.nextDate) : null
        const todayMid = new Date(
          this.today.getFullYear(),
          this.today.getMonth(),
          this.today.getDate(),
        )
        const diff = nextMid
          ? Math.round((nextMid.getTime() - todayMid.getTime()) / (1000 * 60 * 60 * 24))
          : 0
        return {
          type,
          icon: TYPE_ICONS[type] || 'map-pin',
          typeLabel: legend ? legend.label : type,
          date: r.nextDate || '-',
          desc: this.extraNote(r) || (legend ? `${legend.label}计划` : ''),
          statusText:
            diff < 0 ? `已过期 ${Math.abs(diff)} 天` : diff === 0 ? '今天' : `还剩 ${diff} 天`,
          overdue: diff < 0,
        }
      })
    },

    monthlyStats() {
      const counts = {}
      for (const r of this.reminders) {
        // 只统计当前查看月且启用的提醒，避免跨月/已关闭类型混入概览
        if (!r.nextDate || r.enabled === false) continue
        const [y, m] = String(r.nextDate).split('-').map(Number)
        if (y !== this.currentYear || m !== this.currentMonth + 1) continue
        const type = this.displayType(r.reminderType || r.type || '')
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
    async loadData() {
      try {
        const pets = await petApi.getPets()
        this.petList = Array.isArray(pets) ? pets : []
        // 未传 petId（如无宠物时入口不传参）时兜底第一只宠物再拉提醒
        if (!this.petId && this.petList.length > 0) {
          this.petId = this.petList[0].id
        }
        if (this.petId) {
          try {
            this.reminders = (await petApi.getReminders(this.petId)) || []
          } catch (e) {
            console.warn('[health-calendar] reminders load failed', e)
            this.reminders = []
          }
        } else {
          this.reminders = []
        }
        // 高亮与名字用数字比较，避免 petId 字符串与 p.id 数字失配
        const activePet =
          this.petList.find((p) => p.id === Number(this.petId)) || this.petList[0] || null
        this.petList.forEach((p) => {
          p.active = p.id === Number(this.petId)
        })
        this.currentPetName = activePet ? activePet.name : ''
        // 首次进入/切换宠物后同步刷新选中日详情，避免残留上一只宠物的旧事件
        this.updateSelectedEvents()
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
      // 直接按选中日期过滤提醒，可带出备注/类型文案
      this.selectedEvents = this.reminders
        .filter((r) => r.nextDate && this.dateKey(r.nextDate) === fullDate)
        .map((r) => {
          const type = this.displayType(r.reminderType || r.type || '')
          const legend = LEGEND_ITEMS.find((l) => l.type === type)
          const note = this.extraNote(r)
          return {
            type,
            icon: TYPE_ICONS[type] || 'calendar',
            typeLabel: legend ? legend.label : type,
            time: '全天',
            desc: note || `${legend ? legend.label : type}提醒`,
          }
        })
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
      // 页面 key → 后端大写类型；体检用 EXAM，避免与护理记录体系产生 checkup 重复提醒
      const backendType = TYPE_KEY_BACKEND[this.reminderForm.type]
      if (!backendType) {
        uni.showToast({ title: '请选择有效的提醒类型', icon: 'none' })
        return
      }
      if (!this.petId) {
        uni.showToast({ title: '请先选择宠物', icon: 'none' })
        return
      }
      try {
        // reminderId 传 0：后端按 petId+reminderType upsert，可新增/覆盖该类型提醒；
        // 后端无 note 字段，备注统一放 extra(JSON 列) 并写为 { note }，读取时解析
        await petApi.updateReminder(this.petId, 0, {
          reminderType: backendType,
          nextDate: this.reminderForm.date,
          extra: this.reminderForm.note
            ? JSON.stringify({ note: this.reminderForm.note.trim() })
            : null,
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

    // 后端 reminderType（大写）→ 页面展示 key；体检 EXAM 映射到 checkup 视觉
    displayType(raw) {
      const upper = String(raw || '').toUpperCase()
      return BACKEND_TYPE_KEY[upper] || upper.toLowerCase()
    },

    // 从 extra(JSON) 中解析备注；兼容纯文本与非法 JSON
    extraNote(r) {
      const ex = r && r.extra
      if (ex === null || ex === undefined) return ''
      if (typeof ex === 'string') {
        try {
          const o = JSON.parse(ex)
          if (o && typeof o.note === 'string') return o.note
          if (typeof o === 'string') return o
        } catch (e) {
          /* 非法 JSON 按原样展示 */
        }
      }
      return typeof ex === 'string' ? ex : ''
    },

    // '2026-09-07' → '2026-9-7'（与日历 fullDate 的拼接格式一致）
    dateKey(dateStr) {
      const [y, m, d] = String(dateStr || '')
        .split('-')
        .map(Number)
      if (!y || !m || !d) return ''
      return `${y}-${m}-${d}`
    },

    // 按本地时区解析 'YYYY-MM-DD' 为当天零点，避免 UTC 偏移
    localMidnight(dateStr) {
      const [y, m, d] = String(dateStr || '')
        .split('-')
        .map(Number)
      return new Date(y, m - 1, d)
    },

    // 宠物头像占位（后端 PetVO 无 emoji 字段，按类型给默认表情）
    petEmoji(pet) {
      const map = { DOG: '🐶', CAT: '🐱', RABBIT: '🐰', BIRD: '🐦', OTHER: '🐾' }
      return (pet && map[pet.type]) || '🐾'
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

// 宠物横向滚动
.pet-scroll {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 16rpx 24rpx 8rpx;
}

// 宠物列表自身横向滚动，占满剩余空间，把右侧「+」按钮推到最右
.pet-scroll-inner {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 24rpx;
  overflow-x: auto;
}

.pet-add-btn {
  flex-shrink: 0;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-primary);
}

.pet-add-icon {
  font-size: 40rpx;
  color: #ffffff;
  font-weight: var(--font-weight-bold);
  line-height: 1;
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

.pet-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.pet-avatar-emoji {
  font-size: 36rpx;
}

.pet-name-label {
  font-size: 20rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-tertiary);
}

.pet-name-active {
  color: var(--color-primary);
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
