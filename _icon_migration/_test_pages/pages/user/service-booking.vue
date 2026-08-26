<template>
  <view class="service-booking">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">服务预约</text>
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
            {{ step.label }}
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
        <text class="section-title">选择服务类型</text>
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
              <text class="service-icon-text luc" :class="$luc(svc.icon)"></text>
            </view>
            <text
              class="service-name"
              :style="{
                color: selectedService === svc.id ? 'var(--primary)' : 'var(--foreground)',
              }"
            >
              {{ svc.name }}
            </text>
            <text class="service-desc">{{ svc.desc }}</text>
          </view>
        </scroll-view>
      </view>

      <!-- 推荐门店 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">推荐门店</text>
          <text class="section-more">查看全部 <text class="luc luc-chevron-right"></text></text>
        </view>
        <view v-for="store in stores" :key="store.id" class="store-card">
          <view class="store-top">
            <view class="store-avatar">
              <text>{{ store.initial }}</text>
            </view>
            <view class="store-info">
              <view class="store-name-row">
                <text class="store-name">{{ store.name }}</text>
                <view class="store-rating">
                  <text class="star"><text class="luc luc-star"></text></text>
                  <text class="rating-value">{{ store.rating }}</text>
                </view>
              </view>
              <view class="store-address">
                <text>{{ store.address }}</text>
              </view>
              <view class="store-tags">
                <view v-for="tag in store.tags" :key="tag" class="store-tag">{{ tag }}</view>
              </view>
            </view>
          </view>
          <view class="store-bottom">
            <text class="store-price-label">参考价</text>
            <text class="store-price">¥{{ store.priceMin }} - ¥{{ store.priceMax }}</text>
            <view class="book-btn" @tap="handleQuickBook(store)">立即预约</view>
          </view>
        </view>
      </view>

      <!-- 选择日期 -->
      <view class="section">
        <text class="section-title">选择日期</text>
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
              {{ date.label }}
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
        <text class="section-title">选择时段</text>
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
              {{ slot.name }}
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
          <text class="section-title">选择宠物</text>
          <text class="section-more">＋ 添加宠物</text>
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
            <text v-if="selectedPet === pet.id" class="check-mark"><text class="luc luc-check"></text></text>
          </view>
          <view class="pet-avatar">
            <text>{{ pet.initial }}</text>
          </view>
          <view class="pet-info">
            <text class="pet-name">{{ pet.name }}</text>
            <text class="pet-breed">{{ pet.breed }}</text>
          </view>
          <text class="pet-arrow"><text class="luc luc-chevron-right"></text></text>
        </view>
      </view>

      <!-- 费用明细 -->
      <view class="section">
        <text class="section-title">费用明细</text>
        <view class="fee-card">
          <view class="fee-row">
            <text class="fee-label">洗澡 + 造型（金毛）</text>
            <text class="fee-value">¥198.00</text>
          </view>
          <view class="fee-row">
            <text class="fee-label">在线定金（20%）</text>
            <text class="fee-value fee-primary">-¥39.60</text>
          </view>
          <view class="fee-row total">
            <text class="fee-label">合计应付</text>
            <text class="fee-total">¥39.60</text>
          </view>
        </view>
        <text class="fee-note">到店后支付剩余 80% 费用（¥158.40），定金不可退</text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部确认预约栏 -->
    <view class="bottom-bar">
      <view class="policy-hint">
        <text class="hint-icon"><text class="luc luc-alert-triangle"></text></text>
        <text class="hint-text">
          预约成功后如需取消，请提前24小时操作；逾期取消或爽约将扣除全额定金。
        </text>
      </view>
      <view class="confirm-btn" @tap="handleConfirmBooking">
        <text>确认预约 · ¥39.60</text>
      </view>
    </view>
  </view>
</template><script>
import { petApi } from '@/api'

export default {
  data() {
    return {
      selectedService: 'grooming',
      selectedDate: 1,
      selectedTimeSlot: 'morning',
      selectedPet: null,
      steps: [
        { label: '选服务', done: true },
        { label: '选门店', done: false },
        { label: '选日期', done: false },
        { label: '选宠物', done: false },
        { label: '支付', done: false },
      ],
      services: [
        {
          id: 'grooming',
          name: '宠物美容',
          desc: '洗澡 / 造型 / 修甲',
          icon: 'sparkles',
          iconBg: 'var(--primary)',
        },
        {
          id: 'health',
          name: '健康体检',
          desc: '疫苗 / 驱虫 / 年检',
          icon: 'heart',
          iconBg: 'var(--secondary)',
        },
        {
          id: 'boarding',
          name: '宠物寄养',
          desc: '日托 / 长期',
          icon: '⌂',
          iconBg: 'var(--secondary)',
        },
        {
          id: 'home',
          name: '上门服务',
          desc: '上门洗护 / 上门喂养',
          icon: '⊕',
          iconBg: 'var(--secondary)',
        },
      ],
      stores: [
        {
          id: 1,
          name: '萌宠美学馆',
          initial: '萌',
          rating: 4.9,
          address: '朝阳区望京街道 · 1.2km',
          tags: ['洗澡', '造型', '修甲'],
          priceMin: 68,
          priceMax: 298,
        },
        {
          id: 2,
          name: '小爪子宠物护理',
          initial: '小',
          rating: 4.8,
          address: '海淀区中关村 · 3.5km',
          tags: ['洗澡', 'SPA护理'],
          priceMin: 58,
          priceMax: 198,
        },
        {
          id: 3,
          name: '汪星人美容工坊',
          initial: '汪',
          rating: 4.7,
          address: '西城区金融街 · 5.1km',
          tags: ['造型', '染色', '修甲'],
          priceMin: 88,
          priceMax: 358,
        },
      ],
      dates: [
        { label: '今天', num: '08' },
        { label: '明天', num: '09' },
        { label: '周四', num: '10' },
        { label: '周五', num: '11' },
        { label: '周六', num: '12' },
        { label: '周日', num: '13' },
        { label: '周一', num: '14' },
      ],
      timeSlots: [
        { id: 'morning', name: '上午', time: '09:00-12:00' },
        { id: 'afternoon', name: '下午', time: '12:00-18:00' },
        { id: 'evening', name: '晚上', time: '18:00-21:00' },
      ],
      pets: [],
    }
  },

  onLoad() {
    this.loadPets()
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
      uni.showToast({ title: `已选择「${store.name}」`, icon: 'success' })
    },

    async handleConfirmBooking() {
      try {
        await petApi.submitBooking({
          serviceId: this.selectedService,
          date: this.dates[this.selectedDate],
          timeSlot: this.selectedTimeSlot,
          petId: this.selectedPet,
        })
        uni.showToast({ title: '预约成功！', icon: 'success' })
      } catch (err) {
        uni.showToast({ title: err.message || '预约失败', icon: 'none' })
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
