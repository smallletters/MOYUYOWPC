<template>
  <view class="weight-chart">
    <!-- 顶部导航 -->
    <view class="header">
      <view class="header-inner">
        <view class="header-btn" @click="onBack">
          <text class="header-btn-icon"><text class="luc luc-arrow-left"></text></text>
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
        <text class="current-weight-number">28.5</text>
        <text class="current-weight-unit">kg</text>
        <view class="current-weight-trend current-weight-trend-down">
          <text class="trend-icon">↓</text>
          <text class="trend-value">0.3</text>
        </view>
      </view>
      <view class="current-weight-meta">
        <text class="meta-item">较上月</text>
        <text class="meta-divider">|</text>
        <text class="meta-item">目标 25.0 - 30.0 kg</text>
      </view>
    </view>

    <!-- 周/月切换 -->
    <view class="chart-toggle">
      <view
        class="toggle-item"
        :class="{ 'toggle-active': chartMode === 'weekly' }"
        @click="chartMode = 'weekly'"
      >
        <text class="toggle-text">周</text>
      </view>
      <view
        class="toggle-item"
        :class="{ 'toggle-active': chartMode === 'monthly' }"
        @click="chartMode = 'monthly'"
      >
        <text class="toggle-text">月</text>
      </view>
    </view>

    <!-- 图表区域（div 模拟） -->
    <view class="chart-card">
      <view class="chart-header">
        <text class="chart-title">{{ chartMode === 'weekly' ? '本周' : '本月' }}体重变化</text>
      </view>
      <!-- 柱状图 -->
      <view class="chart-container">
        <!-- Y 轴标签 -->
        <view class="chart-y-axis">
          <text class="y-label">30.0</text>
          <text class="y-label">29.0</text>
          <text class="y-label">28.0</text>
          <text class="y-label">27.0</text>
          <text class="y-label">26.0</text>
        </view>
        <!-- 图表主体 -->
        <view class="chart-body">
          <!-- 网格线 -->
          <view class="chart-grid">
            <view v-for="i in 4" :key="i" class="grid-line" />
          </view>
          <!-- 柱子 -->
          <view class="chart-bars">
            <view v-for="(bar, bIdx) in chartData" :key="bIdx" class="chart-bar-col">
              <view class="bar-wrap">
                <view
                  class="bar-fill"
                  :class="{ 'bar-above': bar.value > 28.5, 'bar-below': bar.value <= 28.5 }"
                  :style="{ height: barHeight(bar.value) + '%' }"
                />
              </view>
              <view class="bar-label-col">
                <text class="bar-value-label">{{ bar.value }}</text>
                <text class="bar-date-label">{{ bar.label }}</text>
              </view>
            </view>
          </view>
        </view>
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
            <text class="record-icon luc" :class="$luc(record.trend === 'up'  ?  'arrow-up'  :  'arrow-down')"></text>
          </view>
          <view class="record-info">
            <text class="record-weight">{{ record.weight }} kg</text>
            <text class="record-date">{{ record.date }}</text>
          </view>
        </view>
        <view class="record-right">
          <text class="record-diff" :class="record.trend === 'up' ? 'diff-up' : 'diff-down'">
            {{ record.trend === 'up' ? '+' : '-' }}{{ record.diff }}
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
                <text class="form-picker-arrow"><text class="luc luc-chevron-right"></text></text>
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

export default {
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
      chartData: [],
      weightRecords: [],
    }
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
      if (!this.petId) return
      try {
        const [chart, records] = await Promise.all([
          petWeightApi.getPetWeightChart(this.petId),
          petWeightApi.getPetWeights(this.petId),
        ])
        this.chartData = chart || []
        this.weightRecords = records || []
      } catch (e) {
        console.warn('[weight] load failed', e)
        this.chartData = []
        this.weightRecords = []
      }
    },

    barHeight(value) {
      const min = 26.0
      const max = 30.0
      const percent = ((value - min) / (max - min)) * 100
      return Math.max(10, Math.min(100, percent))
    },

    onAddWeight() {
      this.weightForm = { value: '', date: '', note: '' }
      this.showWeightModal = true
    },

    onCloseWeightModal() {
      this.showWeightModal = false
    },

    async onSubmitWeight() {
      if (!this.weightForm.value) {
        uni.showToast({ title: '请输入体重', icon: 'none' })
        return
      }
      try {
        await petWeightApi.createPetWeight(this.petId, this.weightForm)
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
  },
}
</script>

<style lang="scss" scoped>
.weight-chart {
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

// 图表容器
.chart-container {
  display: flex;
  gap: 16rpx;
}

.chart-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-bottom: 56rpx;
  flex-shrink: 0;
}

.y-label {
  font-size: 18rpx;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.chart-body {
  flex: 1;
  position: relative;
}

.chart-grid {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-bottom: 56rpx;
}

.grid-line {
  height: 1rpx;
  background: var(--color-divider);
}

.chart-bars {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  height: 100%;
}

.chart-bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.bar-wrap {
  width: 40rpx;
  height: 240rpx;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.bar-fill {
  width: 100%;
  border-radius: 8rpx 8rpx 0 0;
  transition: height 0.4s ease;
  min-height: 8rpx;
}

.bar-above {
  background: var(--color-warning);
  opacity: 0.7;
}

.bar-below {
  background: var(--color-primary);
  opacity: 0.7;
}

.bar-label-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  margin-top: 8rpx;
}

.bar-value-label {
  font-size: 18rpx;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
  font-variant-numeric: tabular-nums;
}

.bar-date-label {
  font-size: 18rpx;
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
