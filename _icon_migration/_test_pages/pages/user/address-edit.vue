<template>
  <view class="address-edit">
    <scroll-view scroll-y class="form">
      <view class="input-group">
        <text class="input-label">Full Name *</text>
        <input v-model="form.receiver" class="input" placeholder="John Doe">
      </view>
      <view class="input-group">
        <text class="input-label">Phone *</text>
        <input
          v-model="form.phone"
          class="input"
          type="number"
          placeholder="+1 555 1234">
      </view>
      <view class="input-group">
        <text class="input-label">Country *</text>
        <picker mode="selector" :range="countries" @change="onCountryChange">
          <view class="input picker">{{ form.country || 'Select' }}</view>
        </picker>
      </view>
      <view class="input-group">
        <text class="input-label">Province / State</text>
        <input v-model="form.province" class="input" placeholder="California">
      </view>
      <view class="input-group">
        <text class="input-label">City *</text>
        <input v-model="form.city" class="input">
      </view>
      <view class="input-group">
        <text class="input-label">District</text>
        <input v-model="form.district" class="input">
      </view>
      <view class="input-group">
        <text class="input-label">Address *</text>
        <input v-model="form.detail" class="input" placeholder="Street, building, apt">
      </view>
      <view class="input-group">
        <text class="input-label">Postal Code</text>
        <input v-model="form.zipCode" class="input">
      </view>
      <view class="input-group">
        <text class="input-label">Tag</text>
        <picker mode="selector" :range="tags" @change="onTagChange">
          <view class="input picker">{{ form.tag || 'Select (optional)' }}</view>
        </picker>
      </view>
      <view class="default-row" @click="form.isDefault = !form.isDefault">
        <view class="checkbox" :class="{ checked: form.isDefault }">
          <text v-if="form.isDefault"><text class="luc luc-check"></text></text>
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
import { addressApi } from '@/api'

export default {
  data() {
    return {
      form: {
        id: null,
        receiver: '',
        phone: '',
        country: 'US',
        province: '',
        city: '',
        district: '',
        detail: '',
        zipCode: '',
        tag: '',
        isDefault: false,
      },
      countries: ['US', 'CA', 'GB', 'DE', 'FR', 'ES', 'IT', 'NL', 'AU', 'JP'],
      tags: ['HOME', 'COMPANY', 'OTHER'],
    }
  },

  async onLoad(query) {
    if (query.id) {
      try {
        const addr = await addressApi.getAddressDetail(query.id)
        if (addr) this.form = { ...addr }
      } catch (e) {
        console.warn('[address-edit] load failed', e)
      }
    }
  },

  methods: {
    onCountryChange(e) {
      this.form.country = this.countries[e.detail.value]
    },

    onTagChange(e) {
      this.form.tag = this.tags[e.detail.value]
    },

    async onSave() {
      const required = ['receiver', 'phone', 'detail', 'city']
      for (const key of required) {
        if (!this.form[key]) {
          uni.showToast({ title: 'Please fill required fields', icon: 'none' })
          return
        }
      }
      try {
        if (this.form.id) {
          await addressApi.updateAddress(this.form.id, this.form)
        } else {
          await addressApi.createAddress(this.form)
        }
        uni.showToast({ title: 'Saved', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.showToast({ title: 'Save failed', icon: 'none' })
      }
    },
  },
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
