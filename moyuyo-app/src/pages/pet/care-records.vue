<template>
  <view class="care-records">
    <!-- 顶部导航（自定义，需叠加状态栏高度） -->
    <view class="header">
      <view class="header-inner">
        <view class="header-btn" :aria-label="$t('careRecords.back')" @click="onBack">
          <text class="luc luc-arrow-left" />
        </view>
        <text class="header-title">{{ petName }} · {{ $t('careRecords.title') }}</text>
        <view
          class="header-btn header-btn-primary"
          :aria-label="$t('careRecords.addRecord')"
          @click="openForm"
        >
          <text class="header-add">+</text>
        </view>
      </view>
    </view>

    <!-- 护理类型切换 -->
    <scroll-view scroll-x class="type-scroll" :show-scrollbar="false">
      <view class="type-tabs">
        <view
          v-for="t in typeMetas"
          :key="t.type"
          class="type-tab"
          :class="{ active: activeType === t.type }"
          @click="switchType(t.type)"
        >
          <text class="tab-label">{{ t.label }}</text>
          <text class="tab-count">{{ countOf(t.type) }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 当前类型护理摘要（上次 / 下次 / 周期） -->
    <view class="summary-card">
      <view class="summary-top">
        <text class="summary-title">{{ $t('careRecords.planTitle', { type: meta.label }) }}</text>
        <text class="summary-state" :class="summaryStateTone">{{ summaryStateText }}</text>
      </view>
      <view class="summary-grid">
        <view class="summary-cell">
          <text class="cell-value">
            {{
              summaryItem && summaryItem.lastRecordDate ? fmtDate(summaryItem.lastRecordDate) : '—'
            }}
          </text>
          <text class="cell-label">{{ $t('careRecords.lastCare', { type: meta.label }) }}</text>
        </view>
        <view class="summary-cell">
          <text class="cell-value">
            {{ summaryItem && summaryItem.nextDate ? fmtDate(summaryItem.nextDate) : '—' }}
          </text>
          <text class="cell-label">{{ $t('careRecords.nextCare', { type: meta.label }) }}</text>
        </view>
        <view class="summary-cell summary-cell-link" @click="openCycleForm">
          <text class="cell-value" :class="{ 'cell-value-muted': cycleUnset }">
            {{ cycleDaysText }}
          </text>
          <view class="cell-label-row">
            <text class="cell-label">{{ $t('careRecords.cycle') }}</text>
            <text class="cell-link-arrow luc luc-chevron-right" />
          </view>
        </view>
      </view>
    </view>

    <!-- 历史记录（时间线） -->
    <view class="list-block">
      <view class="list-title">{{ $t('careRecords.historyTitle') }}</view>
      <view v-if="curRecords.length > 0" class="timeline">
        <view v-for="(r, i) in curRecords" :key="r.id" class="tl-item">
          <view class="tl-track">
            <view class="tl-dot" :class="dotClass" />
            <view v-if="i < curRecords.length - 1" class="tl-line" />
          </view>
          <view class="tl-card">
            <view class="tl-head">
              <text class="tl-date">{{ fmtDate(r.recordDate) }}</text>
              <text
                class="tl-del luc luc-trash-2"
                :aria-label="$t('careRecords.delete')"
                @click="onDelete(r)"
              />
            </view>
            <text class="tl-content">
              {{ r.content || $t('careRecords.completedOnce', { type: meta.label }) }}
            </text>
          </view>
        </view>
      </view>
      <view v-else class="empty-box">
        <text class="luc empty-icon" :class="`luc-${meta.icon}`" />
        <text class="empty-text">{{ $t('careRecords.noRecordYet', { type: meta.label }) }}</text>
        <view class="empty-btn" @click="openForm">
          {{ $t('careRecords.recordFirst', { type: meta.label }) }}
        </view>
      </view>
    </view>

    <!-- 新增记录弹窗 -->
    <view v-if="showModal" class="modal-overlay" @click="closeForm">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-btn" @click="closeForm">{{ $t('careRecords.cancel') }}</text>
          <text class="modal-title">{{ $t('careRecords.recordTitle', { type: meta.label }) }}</text>
          <text class="modal-btn modal-submit" @click="submitForm">
            {{ $t('careRecords.save') }}
          </text>
        </view>
        <view class="modal-body">
          <view class="form-group">
            <text class="form-label">{{ $t('careRecords.type') }}</text>
            <view class="form-static">
              <text class="form-static-text">{{ meta.label }}</text>
            </view>
          </view>
          <view class="form-group">
            <text class="form-label">{{ $t('careRecords.date') }}</text>
            <picker mode="date" :value="form.date" @change="onDateChange">
              <view class="form-picker">
                <text class="form-picker-text">
                  {{ form.date || $t('careRecords.datePlaceholder') }}
                </text>
                <text class="form-picker-arrow luc luc-chevron-right" />
              </view>
            </picker>
          </view>
          <view class="form-group">
            <text class="form-label">{{ $t('careRecords.contentLabel') }}</text>
            <input v-model="form.content" class="form-input" :placeholder="meta.placeholder">
          </view>
          <text class="form-tip">{{ $t('careRecords.saveNextTip', { type: meta.label }) }}</text>
        </view>
      </view>
    </view>

    <!-- 设置护理周期弹窗 -->
    <view v-if="showCycleModal" class="modal-overlay" @click="closeCycleForm">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-btn" @click="closeCycleForm">{{ $t('careRecords.cancel') }}</text>
          <text class="modal-title">{{ $t('careRecords.cycleTitle') }}</text>
          <text class="modal-btn modal-submit" @click="saveCycleForm">
            {{ $t('careRecords.save') }}
          </text>
        </view>
        <view class="modal-body">
          <view class="cycle-tip">
            <text class="cycle-tip-text">
              {{ $t('careRecords.cycleTip', { type: meta.label }) }}
            </text>
          </view>
          <!-- 常用周期快捷选择 -->
          <view class="cycle-presets">
            <view
              v-for="days in cyclePresets"
              :key="days"
              class="cycle-chip"
              :class="{ 'cycle-chip-active': cycleDays === days }"
              @click="cycleDays = days"
            >
              <text class="cycle-chip-text">{{ days }} {{ $t('careRecords.dayUnit') }}</text>
            </view>
          </view>
          <!-- 自定义天数（步进 + 输入） -->
          <view class="cycle-stepper">
            <view class="cycle-step-btn" @click="stepCycle(-1)">
              <text class="cycle-step-symbol">−</text>
            </view>
            <view class="cycle-step-value-wrap">
              <input
                v-model.number="cycleDays"
                class="cycle-step-input"
                type="number"
                @blur="clampCycle"
              >
              <text class="cycle-step-unit">{{ $t('careRecords.dayUnit') }}</text>
            </view>
            <view class="cycle-step-btn" @click="stepCycle(1)">
              <text class="cycle-step-symbol">+</text>
            </view>
          </view>
          <view class="cycle-mini-tip">
            <text class="cycle-mini-text">
              {{ $t('careRecords.cycleSaveTip', { type: meta.label }) }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { usePetStore } from '@/store'

// 四种护理类型的图标映射（label/placeholder 文案由 i18n 字典 careRecords.* 提供，随 locale 切换）
const TYPE_META = {
  BATH: { icon: 'droplets' },
  VACCINE: { icon: 'syringe' },
  DEWORM: { icon: 'bug' },
  EXAM: { icon: 'stethoscope' },
}
const TYPE_ORDER = ['BATH', 'VACCINE', 'DEWORM', 'EXAM']

// 护理周期（天）可选范围
const CYCLE_MIN = 1
const CYCLE_MAX = 365
// 常用周期快捷项（天）
const CYCLE_PRESETS = [7, 15, 30, 60, 90, 180, 365]
// 各护理类型默认周期（天），与后端 DEFAULT_CYCLE_DAYS 对齐
const DEFAULT_CYCLE_DAYS = { BATH: 7, VACCINE: 365, DEWORM: 90, EXAM: 365 }

export default {
  // 原生 navbar 标题跟随 i18n（本页为自定义导航，仍声明以保持一致）
  pageTitleKey: 'pageTitle.petCareRecords',

  data() {
    return {
      petId: null,
      petName: '宝贝',
      activeType: 'BATH',
      showModal: false,
      submitting: false,
      showCycleModal: false,
      submittingCycle: false,
      cycleDays: 7,
      cyclePresets: CYCLE_PRESETS,
      form: {
        date: '',
        content: '',
      },
    }
  },

  computed: {
    petStore() {
      return usePetStore()
    },
    // 当前类型的展示信息（label / placeholder 走 i18n，切换语言即时生效）
    meta() {
      const type = TYPE_META[this.activeType] ? this.activeType : 'BATH'
      return {
        label: this.$t(`careRecords.types.${type}`),
        icon: TYPE_META[type].icon,
        placeholder: this.$t(`careRecords.placeholders.${type}`),
      }
    },
    typeMetas() {
      // 补上 type / label 字段，供模板中 :key / active 判断 / switchType / countOf 使用
      return TYPE_ORDER.map((type) => ({
        type,
        ...TYPE_META[type],
        label: this.$t(`careRecords.types.${type}`),
      }))
    },
    // 当前类型在 care-summary 中的聚合项
    summaryItem() {
      const list = this.petStore.careSummary || []
      return list.find((s) => s.careType === this.activeType) || null
    },
    // 护理周期展示文案（未配置提醒时提示可设置）
    cycleDaysText() {
      const s = this.summaryItem
      return s && s.intervalDays
        ? this.$t('careRecords.everyNDays', { days: s.intervalDays })
        : this.$t('careRecords.unset')
    },
    // 是否尚未配置护理周期（用于弱化占位文案）
    cycleUnset() {
      const s = this.summaryItem
      return !(s && s.intervalDays)
    },
    summaryStateText() {
      const s = this.summaryItem
      const state = (key, params) => this.$t(`careRecords.summaryState.${key}`, params)
      if (!s) return ''
      if (s.enabled === false) return state('reminderOff')
      if (!s.nextDate) return s.hasRecord ? state('setNextPending') : state('notStarted')
      if (s.overdue) return state('overdue')
      if (s.daysUntilNext === 0) return state('today')
      if (s.daysUntilNext <= (s.advanceDays || 3))
        return state('daysLeft', { days: s.daysUntilNext })
      return state('healthy')
    },
    summaryStateTone() {
      const s = this.summaryItem
      if (!s) return ''
      if (s.enabled === false || (!s.nextDate && !s.hasRecord)) return 'state-muted'
      if (s.overdue) return 'state-warn'
      if (s.nextDate && s.daysUntilNext <= (s.advanceDays || 3)) return 'state-warn'
      return 'state-ok'
    },
    dotClass() {
      return `dot-${this.activeType.toLowerCase()}`
    },
    // 当前类型历史记录（后端已按日期倒序）
    curRecords() {
      const list = this.petStore.growthRecords || []
      return list.filter((r) => r.recordType === this.activeType)
    },
  },

  onLoad(query) {
    this.petId = query.petId || null
    const type = (query.type || '').toUpperCase()
    if (TYPE_META[type]) this.activeType = type
  },

  onShow() {
    this.init()
  },

  methods: {
    onBack() {
      uni.navigateBack()
    },

    // 初始化宠物名并拉取记录 + 聚合摘要
    async init() {
      if (!this.petId) return
      try {
        await this.petStore.loadPets()
      } catch (e) {
        // 宠物列表失败不影响记录读取
      }
      const matched = (this.petStore.pets || []).find((p) => p.id === Number(this.petId))
      if (matched?.name) this.petName = matched.name
      await this.refresh()
    },

    // 刷新成长记录与 care-summary（新增后联动更新）
    async refresh() {
      if (!this.petId) return
      await Promise.all([
        this.petStore.loadGrowthRecords(this.petId),
        this.petStore.loadCareSummary(this.petId),
      ])
    },

    switchType(type) {
      this.activeType = type
    },

    countOf(type) {
      const list = this.petStore.growthRecords || []
      return list.filter((r) => r.recordType === type).length
    },

    openForm() {
      if (!this.petId) return
      this.form = { date: this.todayStr(), content: '' }
      this.showModal = true
    },

    closeForm() {
      if (this.submitting) return
      this.showModal = false
    },

    async submitForm() {
      if (!this.form.date) {
        uni.showToast({ title: this.$t('careRecords.datePlaceholder'), icon: 'none' })
        return
      }
      if (this.submitting) return
      this.submitting = true
      try {
        const payload = {
          recordType: this.activeType,
          recordDate: this.form.date,
          content: (this.form.content || '').trim(),
        }
        await this.petStore.createGrowthRecord(this.petId, payload)
        uni.showToast({ title: this.$t('careRecords.saved'), icon: 'success' })
        this.showModal = false
        await this.refresh()
      } catch (e) {
        console.warn('[care-records] 保存失败', e)
        uni.showToast({ title: this.$t('careRecords.saveFailed'), icon: 'none' })
      } finally {
        this.submitting = false
      }
    },

    onDateChange(e) {
      this.form.date = e.detail.value
    },

    // 删除单条记录（二次确认）；删除后该类型提醒会按剩余记录回算
    onDelete(record) {
      if (!record || !record.id) return
      uni.showModal({
        title: this.$t('careRecords.deleteModalTitle'),
        content: this.$t('careRecords.deleteModalContent', { type: this.meta.label }),
        confirmText: this.$t('careRecords.delete'),
        confirmColor: '#e03a3a',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await this.petStore.deleteGrowthRecord(this.petId, record.id)
            uni.showToast({ title: this.$t('careRecords.deleted'), icon: 'success' })
            await this.refresh()
          } catch (e) {
            console.warn('[care-records] 删除失败', e)
            uni.showToast({ title: this.$t('careRecords.deleteFailed'), icon: 'none' })
          }
        },
      })
    },

    // 日期（YYYY-MM-DD）转本地化展示：中文「M月D日」/ 英文「Mon D」
    fmtDate(dateStr) {
      if (!dateStr) return ''
      const [y, m, d] = String(dateStr).split('-').map(Number)
      if (!y || !m || !d) return dateStr
      const isEn = this.$i18n && this.$i18n.locale === 'en-US'
      if (!isEn) return `${m}月${d}日`
      const MONTHS_EN = [
        'Jan',
        'Feb',
        'Mar',
        'Apr',
        'May',
        'Jun',
        'Jul',
        'Aug',
        'Sep',
        'Oct',
        'Nov',
        'Dec',
      ]
      return `${MONTHS_EN[m - 1]} ${d}`
    },

    todayStr() {
      const d = new Date()
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    },

    // ===== 护理周期设置 =====

    // 打开周期设置弹窗：有配置取现值，否则取该类型默认周期
    openCycleForm() {
      if (!this.petId) return
      const s = this.summaryItem
      this.cycleDays = (s && s.intervalDays) || DEFAULT_CYCLE_DAYS[this.activeType] || 7
      this.showCycleModal = true
    },

    closeCycleForm() {
      if (this.submittingCycle) return
      this.showCycleModal = false
    },

    // 步进按钮：在允许范围内 ±1 天
    stepCycle(delta) {
      const next = Number(this.cycleDays || 0) + delta
      if (next >= CYCLE_MIN && next <= CYCLE_MAX) this.cycleDays = next
    },

    // 输入失焦后把天数收敛到合法范围
    clampCycle() {
      let v = Math.round(Number(this.cycleDays))
      if (!Number.isFinite(v) || v < CYCLE_MIN) v = CYCLE_MIN
      if (v > CYCLE_MAX) v = CYCLE_MAX
      this.cycleDays = v
    },

    // 保存周期：按 petId+reminderType upsert；已有最近护理日期时联动重排下次提醒
    async saveCycleForm() {
      const days = Math.round(Number(this.cycleDays))
      if (!Number.isInteger(days) || days < CYCLE_MIN || days > CYCLE_MAX) {
        uni.showToast({
          title: this.$t('careRecords.rangeToast', { min: CYCLE_MIN, max: CYCLE_MAX }),
          icon: 'none',
        })
        return
      }
      if (this.submittingCycle) return
      this.submittingCycle = true
      try {
        const s = this.summaryItem
        // 计算周期锚点：最近一次实际护理优先；没有记录时用「下次日期 - 原周期」反推锚点
        // （首次设周期后端生成的 nextDate = 当天 + 周期，反推回当天，保证再次改周期下次随之更新）
        let anchor = ''
        if (s) {
          if (s.lastNotifiedDate) anchor = s.lastNotifiedDate
          else if (s.lastRecordDate) anchor = s.lastRecordDate
          else if (s.nextDate && s.intervalDays) anchor = this.addDays(s.nextDate, -s.intervalDays)
        }
        if (!anchor) anchor = this.todayStr()
        const payload = {
          reminderType: this.activeType,
          intervalDays: days,
          // 无论是否已有记录都重排下次 = 锚点 + 新周期
          nextDate: this.addDays(anchor, days),
        }
        await this.petStore.updateReminder(this.petId, 0, payload)
        uni.showToast({ title: this.$t('careRecords.cycleUpdated'), icon: 'success' })
        this.showCycleModal = false
        await this.refresh()
      } catch (e) {
        console.warn('[care-records] 更新护理周期失败', e)
        uni.showToast({ title: this.$t('careRecords.saveFailed'), icon: 'none' })
      } finally {
        this.submittingCycle = false
      }
    },

    // 'YYYY-MM-DD' 增加 N 天，返回同格式字符串（本地时区计算避免 UTC 偏移）
    addDays(dateStr, days) {
      const [y, m, d] = String(dateStr).split('-').map(Number)
      if (!y || !m || !d) return ''
      const date = new Date(y, m - 1, d)
      date.setDate(date.getDate() + days)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    },
  },
}
</script>

<style lang="scss" scoped>
.care-records {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
}

/* ===== 顶部导航 ===== */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  padding-top: calc(var(--status-bar-height, 0px) + env(safe-area-inset-top, 0px));
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
  background: var(--color-background);
}

.header-btn-primary {
  background: var(--color-primary);
}

.header-add {
  font-size: 44rpx;
  font-weight: var(--font-weight-bold);
  color: #ffffff;
  line-height: 1;
}

/* ===== 类型切换（横向滑动胶囊 / 分段控件质感） ===== */
.type-scroll {
  width: 100%;
  white-space: nowrap; /* 禁止换行，配合 scroll-view scroll-x 左右横滑 */
  background: var(--color-surface);
}

.type-tabs {
  display: inline-flex; /* 宽度跟随内容，超宽时交给 scroll-view 横滑 */
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 24rpx 20rpx;
}

/* 单个胶囊：白底 + 细分隔线 + 轻投影，与摘要卡同为 surface 卡片语言 */
.type-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 10rpx 22rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.15s ease;
}

/* 按压反馈：轻缩放模拟胶囊回弹 */
.type-tab:active {
  transform: scale(0.96);
}

/* 激活胶囊：品牌沙金渐变（primary → primary-dark），白字成为当前焦点 */
.type-tab.active {
  border-color: var(--color-primary-dark);
  background-image: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  box-shadow: var(--shadow-md);
}

.tab-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
  transition: color 0.2s ease;
}

.type-tab.active .tab-label {
  color: #ffffff;
  /* 沙金渐变偏浅，加一道柔和文字投影保证白字清晰可读 */
  text-shadow: 0 1rpx 0 rgba(120, 102, 60, 0.18);
}

/* 记录次数：迷你药丸徽标 */
.tab-count {
  box-sizing: border-box;
  display: inline-block;
  min-width: 36rpx;
  height: 34rpx;
  line-height: 34rpx;
  padding: 0 10rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary-light); /* 非激活：柔和淡金底 */
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
  text-align: center;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

/* 激活态徽标反白：白底 + 品牌深沙金数字，与白字 label 形成对比层次 */
.type-tab.active .tab-count {
  background: #ffffff;
  color: var(--color-primary-dark);
  box-shadow: 0 2rpx 6rpx rgba(90, 74, 38, 0.18);
}

/* ===== 摘要卡 ===== */
.summary-card {
  margin: 24rpx 24rpx 0;
  padding: 28rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.summary-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.summary-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.summary-state {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.state-ok {
  color: var(--color-success);
}

.state-warn {
  color: var(--color-danger);
}

.state-muted {
  color: var(--color-text-tertiary);
}

.summary-grid {
  display: flex;
  align-items: flex-start;
}

.summary-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.cell-value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.cell-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* 护理周期格：可点击设置 */
.summary-cell-link {
  cursor: pointer;
}

.summary-cell-link:active {
  opacity: 0.6;
}

/* 未配置周期时的占位弱化 */
.cell-value-muted {
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

/* 标签行（文字 + 引导箭头） */
.cell-label-row {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.cell-link-arrow {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

/* ===== 历史时间线 ===== */
.list-block {
  margin-top: 32rpx;
  padding: 0 24rpx;
}

.list-title {
  margin-bottom: 20rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.timeline {
  display: flex;
  flex-direction: column;
}

.tl-item {
  display: flex;
}

.tl-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 40rpx;
  margin-right: 20rpx;
}

.tl-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  margin-top: 24rpx;
  flex-shrink: 0;
}

.tl-line {
  flex: 1;
  width: 2rpx;
  background: var(--color-divider);
}

/* 各类型时间线圆点颜色 */
.dot-bath {
  background: #4aa8f0;
}

.dot-vaccine {
  background: #34c759;
}

.dot-deworm {
  background: #a060ff;
}

.dot-exam {
  background: #f5a623;
}

.tl-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  margin-bottom: 20rpx;
}

.tl-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.tl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tl-del {
  /* 删除图标：放大字号便于点按，颜色沿用警示红 */
  padding: 8rpx;
  font-size: 36rpx;
  color: var(--color-danger);
}

.tl-content {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

/* 空状态 */
.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 96rpx 0 40rpx;
}

.empty-icon {
  font-size: 64rpx;
  color: var(--color-text-tertiary);
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.empty-btn {
  margin-top: 12rpx;
  padding: 16rpx 40rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}

/* ===== 新增弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}

.modal-content {
  width: 100%;
  background: var(--color-surface);
  border-radius: 32rpx 32rpx 0 0;
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100rpx;
  padding: 0 32rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.modal-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.modal-btn {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.modal-submit {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.modal-body {
  padding: 8rpx 32rpx 32rpx;
}

.form-group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 96rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.form-label {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.form-static {
  flex: 1;
  display: flex;
  justify-content: flex-end;
}

.form-static-text,
.form-picker-text,
.form-input {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.form-picker {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
}

.form-picker-arrow {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}

.form-input {
  flex: 1;
  text-align: right;
}

.form-tip {
  display: block;
  margin-top: 20rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.6;
}

/* ===== 设置护理周期弹窗 ===== */
.cycle-tip {
  padding: 8rpx 0 24rpx;
}

.cycle-tip-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

/* 常用周期快捷胶囊 */
.cycle-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.cycle-chip {
  padding: 14rpx 28rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
  background: var(--color-background);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.cycle-chip:active {
  transform: scale(0.96);
}

.cycle-chip-active {
  border-color: var(--color-primary-dark);
  background-image: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
}

.cycle-chip-text {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.cycle-chip-active .cycle-chip-text {
  color: #ffffff;
}

/* 自定义步进器 */
.cycle-stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40rpx;
  margin-top: 40rpx;
}

.cycle-step-btn {
  width: 84rpx;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid var(--color-divider);
  border-radius: 50%;
  background: var(--color-background);
}

.cycle-step-btn:active {
  opacity: 0.6;
}

.cycle-step-symbol {
  font-size: 48rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
  line-height: 1;
}

.cycle-step-value-wrap {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8rpx;
  min-width: 200rpx;
}

.cycle-step-input {
  width: 120rpx;
  font-size: 52rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  text-align: center;
}

.cycle-step-unit {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.cycle-mini-tip {
  margin-top: 28rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--color-divider);
}

.cycle-mini-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.6;
}
</style>
