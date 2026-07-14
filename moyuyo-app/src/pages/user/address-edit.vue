<template>
  <view class="address-edit">
    <scroll-view scroll-y class="form">
      <view class="input-group">
        <text class="input-label">First Name *</text>
        <input v-model="form.first_name" class="input" />
      </view>
      <view class="input-group">
        <text class="input-label">Last Name *</text>
        <input v-model="form.last_name" class="input" />
      </view>
      <view class="input-group">
        <text class="input-label">Phone *</text>
        <input v-model="form.phone" class="input" type="number" />
      </view>
      <view class="input-group">
        <text class="input-label">Country *</text>
        <picker mode="selector" :range="countries" @change="onCountryChange">
          <view class="input picker">{{ form.country || 'Select' }}</view>
        </picker>
      </view>
      <view class="input-group">
        <text class="input-label">State / Province</text>
        <input v-model="form.state" class="input" />
      </view>
      <view class="input-group">
        <text class="input-label">City *</text>
        <input v-model="form.city" class="input" />
      </view>
      <view class="input-group">
        <text class="input-label">Address Line 1 *</text>
        <input v-model="form.address_1" class="input" placeholder="Street, building, etc." />
      </view>
      <view class="input-group">
        <text class="input-label">Address Line 2</text>
        <input v-model="form.address_2" class="input" placeholder="Apt, suite, unit" />
      </view>
      <view class="input-group">
        <text class="input-label">Postal Code *</text>
        <input v-model="form.postcode" class="input" />
      </view>
      <view class="default-row" @click="form.isDefault = !form.isDefault">
        <view class="checkbox" :class="{ checked: form.isDefault }">
          <text v-if="form.isDefault">✓</text>
        </view>
        <text>Set as default address</text>
      </view>
    </scroll-view>

    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-primary save-btn" @click="onSave">Save</view>
    </view>
  </view>
</template>

<script>
import { setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

export default {
  data() {
    return {
      form: {
        id: null,
        first_name: '',
        last_name: '',
        phone: '',
        country: 'US',
        state: '',
        city: '',
        address_1: '',
        address_2: '',
        postcode: '',
        isDefault: false
      },
      countries: ['US', 'CA', 'GB', 'DE', 'FR', 'ES', 'IT', 'NL', 'AU', 'JP']
    }
  },

  onLoad(query) {
    if (query.id) {
      const list = getStorage(STORAGE_KEYS.ADDRESS_LIST, [])
      const addr = list.find((a) => a.id == query.id)
      if (addr) this.form = { ...addr }
    }
  },

  methods: {
    onCountryChange(e) {
      this.form.country = this.countries[e.detail.value]
    },

    onSave() {
      const required = ['first_name', 'last_name', 'phone', 'address_1', 'city', 'postcode']
      for (const key of required) {
        if (!this.form[key]) {
          uni.showToast({ title: '请填写完整信息', icon: 'none' })
          return
        }
      }
      const list = getStorage(STORAGE_KEYS.ADDRESS_LIST, [])
      if (this.form.id) {
        // 编辑
        const idx = list.findIndex((a) => a.id === this.form.id)
        if (idx >= 0) {
          list[idx] = { ...this.form }
        }
      } else {
        // 新增
        this.form.id = Date.now()
        list.push({ ...this.form })
      }
      // 默认地址处理
      if (this.form.isDefault) {
        list.forEach((a) => (a.isDefault = a.id === this.form.id))
      }
      setStorage(STORAGE_KEYS.ADDRESS_LIST, list)
      uni.showToast({ title: 'Saved', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 800)
    }
  }
}
</script>

<style lang="scss" scoped>
.address-edit {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.form {
  flex: 1;
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.input-group {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16rpx 24rpx;
}

.input-label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 4rpx;
}

.input {
  display: block;
  width: 100%;
  font-size: var(--font-size-base);
}

.picker {
  min-height: 44rpx;
}

.default-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.checkbox {
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20rpx;
}

.checkbox.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.bottom-bar {
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.save-btn {
  padding: 24rpx 0;
  font-size: var(--font-size-md);
}
</style>
