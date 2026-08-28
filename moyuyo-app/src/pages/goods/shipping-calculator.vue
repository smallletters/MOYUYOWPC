<template>
  <view class="shipping-calculator">
    <view class="nav-header">
      <view class="nav-back" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="nav-title">运费计算</text>
      <view class="nav-placeholder" />
    </view>

    <scroll-view class="scroll" scroll-y>
      <view class="section-card">
        <view class="section-title">
          <text class="section-icon"><text class="luc luc-map-pin" /></text>
          <text>收货地址</text>
        </view>
        <view class="form-row">
          <view class="form-group">
            <text class="form-label">国家/地区</text>
            <view class="form-select" @click="showCountryPicker = true">
              <text class="select-text">{{ selectedCountry.name }}</text>
              <text class="select-arrow">▼</text>
            </view>
          </view>
        </view>
        <view class="form-row">
          <view class="form-group">
            <text class="form-label">邮政编码</text>
            <input
              v-model="zipCode"
              class="form-input"
              type="text"
              placeholder="例如 10001"
              maxlength="10"
              @input="onZipInput"
            >
          </view>
          <view class="form-group">
            <text class="form-label">州/省</text>
            <input
              v-model="state"
              class="form-input"
              type="text"
              placeholder="自动填充"
              disabled>
          </view>
        </view>
        <view class="form-row">
          <view class="form-group">
            <text class="form-label">城市</text>
            <input
              v-model="city"
              class="form-input"
              type="text"
              placeholder="自动填充"
              disabled>
          </view>
        </view>
      </view>

      <view class="section-card">
        <view class="section-title">
          <text class="section-icon"><text class="luc luc-package" /></text>
          <text>包裹信息</text>
        </view>
        <view class="form-row">
          <view class="form-group">
            <text class="form-label">预估重量 (kg)</text>
            <input
              v-model="weight"
              class="form-input"
              type="digit"
              placeholder="例如 1.5">
          </view>
        </view>
      </view>

      <view class="calc-btn" @click="calculateShipping">
        <text class="calc-btn-text">计算运费</text>
      </view>

      <view v-if="results.length > 0" class="results-section">
        <view class="section-title">
          <text class="section-icon"><text class="luc luc-truck" /></text>
          <text>可选配送方式</text>
        </view>
        <view
          v-for="result in results"
          :key="result.carrier"
          class="carrier-card"
          :class="{ selected: selectedCarrier === result.carrier }"
          @click="selectedCarrier = result.carrier"
        >
          <view class="carrier-radio">
            <view v-if="selectedCarrier === result.carrier" class="radio-dot" />
          </view>
          <view class="carrier-info">
            <view class="carrier-name-row">
              <text class="carrier-name">{{ result.carrier }}</text>
              <text v-if="result.recommended" class="carrier-tag recommended">推荐</text>
              <text v-if="result.fastest" class="carrier-tag fastest">最快</text>
            </view>
            <text class="carrier-eta">{{ result.eta }}</text>
          </view>
          <text class="carrier-price">${{ result.cost }}</text>
        </view>
      </view>

      <view v-if="history.length > 0" class="history-section">
        <view class="section-title">
          <text class="section-icon"><text class="luc luc-clock" /></text>
          <text>最近查询</text>
        </view>
        <view v-for="(item, idx) in history" :key="idx" class="history-item">
          <text class="history-dest">{{ item.country }} {{ item.state }} {{ item.city }}</text>
          <text class="history-result">${{ item.cost }} - {{ item.carrier }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="showCountryPicker" class="modal-overlay" @click="showCountryPicker = false">
      <view class="modal-content" @click.stop>
        <text class="modal-title">选择国家/地区</text>
        <scroll-view class="modal-list" scroll-y>
          <view
            v-for="country in countryList"
            :key="country.code"
            class="modal-item"
            :class="{ active: selectedCountry.code === country.code }"
            @click="selectCountry(country)"
          >
            <text class="modal-item-name">{{ country.name }}</text>
            <text v-if="selectedCountry.code === country.code" class="modal-check">
              <text class="luc luc-check" />
            </text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
const ZIP_LOOKUP = {
  10001: { city: 'New York', state: 'NY' },
  90210: { city: 'Beverly Hills', state: 'CA' },
  60601: { city: 'Chicago', state: 'IL' },
  '02101': { city: 'Boston', state: 'MA' },
  98101: { city: 'Seattle', state: 'WA' },
  33101: { city: 'Miami', state: 'FL' },
  77001: { city: 'Houston', state: 'TX' },
  85001: { city: 'Phoenix', state: 'AZ' },
}

const COUNTRIES = [
  { code: 'US', name: '美国' },
  { code: 'CA', name: '加拿大' },
  { code: 'UK', name: '英国' },
  { code: 'AU', name: '澳大利亚' },
  { code: 'JP', name: '日本' },
  { code: 'DE', name: '德国' },
  { code: 'FR', name: '法国' },
  { code: 'CN', name: '中国' },
]

export default {
  data() {
    return {
      selectedCountry: COUNTRIES[0],
      zipCode: '',
      state: '',
      city: '',
      weight: '',
      results: [],
      selectedCarrier: '',
      history: [],
      showCountryPicker: false,
      countryList: COUNTRIES,
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onZipInput() {
      const match = ZIP_LOOKUP[this.zipCode]
      if (match) {
        this.city = match.city
        this.state = match.state
      } else {
        this.city = ''
        this.state = ''
      }
    },

    selectCountry(country) {
      this.selectedCountry = country
      this.showCountryPicker = false
    },

    calculateShipping() {
      if (!this.weight || parseFloat(this.weight) <= 0) {
        uni.showToast({ title: '请输入有效重量', icon: 'none' })
        return
      }
      const w = parseFloat(this.weight)
      this.results = [
        {
          carrier: 'USPS Standard',
          cost: (5.99 + w * 0.5).toFixed(2),
          eta: '预计 5-7 个工作日',
          recommended: true,
          fastest: false,
        },
        {
          carrier: 'FedEx Express',
          cost: (12.99 + w * 0.8).toFixed(2),
          eta: '预计 2-3 个工作日',
          recommended: false,
          fastest: true,
        },
        {
          carrier: 'DHL International',
          cost: (18.5 + w * 1.2).toFixed(2),
          eta: '预计 3-5 个工作日',
          recommended: false,
          fastest: false,
        },
      ]
      this.selectedCarrier = this.results[0].carrier
      this.history.unshift({
        country: this.selectedCountry.name,
        state: this.state || '—',
        city: this.city || '—',
        cost: this.results[0].cost,
        carrier: this.results[0].carrier,
      })
      if (this.history.length > 5) this.history.pop()
    },
  },
}
</script>

<style lang="scss" scoped>
.shipping-calculator {
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

.scroll {
  height: calc(100vh - 88rpx);
  padding: 24rpx;
}

.section-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 24rpx;
  border: 1rpx solid var(--color-divider);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.section-icon {
  font-size: 28rpx;
}

.form-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.form-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

.form-input {
  height: 72rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-sm);
  background: var(--color-background);
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.form-input:disabled {
  opacity: 0.6;
  background: var(--color-divider);
}

.form-select {
  height: 72rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-sm);
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.select-text {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.select-arrow {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
}

.calc-btn {
  height: 88rpx;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}

.calc-btn-text {
  color: var(--color-text);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.results-section {
  margin-bottom: 24rpx;
}

.carrier-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  border-radius: var(--radius-sm);
  border: 2rpx solid var(--color-divider);
  margin-bottom: 12rpx;
  background: var(--color-surface);
}

.carrier-card.selected {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.08);
}

.carrier-radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.carrier-card.selected .carrier-radio {
  border-color: var(--color-primary);
}

.radio-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: var(--color-primary);
}

.carrier-info {
  flex: 1;
}

.carrier-name-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.carrier-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.carrier-tag {
  font-size: 18rpx;
  font-weight: var(--font-weight-bold);
  padding: 2rpx 10rpx;
  border-radius: 4rpx;
}

.carrier-tag.recommended {
  background: var(--color-primary);
  color: var(--color-text);
}

.carrier-tag.fastest {
  background: var(--color-primary-light);
  color: var(--color-primary-dark);
}

.carrier-eta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.carrier-price {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  white-space: nowrap;
}

.history-section {
  padding-bottom: 48rpx;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 20rpx;
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  margin-bottom: 8rpx;
  border: 1rpx solid var(--color-divider);
}

.history-dest {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.history-result {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
}

.modal-content {
  width: 100%;
  max-height: 60vh;
  background: var(--color-surface);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: 32rpx;
}

.modal-title {
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 24rpx;
  text-align: center;
}

.modal-list {
  max-height: 50vh;
}

.modal-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.modal-item.active .modal-item-name {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.modal-item-name {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.modal-check {
  font-size: 24rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-bold);
}
</style>
