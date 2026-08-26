<template>
  <view class="fit-finder">
    <view class="nav-header">
      <view class="nav-back" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">尺码推荐</text>
      <view class="nav-placeholder" />
    </view>

    <view class="step-indicator">
      <view class="step-dot" :class="{ active: currentStep >= 1, done: currentStep > 1 }">
        <text class="dot-text">{{ currentStep > 1 ? '✓' : '1' }}</text>
      </view>
      <view class="step-line" :class="{ active: currentStep > 1 }" />
      <view class="step-dot" :class="{ active: currentStep >= 2, done: currentStep > 2 }">
        <text class="dot-text">{{ currentStep > 2 ? '✓' : '2' }}</text>
      </view>
      <view class="step-line" :class="{ active: currentStep > 2 }" />
      <view class="step-dot" :class="{ active: currentStep >= 3 }">
        <text class="dot-text">3</text>
      </view>
    </view>

    <!-- Step 1: 宠物类型 -->
    <view v-if="currentStep === 1" class="step-content">
      <text class="step-title">选择宠物类型</text>
      <view class="pet-type-row">
        <view
          class="pet-type-card"
          :class="{ selected: petType === 'dog' }"
          @click="petType = 'dog'"
        >
          <text class="pet-type-icon"><text class="luc luc-paw-print"></text></text>
          <text class="pet-type-name">犬类</text>
        </view>
        <view
          class="pet-type-card"
          :class="{ selected: petType === 'cat' }"
          @click="petType = 'cat'"
        >
          <text class="pet-type-icon"><text class="luc luc-paw-print"></text></text>
          <text class="pet-type-name">猫咪</text>
        </view>
      </view>

      <text class="step-title">选择体重范围</text>
      <view class="weight-row">
        <view class="weight-display">{{ weight }} kg</view>
        <slider
          class="weight-slider"
          :value="weight"
          min="1"
          max="80"
          active-color="var(--color-primary)"
          block-size="20"
          @change="onWeightChange"
        />
        <view class="weight-labels">
          <text>1 kg</text>
          <text>80 kg</text>
        </view>
      </view>

      <text class="step-title">选择品种</text>
      <view class="breed-search">
        <input v-model="breedQuery" class="breed-input" placeholder="搜索品种，如金毛、柯基...">
      </view>
      <view class="breed-tags">
        <text
          v-for="breed in filteredBreeds"
          :key="breed"
          class="breed-tag"
          :class="{ selected: selectedBreed === breed }"
          @click="selectedBreed = breed"
        >
          {{ breed }}
        </text>
      </view>

      <view class="next-btn" @click="goStep(2)">
        <text>下一步：填写测量数据 <text class="luc luc-arrow-right"></text></text>
      </view>
    </view>

    <!-- Step 2: 测量数据 -->
    <view v-if="currentStep === 2" class="step-content">
      <text class="step-title">宠物测量数据</text>
      <view class="unit-toggle">
        <text class="unit-btn" :class="{ active: unit === 'cm' }" @click="unit = 'cm'">cm</text>
        <text class="unit-btn" :class="{ active: unit === 'inch' }" @click="unit = 'inch'">
          inch
        </text>
      </view>

      <view class="measure-card">
        <text class="measure-label">颈围</text>
        <view class="measure-input-row">
          <input
            v-model="neckGirth"
            class="measure-input"
            type="digit"
            placeholder="0.0">
          <text class="measure-unit">{{ unit }}</text>
        </view>
        <text class="measure-hint">颈部最粗处自然绕一圈</text>
      </view>

      <view class="measure-card">
        <text class="measure-label">胸围</text>
        <view class="measure-input-row">
          <input
            v-model="chestGirth"
            class="measure-input"
            type="digit"
            placeholder="0.0">
          <text class="measure-unit">{{ unit }}</text>
        </view>
        <text class="measure-hint">前肢后方最粗处绕一圈</text>
      </view>

      <view class="measure-card">
        <text class="measure-label">身长</text>
        <view class="measure-input-row">
          <input
            v-model="bodyLength"
            class="measure-input"
            type="digit"
            placeholder="0.0">
          <text class="measure-unit">{{ unit }}</text>
        </view>
        <text class="measure-hint">肩部到臀部的直线距离</text>
      </view>

      <view class="step-btn-row">
        <view class="prev-btn" @click="goStep(1)"><text class="luc luc-arrow-left"></text> 上一步</view>
        <view class="next-btn flex-2" @click="goStep(3)">获取尺码推荐 <text class="luc luc-sparkles"></text></view>
      </view>
    </view>

    <!-- Step 3: 推荐结果 -->
    <view v-if="currentStep === 3" class="step-content">
      <view class="result-card">
        <view class="result-header">
          <text class="result-icon"><text class="luc luc-check"></text></text>
          <text class="result-title">智能推荐</text>
        </view>
        <view class="result-body">
          <view class="result-info">
            <text class="result-label">最适合的尺码</text>
            <text class="result-size">{{ recommendedSize }}</text>
            <text class="result-desc">基于您的宠物数据与 1.2 万+ 退换货数据综合分析</text>
          </view>
          <view class="fit-ring">
            <text class="fit-percent">{{ fitConfidence }}%</text>
            <text class="fit-label">合身度</text>
          </view>
        </view>
        <view class="result-footer">
          <text>{{ recommendedSize }} 码在同类犬种中的退换率最低，仅 3.2%</text>
        </view>
      </view>

      <view class="size-chart">
        <text class="chart-title">尺码对照表</text>
        <text class="chart-unit">单位: {{ unit }}</text>
        <view class="chart-table">
          <view class="chart-header">
            <text class="chart-cell">尺码</text>
            <text class="chart-cell">胸围</text>
            <text class="chart-cell">颈围</text>
            <text class="chart-cell">身长</text>
            <text class="chart-cell">体重(kg)</text>
          </view>
          <view
            v-for="row in sizeChartRows"
            :key="row.size"
            class="chart-row"
            :class="{ recommended: row.size === recommendedSize }"
          >
            <text class="chart-cell">{{ row.size }}</text>
            <text class="chart-cell">{{ row.chest }}</text>
            <text class="chart-cell">{{ row.neck }}</text>
            <text class="chart-cell">{{ row.body }}</text>
            <text class="chart-cell">{{ row.weight }}</text>
          </view>
        </view>
      </view>

      <view class="step-btn-row">
        <view class="prev-btn" @click="goStep(2)"><text class="luc luc-arrow-left"></text> 修改数据</view>
        <view class="next-btn flex-2" @click="applySize">选择 {{ recommendedSize }} 码 <text class="luc luc-check"></text></view>
      </view>
    </view>
  </view>
</template>

<script>
const DOG_BREEDS = [
  '金毛寻回犬',
  '柯基',
  '拉布拉多',
  '泰迪',
  '哈士奇',
  '德国牧羊犬',
  '柴犬',
  '边境牧羊犬',
  '萨摩耶',
  '比熊',
]
const CAT_BREEDS = [
  '英国短毛猫',
  '美国短毛猫',
  '布偶猫',
  '暹罗猫',
  '波斯猫',
  '苏格兰折耳猫',
  '缅因猫',
  '中华田园猫',
]

export default {
  data() {
    return {
      currentStep: 1,
      petType: 'dog',
      weight: 15,
      breedQuery: '',
      selectedBreed: '',
      unit: 'cm',
      neckGirth: '',
      chestGirth: '',
      bodyLength: '',
      recommendedSize: 'M',
      fitConfidence: 92,
      breedList: DOG_BREEDS,
    }
  },

  computed: {
    filteredBreeds() {
      const list = this.petType === 'dog' ? DOG_BREEDS : CAT_BREEDS
      if (!this.breedQuery) return list
      return list.filter((b) => b.includes(this.breedQuery))
    },
    sizeChartRows() {
      return [
        { size: 'XS', chest: '33-40', neck: '20-25', body: '22-28', weight: '2-4' },
        { size: 'S', chest: '40-50', neck: '25-32', body: '28-35', weight: '4-10' },
        { size: 'M', chest: '50-62', neck: '32-40', body: '35-45', weight: '10-20' },
        { size: 'L', chest: '62-76', neck: '40-50', body: '45-56', weight: '20-35' },
        { size: 'XL', chest: '76-90', neck: '50-62', body: '56-68', weight: '35-55' },
      ]
    },
  },

  watch: {
    petType() {
      this.breedList = this.petType === 'dog' ? DOG_BREEDS : CAT_BREEDS
      this.selectedBreed = ''
      this.breedQuery = ''
    },
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onWeightChange(e) {
      this.weight = e.detail.value
    },

    goStep(step) {
      this.currentStep = step
    },

    applySize() {
      uni.showToast({ title: `已选择 ${this.recommendedSize} 码`, icon: 'success' })
    },
  },
}
</script>

<style lang="scss" scoped>
.fit-finder {
  min-height: 100vh;
  background: var(--color-background);
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
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

.nav-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.nav-placeholder {
  width: 60rpx;
}

.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx 48rpx;
}

.step-dot {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-dot.active {
  background: var(--color-primary);
}

.step-dot.done {
  background: var(--color-success);
}

.dot-text {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-bold);
}

.step-dot.done .dot-text {
  color: #fff;
}

.step-line {
  width: 64rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-divider);
  margin: 0 16rpx;
}

.step-line.active {
  background: var(--color-success);
}

.step-content {
  padding: 0 24rpx 48rpx;
}

.step-title {
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.pet-type-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.pet-type-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 32rpx;
  border-radius: var(--radius-md);
  border: 2rpx solid var(--color-divider);
  background: var(--color-surface);
}

.pet-type-card.selected {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.08);
}

.pet-type-icon {
  font-size: 64rpx;
}

.pet-type-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.weight-row {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 32rpx;
  border: 1rpx solid var(--color-divider);
}

.weight-display {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
  text-align: center;
  margin-bottom: 16rpx;
}

.weight-slider {
  width: 100%;
}

.weight-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.breed-search {
  margin-bottom: 16rpx;
}

.breed-input {
  width: 100%;
  height: 72rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.breed-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 48rpx;
}

.breed-tag {
  padding: 12rpx 24rpx;
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  border: 1rpx solid var(--color-divider);
}

.breed-tag.selected {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
  border-color: var(--color-primary);
}

.unit-toggle {
  display: flex;
  gap: 8rpx;
  margin-bottom: 24rpx;
}

.unit-btn {
  padding: 8rpx 24rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-tertiary);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.unit-btn.active {
  background: var(--color-primary);
  color: var(--color-text);
  border-color: var(--color-primary);
}

.measure-card {
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  padding: 20rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid var(--color-divider);
}

.measure-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.measure-input-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.measure-input {
  flex: 1;
  height: 56rpx;
  background: transparent;
  border: none;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.measure-unit {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.measure-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
}

.step-btn-row {
  display: flex;
  gap: 16rpx;
  margin-top: 32rpx;
}

.prev-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.next-btn {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-text);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.next-btn.flex-2 {
  flex: 2;
}

.result-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: 24rpx;
  border: 1rpx solid var(--color-divider);
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx;
}

.result-icon {
  font-size: 28rpx;
  color: var(--color-success);
  font-weight: var(--font-weight-bold);
}

.result-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-success);
}

.result-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24rpx 24rpx;
}

.result-info {
  flex: 1;
}

.result-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.result-size {
  font-size: 64rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  line-height: 1.2;
}

.result-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
}

.fit-ring {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  border: 10rpx solid var(--color-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.fit-percent {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
}

.fit-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.result-footer {
  padding: 16rpx 24rpx;
  background: rgba(219, 201, 138, 0.1);
  font-size: var(--font-size-xs);
  color: var(--color-primary-dark);
}

.size-chart {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 24rpx;
  border: 1rpx solid var(--color-divider);
}

.chart-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.chart-unit {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin: 8rpx 0 16rpx;
  display: block;
}

.chart-table {
  overflow-x: auto;
}

.chart-header {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr 1fr;
  padding: 12rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr 1fr;
  padding: 12rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.chart-row.recommended {
  background: rgba(219, 201, 138, 0.1);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
}

.chart-cell {
  font-size: var(--font-size-xs);
  color: var(--color-text);
  text-align: center;
}

.chart-header .chart-cell {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-semibold);
}
</style>
