<template>
  <view class="weight-chart">
    <!-- 顶部导航 -->
    <view class="header">
      <view class="header-inner">
        <view class="header-btn" @click="onBack">
          <text class="header-btn-icon luc luc-arrow-left" />
        </view>
        <text class="header-title">体重记录</text>
        <view class="header-btn header-btn-primary" @click="onAddWeight">
          <text class="header-btn-icon header-btn-icon-white">+</text>
        </view>
      </view>
    </view>

    <!-- 当前体重卡片 -->
    <view class="current-weight-card">
      <text class="current-weight-label">当前体重</text>
      <view class="current-weight-main">
        <template v-if="weightRecords.length">
          <text class="current-weight-number">{{ latestWeight }}</text>
          <text class="current-weight-unit">kg</text>
          <view
            v-if="latestTrend !== null"
            class="current-weight-trend"
            :class="latestTrend >= 0 ? 'current-weight-trend-up' : 'current-weight-trend-down'"
          >
            <text
              class="trend-icon luc"
              :class="$luc(latestTrend >= 0 ? 'arrow-up' : 'arrow-down')"
            />
            <text class="trend-value">{{ latestTrendText }}</text>
          </view>
        </template>
        <template v-else>
          <text class="current-weight-number">--</text>
          <text class="current-weight-unit">kg</text>
        </template>
      </view>
      <view class="current-weight-meta">
        <template v-if="latestDate">
          <text class="meta-item">较上次</text>
          <text class="meta-divider">|</text>
          <text class="meta-item">{{ latestDate }}</text>
        </template>
        <text v-else class="meta-item">暂无记录，点右上角「+」开始记录</text>
      </view>
    </view>

    <!-- 周/月切换 -->
    <view class="chart-toggle">
      <view
        class="toggle-item"
        :class="{ 'toggle-active': chartMode === 'weekly' }"
        @click="onToggleMode('weekly')"
      >
        <text class="toggle-text">周</text>
      </view>
      <view
        class="toggle-item"
        :class="{ 'toggle-active': chartMode === 'monthly' }"
        @click="onToggleMode('monthly')"
      >
        <text class="toggle-text">月</text>
      </view>
    </view>

    <!-- 图表区域（div 模拟） -->
    <view class="chart-card">
      <view class="chart-header">
        <text class="chart-title">{{ chartMode === 'weekly' ? '本周' : '本月' }}体重变化</text>
      </view>
      <!-- 柱状图：Y 轴刻度与柱高同源，随数据动态计算 -->
      <view v-if="chartData.length" class="chart-container">
        <!-- Y 轴刻度 -->
        <view class="chart-y-axis">
          <view
            v-for="t in chartRange.ticks"
            :key="t"
            class="y-tick"
            :style="{ bottom: tickBottom(t) }"
          >
            <text class="y-label">{{ fmtTick(t) }}</text>
          </view>
        </view>
        <!-- 图表主体 -->
        <view class="chart-body">
          <!-- 网格线（与刻度位置一致） -->
          <view class="chart-grid">
            <view
              v-for="t in chartRange.ticks"
              :key="'g' + t"
              class="grid-line"
              :style="{ bottom: barOffset(t) }"
            />
          </view>
          <!-- 柱子 -->
          <view class="chart-bars">
            <view v-for="(bar, bIdx) in chartData" :key="bIdx" class="chart-bar-col">
              <view
                class="bar-fill"
                :class="{
                  'bar-above': bar.value >= chartBaseline,
                  'bar-below': bar.value < chartBaseline,
                }"
                :style="{ height: barOffset(bar.value) }"
              />
              <view class="bar-label-col">
                <text class="bar-value-label">{{ bar.value }}</text>
                <text class="bar-date-label">{{ bar.label }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view v-else class="chart-empty">
        <text>该时间段暂无记录</text>
      </view>
    </view>

    <!-- 记录列表 -->
    <view class="record-section">
      <text class="record-section-title">历史记录</text>

      <view v-for="(record, rIdx) in weightRecords" :key="rIdx" class="record-item">
        <view class="record-left">
          <view
            class="record-icon-wrap"
            :class="record.trend === 'up' ? 'record-icon-up' : 'record-icon-down'"
          >
            <text
              class="record-icon luc"
              :class="$luc(record.trend === 'up' ? 'arrow-up' : 'arrow-down')"
            />
          </view>
          <view class="record-info">
            <text class="record-weight">{{ record.weight }} kg</text>
            <text class="record-date">{{ record.date }}</text>
          </view>
        </view>
        <view class="record-right">
          <text class="record-diff" :class="record.trend === 'up' ? 'diff-up' : 'diff-down'">
            {{ record.diffText }}
          </text>
          <text class="record-note">{{ record.note }}</text>
        </view>
      </view>
    </view>

    <!-- 添加体重弹窗 -->
    <view v-if="showWeightModal" class="modal-overlay" @click="onCloseWeightModal">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-cancel" @click="onCloseWeightModal">取消</text>
          <text class="modal-title">记录体重</text>
          <text class="modal-submit" @click="onSubmitWeight">保存</text>
        </view>
        <view class="modal-body">
          <view class="form-group">
            <text class="form-label">体重（kg）</text>
            <view class="weight-input-wrap">
              <input
                v-model="weightForm.value"
                class="weight-input"
                type="digit"
                placeholder="请输入体重"
              >
              <text class="weight-input-unit">kg</text>
            </view>
          </view>
          <view class="form-group">
            <text class="form-label">日期</text>
            <picker mode="date" :value="weightForm.date" @change="onWeightDateChange">
              <view class="form-picker">
                <text class="form-picker-text">{{ weightForm.date || '请选择日期' }}</text>
                <text class="form-picker-arrow luc-chevron-right" />
              </view>
            </picker>
          </view>
          <view class="form-group">
            <text class="form-label">备注</text>
            <input v-model="weightForm.note" class="form-input" placeholder="如：洗澡后测量">
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { petWeightApi } from '@/api'
import { usePetStore } from '@/store'

// 绘图区高度 / 底部柱标签区高度（rpx，须与 <style> 中 .chart-body 的取值一致）
const CHART_PLOT_H = 260
const CHART_LABEL_H = 60

export default {
  pageTitleKey: 'pageTitle.petWeightChart',

  data() {
    return {
      petId: null,
      chartMode: 'weekly',
      showWeightModal: false,
      weightForm: {
        value: '',
        date: '',
        note: '',
      },
      // 后端 /chart 返回的原始实体（时间升序）
      rawChart: [],
      // 柱状图展示数据 [{ value, label }]
      chartData: [],
      // 历史列表展示数据（最新在前，已含 diff/趋势/日期文案）
      weightRecords: [],
      // 当前体重卡片展示数据
      latestWeight: '--',
      latestDate: '',
      latestTrend: null,
      latestTrendText: '',
      // 柱状图颜色基准（取最新体重）
      chartBaseline: null,
    }
  },

  computed: {
    petStore() {
      return usePetStore()
    },

    // 图表 Y 轴范围：数据 min~max 外扩后取“好看”步长，得到 lo/hi/ticks，
    // 刻度标签与柱高共用同一范围，保证柱顶读数与刻度一致
    chartRange() {
      const vals = (this.chartData || []).map((d) => d.value)
      let lo = 0
      let hi = 1
      if (vals.length) {
        let min = Math.min(...vals)
        let max = Math.max(...vals)
        // 上下外扩，避免柱子贴死顶/底、首尾刻度缺失
        const pad = Math.max((max - min) * 0.15, 0.5)
        min -= pad
        max += pad
        // 4 等分下选“好看”步长：1 / 2 / 2.5 / 5 × 10^n
        const raw = (max - min) / 4
        const base = Math.pow(10, Math.floor(Math.log10(raw)))
        const niceSteps = [1, 2, 2.5, 5, 10]
        let step = base * 10
        for (let i = 0; i < niceSteps.length; i += 1) {
          const s = base * niceSteps[i]
          if (s >= raw) {
            step = s
            break
          }
        }
        lo = Math.floor(min / step) * step
        hi = Math.ceil(max / step) * step
        // 在 lo/hi 之间按 step 生成刻度（至少 4 段）
        step = (hi - lo) / Math.max(4, Math.round((hi - lo) / step))
        const ticks = []
        for (let v = lo; v <= hi + 1e-9; v += step) {
          ticks.push(Math.round(v * 100) / 100)
        }
        return { lo, hi, step, ticks }
      }
      return { lo, hi, step: 1, ticks: [0, 1] }
    },
  },

  onLoad(query) {
    // petId 为雪花 ID（约 2e18），超出 JS 安全整数范围(2^53)，
    // 必须保持字符串传递，不能用 Number() 强转，否则精度丢失会请求到错误宠物
    const raw = query.petId === undefined || query.petId === null ? '' : String(query.petId)
    this.petId = raw || null
    // 未带 petId（无宠物时快捷入口不传参）时兜底当前宠物
    if (!this.petId) {
      const pet = this.petStore.activePet || this.petStore.pets[0] || null
      if (pet && pet.id) this.petId = String(pet.id)
    }
    this.loadData()
  },

  methods: {
    onBack() {
      uni.navigateBack()
    },

    // 后端原始实体转展示数据；记录接口按时间降序（最新在前）
    decorateRecords(descRecords) {
      const chrono = descRecords.slice().reverse()
      this.weightRecords = chrono
        .map((w, i) => {
          const prev = chrono[i - 1]
          const weight = this.toWeightText(w.weight)
          let diff = null
          if (prev) diff = Number(w.weight) - Number(prev.weight)
          return {
            weight,
            date: this.formatDate(w.measuredAt),
            note: w.note || '',
            // 涨/跌相对更早一条记录；首条记录无对比对象
            trend: diff === null || diff < 0 ? 'down' : 'up',
            diffText:
              diff === null ? '首次' : `${diff >= 0 ? '+' : '-'}${Math.abs(diff).toFixed(1)}`,
          }
        })
        .reverse()

      // 当前体重卡片：最新一条 + 与上一条的差值
      const latest = descRecords[0]
      if (latest) {
        const prev = descRecords[1]
        const diff = prev ? Number(latest.weight) - Number(prev.weight) : null
        this.latestWeight = this.toWeightText(latest.weight)
        this.latestDate = this.formatDate(latest.measuredAt)
        this.latestTrend = diff
        this.latestTrendText =
          diff === null ? '' : `${diff >= 0 ? '+' : '-'}${Math.abs(diff).toFixed(1)}`
        this.chartBaseline = Number(latest.weight)
      } else {
        this.latestWeight = '--'
        this.latestDate = ''
        this.latestTrend = null
        this.latestTrendText = ''
        this.chartBaseline = null
      }
    },

    async loadData() {
      if (!this.petId) return
      try {
        const [chart, records] = await Promise.all([
          petWeightApi.getPetWeightChart(this.petId),
          petWeightApi.getPetWeights(this.petId),
        ])
        // chart 为时间升序的原始实体（旧→新）
        this.rawChart = Array.isArray(chart) ? chart : []
        this.decorateRecords(Array.isArray(records) ? records : [])
        this.applyChartMode()
      } catch (e) {
        console.warn('[weight] load failed', e)
        this.rawChart = []
        this.chartData = []
        this.weightRecords = []
      }
    },

    // 周/月切换：仅保留最近 7 / 30 天的点，再映射为柱状图数据
    onToggleMode(mode) {
      this.chartMode = mode
      this.applyChartMode()
    },

    applyChartMode() {
      const days = this.chartMode === 'monthly' ? 29 : 6
      const from = new Date()
      from.setDate(from.getDate() - days)
      const pad = (n) => String(n).padStart(2, '0')
      const fromStr = `${from.getFullYear()}-${pad(from.getMonth() + 1)}-${pad(from.getDate())}`
      this.chartData = (this.rawChart || [])
        .filter((e) => (e.measuredAt || '').slice(0, 10) >= fromStr)
        .map((e) => ({
          value: Number(e.weight),
          label: (e.measuredAt || '').slice(5, 10),
        }))
    },

    // 柱顶 / 网格线相对绘图区底部的偏移高度（rpx），刻度与柱体共用同一映射
    barOffset(value) {
      const { lo, hi } = this.chartRange
      if (!(hi > lo)) return '0rpx'
      const pct = Math.max(0, Math.min(1, (value - lo) / (hi - lo)))
      return `${(pct * CHART_PLOT_H).toFixed(1)}rpx`
    },

    // Y 轴刻度文本定位：底部柱标签区之上，再按刻度偏移
    tickBottom(value) {
      return `calc(${CHART_LABEL_H}rpx + ${this.barOffset(value)})`
    },

    // 刻度文本格式化：去掉浮点误差，整数不显示小数点
    fmtTick(v) {
      return String(Math.round(v * 100) / 100)
    },

    onAddWeight() {
      // 日期默认今天，减少一次选择
      const now = new Date()
      const pad = (n) => String(n).padStart(2, '0')
      const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
      this.weightForm = { value: '', date: today, note: '' }
      this.showWeightModal = true
    },

    onCloseWeightModal() {
      this.showWeightModal = false
    },

    async onSubmitWeight() {
      const value = Number(this.weightForm.value)
      if (!value || value <= 0) {
        uni.showToast({ title: '请输入正确的体重', icon: 'none' })
        return
      }
      if (!this.weightForm.date) {
        uni.showToast({ title: '请选择日期', icon: 'none' })
        return
      }
      try {
        // 后端实体字段为 weight/unit/note/measuredAt(LocalDateTime)，
        // 取所选日期中午时分提交，避免字段错位导致入库失败
        await petWeightApi.createPetWeight(this.petId, {
          weight: value,
          unit: 'kg',
          note: (this.weightForm.note || '').trim(),
          measuredAt: `${this.weightForm.date}T12:00:00`,
        })
        uni.showToast({ title: '记录成功', icon: 'success' })
        this.showWeightModal = false
        this.loadData()
      } catch (e) {
        console.warn('[weight] submit failed', e)
        uni.showToast({ title: '记录失败', icon: 'none' })
      }
    },

    onWeightDateChange(e) {
      this.weightForm.date = e.detail.value
    },

    // 后端 LocalDateTime 形如 2026-09-07T12:00:00，只取日期部分展示
    formatDate(iso) {
      if (!iso) return ''
      const s = String(iso)
      return s.length >= 10 ? s.slice(0, 10) : s
    },

    // 体重数值转文本（整数不带小数点）
    toWeightText(w) {
      const num = Number(w)
      if (Number.isNaN(num)) return '--'
      return num % 1 === 0 ? String(num) : num.toFixed(1)
    },
  },
}
</script>

<style lang="scss" scoped>
.weight-chart {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
}

// 顶部导航（自定义导航栏，叠加状态栏高度）
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

// 当前体重卡片
.current-weight-card {
  margin: 24rpx 24rpx 0;
  padding: 32rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  text-align: center;
}

.current-weight-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  display: block;
  margin-bottom: 16rpx;
}

.current-weight-main {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8rpx;
  margin-bottom: 16rpx;
}

.current-weight-number {
  font-size: 96rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.current-weight-unit {
  font-size: var(--font-size-lg);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}

.current-weight-trend {
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 6rpx 16rpx;
  border-radius: var(--radius-pill);
  margin-left: 12rpx;
}

.current-weight-trend-down {
  background: var(--color-success);
  color: #ffffff;
  opacity: 0.85;
}

.current-weight-trend-up {
  background: rgba(230, 185, 122, 0.2);
  color: var(--color-warning);
}

.trend-icon {
  font-size: 24rpx;
  font-weight: var(--font-weight-bold);
}

.trend-value {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
}

.current-weight-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}

.meta-item {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.meta-divider {
  font-size: var(--font-size-xs);
  color: var(--color-divider);
}

// 周/月切换
.chart-toggle {
  display: flex;
  align-items: center;
  margin: 24rpx 24rpx 0;
  padding: 4rpx;
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.toggle-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: var(--radius-pill);
  transition: all 0.2s;
}

.toggle-active {
  background: var(--color-primary);
}

.toggle-text {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.toggle-active .toggle-text {
  color: #ffffff;
  font-weight: var(--font-weight-semibold);
}

// 图表卡片
.chart-card {
  margin: 16rpx 24rpx 0;
  padding: 28rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.chart-header {
  margin-bottom: 24rpx;
}

.chart-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

// 图表容器（左轴刻度列 + 绘图区）
.chart-container {
  display: flex;
  align-items: stretch;
}

// Y 轴刻度列：刻度文本由 JS 定位在绘图区 60rpx 标签区之上
.chart-y-axis {
  position: relative;
  width: 96rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.y-tick {
  position: absolute;
  left: 0;
  right: 0;
  line-height: 1;
  transform: translateY(50%);
  text-align: right;
}

.y-label {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

// 绘图区总高 = 260rpx 数据区 + 60rpx 柱底标签区（与脚本 CHART_PLOT_H / CHART_LABEL_H 一致）
.chart-body {
  position: relative;
  flex: 1;
  height: 320rpx;
}

// 网格线层：只覆盖绘图区高度，线位置与刻度同源
.chart-grid {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 60rpx;
}

.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1rpx;
  background: var(--color-divider);
}

// 柱子层：铺满整个绘图区
.chart-bars {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: space-around;
}

.chart-bar-col {
  position: relative;
  flex: 1;
  max-width: 120rpx;
}

// 柱体：从柱底标签区顶部（bottom 60rpx）向上按数据刻度绘制
.bar-fill {
  position: absolute;
  bottom: 60rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 44rpx;
  border-radius: 8rpx 8rpx 0 0;
  transition: height 0.4s ease;
}

.bar-above {
  background: var(--color-warning);
  opacity: 0.7;
}

.bar-below {
  background: var(--color-primary);
  opacity: 0.7;
}

// 柱底标签（体重值 + 日期）
.bar-label-col {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

.bar-value-label {
  font-size: 18rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.bar-date-label {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

// 图表空态
.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

// 记录列表
.record-section {
  margin: 24rpx 24rpx 0;
}

.record-section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  display: block;
  margin-bottom: 16rpx;
}

.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: 12rpx;
  box-shadow: var(--shadow-sm);
}

.record-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.record-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  flex-shrink: 0;
}

.record-icon-up {
  background: rgba(230, 185, 122, 0.15);
}

.record-icon-down {
  background: var(--color-success);
  opacity: 0.85;
}

.record-icon {
  font-size: 28rpx;
  font-weight: var(--font-weight-bold);
  color: #ffffff;
}

.record-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.record-weight {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.record-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.record-right {
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.record-diff {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.diff-up {
  color: var(--color-warning);
}

.diff-down {
  color: var(--color-success);
}

.record-note {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
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

.weight-input-wrap {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.weight-input {
  flex: 1;
  height: 48rpx;
  border: none;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  background: transparent;
}

.weight-input-unit {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
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
