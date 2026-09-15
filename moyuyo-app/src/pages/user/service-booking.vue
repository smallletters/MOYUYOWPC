<template>
  <view class="service-booking">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('serviceBooking.title') }}</text>
      <view class="header-btn" />
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 预约流程步骤指示 -->
      <view class="steps">
        <view v-for="(step, idx) in steps" :key="idx" class="step-item">
          <view
            class="step-circle"
            :style="{
              background: step.done ? 'var(--primary)' : 'var(--background-300)',
              color: step.done ? 'var(--primary-foreground)' : 'var(--text-400)',
            }"
          >
            <text>{{ idx + 1 }}</text>
          </view>
          <text
            class="step-label"
            :style="{ color: step.done ? 'var(--primary)' : 'var(--text-400)' }"
          >
            {{ $t(step.labelKey) }}
          </text>
          <view
            v-if="idx < steps.length - 1"
            class="step-line"
            :style="{ background: step.done ? 'var(--primary)' : 'var(--background-300)' }"
          />
        </view>
      </view>

      <!-- 服务类型选择 -->
      <view class="section">
        <text class="section-title">{{ $t('serviceBooking.selectServiceType') }}</text>
        <scroll-view scroll-x class="service-scroll" show-scrollbar="false">
          <view
            v-for="svc in services"
            :key="svc.id"
            class="service-card"
            :class="{ active: selectedService === svc.id }"
            :style="{
              borderColor: selectedService === svc.id ? 'var(--primary)' : 'var(--border)',
              background: selectedService === svc.id ? 'var(--brand-50)' : 'var(--card)',
            }"
            @tap="selectedService = svc.id"
          >
            <view class="service-icon" :style="{ background: svc.iconBg }">
              <text class="service-icon-text luc" :class="$luc(svc.icon)" />
            </view>
            <text
              class="service-name"
              :style="{
                color: selectedService === svc.id ? 'var(--primary)' : 'var(--foreground)',
              }"
            >
              {{ $t(svc.nameKey) }}
            </text>
            <text class="service-desc">{{ $t(svc.descKey) }}</text>
          </view>
        </scroll-view>
      </view>

      <!-- 推荐门店 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">{{ $t('serviceBooking.recommendedStores') }}</text>
          <text class="section-more">
            {{ $t('serviceBooking.viewAll') }}
            <text class="luc luc-chevron-right" />
          </text>
        </view>
        <view v-for="store in stores" :key="store.id" class="store-card">
          <view class="store-top">
            <view class="store-avatar">
              <text>{{ $t(store.initialKey) }}</text>
            </view>
            <view class="store-info">
              <view class="store-name-row">
                <text class="store-name">{{ $t(store.nameKey) }}</text>
                <view class="store-rating">
                  <text class="star luc-star" />
                  <text class="rating-value">{{ store.rating }}</text>
                </view>
              </view>
              <view class="store-address">
                <text>{{ $t(store.addressKey) }}</text>
              </view>
              <view class="store-tags">
                <view v-for="tag in store.tags" :key="tag" class="store-tag">{{ $t(tag) }}</view>
              </view>
            </view>
          </view>
          <view class="store-bottom">
            <text class="store-price-label">{{ $t('serviceBooking.referencePrice') }}</text>
            <text class="store-price">
              {{ currencySymbol }}{{ store.priceMin }} - {{ currencySymbol }}{{ store.priceMax }}
            </text>
            <view class="book-btn" @tap="handleQuickBook(store)">
              {{ $t('serviceBooking.bookNow') }}
            </view>
          </view>
        </view>
      </view>

      <!-- 选择日期 -->
      <view class="section">
        <text class="section-title">{{ $t('serviceBooking.selectDate') }}</text>
        <scroll-view scroll-x class="date-scroll" show-scrollbar="false">
          <view
            v-for="(date, idx) in dates"
            :key="idx"
            class="date-cell"
            :class="{ active: selectedDate === idx }"
            :style="{
              background: selectedDate === idx ? 'var(--primary)' : 'var(--card)',
              borderColor: selectedDate === idx ? 'var(--primary)' : 'var(--border)',
            }"
            @tap="selectedDate = idx"
          >
            <text
              class="date-label"
              :style="{
                color: selectedDate === idx ? 'var(--primary-foreground)' : 'var(--text-400)',
              }"
            >
              {{ $t(date.labelKey) }}
            </text>
            <text
              class="date-num"
              :style="{
                color: selectedDate === idx ? 'var(--primary-foreground)' : 'var(--foreground)',
              }"
            >
              {{ date.num }}
            </text>
          </view>
        </scroll-view>
      </view>

      <!-- 选择时段 -->
      <view class="section">
        <text class="section-title">{{ $t('serviceBooking.selectSlot') }}</text>
        <view class="time-grid">
          <view
            v-for="slot in timeSlots"
            :key="slot.id"
            class="time-slot"
            :class="{ active: selectedTimeSlot === slot.id }"
            :style="{
              background: selectedTimeSlot === slot.id ? 'var(--primary)' : 'var(--card)',
              borderColor: selectedTimeSlot === slot.id ? 'var(--primary)' : 'var(--border)',
            }"
            @tap="selectedTimeSlot = slot.id"
          >
            <text
              class="slot-name"
              :style="{
                color:
                  selectedTimeSlot === slot.id ? 'var(--primary-foreground)' : 'var(--foreground)',
              }"
            >
              {{ $t(slot.nameKey) }}
            </text>
            <text
              class="slot-time"
              :style="{
                color:
                  selectedTimeSlot === slot.id ? 'var(--primary-foreground)' : 'var(--text-400)',
              }"
            >
              {{ slot.time }}
            </text>
          </view>
        </view>
      </view>

      <!-- 选择宠物 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">{{ $t('serviceBooking.selectPet') }}</text>
          <text class="section-more">{{ $t('serviceBooking.addPet') }}</text>
        </view>
        <view
          v-for="pet in pets"
          :key="pet.id"
          class="pet-card"
          :class="{ active: selectedPet === pet.id }"
          :style="{
            borderColor: selectedPet === pet.id ? 'var(--primary)' : 'var(--border)',
            background: selectedPet === pet.id ? 'var(--brand-50)' : 'var(--card)',
          }"
          @tap="selectedPet = pet.id"
        >
          <view
            class="pet-checkbox"
            :style="{
              background: selectedPet === pet.id ? 'var(--primary)' : 'transparent',
              borderColor: selectedPet === pet.id ? 'var(--primary)' : 'var(--background-400)',
            }"
          >
            <text v-if="selectedPet === pet.id" class="check-mark">
              <text class="luc luc-check" />
            </text>
          </view>
          <view class="pet-avatar">
            <text>{{ pet.initial }}</text>
          </view>
          <view class="pet-info">
            <text class="pet-name">{{ pet.name }}</text>
            <text class="pet-breed">{{ pet.breed }}</text>
          </view>
          <text class="pet-arrow luc-chevron-right" />
        </view>
      </view>

      <!-- 费用明细 -->
      <view class="section">
        <text class="section-title">{{ $t('serviceBooking.feeDetail') }}</text>
        <view class="fee-card">
          <view class="fee-row">
            <text class="fee-label">{{ $t('serviceBooking.feeItemGrooming') }}</text>
            <text class="fee-value">{{ currencySymbol }}198.00</text>
          </view>
          <view class="fee-row">
            <text class="fee-label">{{ $t('serviceBooking.feeItemDeposit') }}</text>
            <text class="fee-value fee-primary">-{{ currencySymbol }}39.60</text>
          </view>
          <view class="fee-row total">
            <text class="fee-label">{{ $t('serviceBooking.feeTotal') }}</text>
            <text class="fee-total">{{ currencySymbol }}39.60</text>
          </view>
        </view>
        <text class="fee-note">
          {{ $t('serviceBooking.feeNote', { amount: currencySymbol + '158.40' }) }}
        </text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部确认预约栏 -->
    <view class="bottom-bar">
      <view class="policy-hint">
        <text class="hint-icon luc-alert-triangle" />
        <text class="hint-text">
          {{ $t('serviceBooking.cancelPolicy') }}
        </text>
      </view>
      <view class="confirm-btn" @tap="handleConfirmBooking">
        <text>
          {{ $t('serviceBooking.confirmBookingFmt', { amount: currencySymbol + '39.60' }) }}
        </text>
      </view>
    </view>
  </view>
</template>
<script>
import { petApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userServiceBooking',

  data() {
    return {
      selectedService: 'grooming',
      selectedDate: 1,
      selectedTimeSlot: 'morning',
      selectedPet: null,
      // locale 版本号：locale 切换时自增，触发 computed 重算
      localeVersion: 0,
      steps: [
        { labelKey: 'serviceBooking.stepService', done: true },
        { labelKey: 'serviceBooking.stepStore', done: false },
        { labelKey: 'serviceBooking.stepDate', done: false },
        { labelKey: 'serviceBooking.stepPet', done: false },
        { labelKey: 'serviceBooking.stepPay', done: false },
      ],
      services: [
        {
          id: 'grooming',
          nameKey: 'serviceBooking.serviceGrooming',
          descKey: 'serviceBooking.serviceGroomingDesc',
          icon: 'sparkles',
          iconBg: 'var(--primary)',
        },
        {
          id: 'health',
          nameKey: 'serviceBooking.serviceHealth',
          descKey: 'serviceBooking.serviceHealthDesc',
          icon: 'heart',
          iconBg: 'var(--secondary)',
        },
        {
          id: 'boarding',
          nameKey: 'serviceBooking.serviceBoarding',
          descKey: 'serviceBooking.serviceBoardingDesc',
          icon: '⌂',
          iconBg: 'var(--secondary)',
        },
        {
          id: 'home',
          nameKey: 'serviceBooking.serviceHome',
          descKey: 'serviceBooking.serviceHomeDesc',
          icon: '⊕',
          iconBg: 'var(--secondary)',
        },
      ],
      stores: [
        {
          id: 1,
          nameKey: 'serviceBooking.store1Name',
          initialKey: 'serviceBooking.store1Initial',
          rating: 4.9,
          addressKey: 'serviceBooking.store1Address',
          tags: ['serviceBooking.tagBath', 'serviceBooking.tagGrooming', 'serviceBooking.tagNail'],
          priceMin: 68,
          priceMax: 298,
        },
        {
          id: 2,
          nameKey: 'serviceBooking.store2Name',
          initialKey: 'serviceBooking.store2Initial',
          rating: 4.8,
          addressKey: 'serviceBooking.store2Address',
          tags: ['serviceBooking.tagBath', 'serviceBooking.tagSpa'],
          priceMin: 58,
          priceMax: 198,
        },
        {
          id: 3,
          nameKey: 'serviceBooking.store3Name',
          initialKey: 'serviceBooking.store3Initial',
          rating: 4.7,
          addressKey: 'serviceBooking.store3Address',
          tags: ['serviceBooking.tagGrooming', 'serviceBooking.tagDye', 'serviceBooking.tagNail'],
          priceMin: 88,
          priceMax: 358,
        },
      ],
      dates: [
        { labelKey: 'serviceBooking.today', num: '08' },
        { labelKey: 'serviceBooking.tomorrow', num: '09' },
        { labelKey: 'serviceBooking.weekThu', num: '10' },
        { labelKey: 'serviceBooking.weekFri', num: '11' },
        { labelKey: 'serviceBooking.weekSat', num: '12' },
        { labelKey: 'serviceBooking.weekSun', num: '13' },
        { labelKey: 'serviceBooking.weekMon', num: '14' },
      ],
      timeSlots: [
        { id: 'morning', nameKey: 'serviceBooking.slotMorning', time: '09:00-12:00' },
        { id: 'afternoon', nameKey: 'serviceBooking.slotAfternoon', time: '12:00-18:00' },
        { id: 'evening', nameKey: 'serviceBooking.slotEvening', time: '18:00-21:00' },
      ],
      pets: [],
    }
  },

  computed: {
    /**
     * 当前语言货币符号：locale 切换时通过 localeVersion 触发重算
     */
    currencySymbol() {
      // 读取 this.localeVersion 让 Vue 追踪依赖，触发 reactive
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad() {
    this.loadPets()
    // 订阅 locale 变化：locale 切换时让 currencySymbol 重新计算
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadPets() {
      try {
        const res = await petApi.getPetList()
        this.pets = res.data || []
        if (this.pets.length > 0 && !this.selectedPet) {
          this.selectedPet = this.pets[0].id
        }
      } catch (err) {
        console.error('加载宠物列表失败', err)
      }
    },

    handleQuickBook(store) {
      uni.showToast({
        title: i18n.t('serviceBooking.selectedStoreFmt', { name: i18n.t(store.nameKey) }),
        icon: 'success',
      })
    },

    async handleConfirmBooking() {
      try {
        await petApi.submitBooking({
          serviceId: this.selectedService,
          date: this.dates[this.selectedDate],
          timeSlot: this.selectedTimeSlot,
          petId: this.selectedPet,
        })
        uni.showToast({ title: i18n.t('serviceBooking.bookingSuccess'), icon: 'success' })
      } catch (err) {
        uni.showToast({
          title: err.message || i18n.t('serviceBooking.bookingFailed'),
          icon: 'none',
        })
      }
    },
  },
}
</script>

<style scoped lang="scss">
.service-booking {
  min-height: 100vh;
  background: var(--background);
}
.header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 2rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}
.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--secondary);
}
.back-icon {
  font-size: 40rpx;
  color: var(--primary);
}
.header-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}
.scroll {
  padding-bottom: 240rpx;
}
.section {
  padding: 30rpx;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  margin-bottom: 24rpx;
}
.section-more {
  font-size: 24rpx;
  color: var(--primary);
}
.steps {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30rpx;
  gap: 0;
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  position: relative;
}
.step-circle {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}
.step-label {
  font-size: 20rpx;
  font-weight: 500;
}
.step-line {
  width: 48rpx;
  height: 4rpx;
  border-radius: 2rpx;
  margin-top: -60rpx;
}
.service-scroll {
  display: flex;
  flex-direction: row;
  white-space: nowrap;
  overflow-x: auto;
}
.service-card {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 28rpx 24rpx;
  border-radius: 24rpx;
  border: 2rpx solid var(--border);
  margin-right: 20rpx;
  width: 240rpx;
  flex-shrink: 0;
}
.service-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.12;
}
.service-icon-text {
  font-size: 40rpx;
  color: var(--primary);
}
.service-name {
  font-size: 24rpx;
  font-weight: 600;
}
.service-desc {
  font-size: 20rpx;
  color: var(--text-400);
  text-align: center;
}
.store-card {
  border-radius: 24rpx;
  background: var(--card);
  border: 2rpx solid var(--border);
  padding: 30rpx;
  margin-bottom: 20rpx;
}
.store-top {
  display: flex;
  gap: 20rpx;
}
.store-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 16rpx;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: var(--icon-muted);
  flex-shrink: 0;
}
.store-info {
  flex: 1;
  min-width: 0;
}
.store-name-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.store-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.store-rating {
  display: flex;
  align-items: center;
  gap: 4rpx;
}
.star {
  color: var(--state-warning);
  font-size: 28rpx;
}
.rating-value {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--foreground);
}
.store-address {
  font-size: 24rpx;
  color: var(--text-400);
  margin-top: 8rpx;
}
.store-tags {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}
.store-tag {
  font-size: 20rpx;
  font-weight: 500;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: var(--brand-50);
  color: var(--primary);
}
.store-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid var(--border);
}
.store-price-label {
  font-size: 20rpx;
  color: var(--text-400);
}
.store-price {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--primary);
}
.book-btn {
  height: 64rpx;
  padding: 0 32rpx;
  border-radius: 16rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.date-scroll {
  display: flex;
  flex-direction: row;
  white-space: nowrap;
  overflow-x: auto;
}
.date-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  width: 80rpx;
  height: 104rpx;
  border-radius: 16rpx;
  border: 2rpx solid var(--border);
  margin-right: 16rpx;
  justify-content: center;
  flex-shrink: 0;
}
.date-label {
  font-size: 20rpx;
  font-weight: 500;
}
.date-num {
  font-size: 28rpx;
  font-weight: 700;
}
.time-grid {
  display: flex;
  gap: 16rpx;
}
.time-slot {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 16rpx;
  border: 2rpx solid var(--border);
  text-align: center;
}
.slot-name {
  font-size: 24rpx;
  font-weight: 600;
}
.slot-time {
  font-size: 20rpx;
  margin-top: 4rpx;
  display: block;
}
.pet-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 30rpx;
  border-radius: 24rpx;
  border: 2rpx solid var(--border);
  margin-bottom: 16rpx;
}
.pet-checkbox {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 4rpx solid var(--background-400);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.check-mark {
  color: var(--primary-foreground);
  font-size: 24rpx;
  font-weight: 700;
}
.pet-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: var(--icon-muted);
  flex-shrink: 0;
}
.pet-info {
  flex: 1;
}
.pet-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.pet-breed {
  font-size: 20rpx;
  color: var(--text-400);
  margin-top: 4rpx;
  display: block;
}
.pet-arrow {
  color: var(--text-400);
  font-size: 32rpx;
}
.fee-card {
  border-radius: 24rpx;
  border: 2rpx solid var(--border);
  overflow: hidden;
}
.fee-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 30rpx;
  height: 88rpx;
  border-bottom: 2rpx solid var(--border);
}
.fee-row.total {
  background: var(--secondary);
  border-bottom: none;
}
.fee-label {
  font-size: 24rpx;
  color: var(--text-500);
}
.fee-value {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--foreground);
}
.fee-value.fee-primary {
  color: var(--primary);
}
.fee-total {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--primary);
}
.fee-note {
  font-size: 20rpx;
  color: var(--text-400);
  margin-top: 16rpx;
  display: block;
}
.bottom-spacer {
  height: 20rpx;
}
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  border-top: 2rpx solid var(--border);
  padding: 0 30rpx;
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 20rpx);
  z-index: 40;
}
.policy-hint {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 20rpx 0 8rpx;
}
.hint-icon {
  font-size: 28rpx;
  color: var(--state-warning);
  flex-shrink: 0;
}
.hint-text {
  font-size: 20rpx;
  color: var(--text-500);
  line-height: 1.5;
}
.confirm-btn {
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 8rpx;
}
</style>
