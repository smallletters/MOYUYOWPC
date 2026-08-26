<template>
  <view class="address">
    <view v-if="!fromCheckout" class="header-bar">
      <view class="header-back" @click="goBack" aria-label="返回"><text class="luc luc-arrow-left"></text></view>
      <text class="title">收货地址</text>
      <view class="btn btn-primary" @click="goEdit(null)">+ 新增收货地址</view>
    </view>

    <scroll-view scroll-y class="list">
      <view
        v-for="addr in addressList"
        :key="addr.id"
        class="card address-card"
        :class="{ active: selectedId === addr.id }"
        @click="onSelect(addr)"
      >
        <view class="card-body">
          <view class="name-row">
            <text class="name">{{ addr.receiver }}</text>
            <text class="phone">{{ addr.phone }}</text>
            <view v-if="addr.isDefault" class="default-tag">默认</view>
          </view>
          <text class="detail">
            {{ addr.country }} {{ addr.province }} {{ addr.city }} {{ addr.detail }}
          </text>
          <text v-if="addr.zipCode" class="zip">邮编: {{ addr.zipCode }}</text>
        </view>
        <view v-if="!fromCheckout" class="actions">
          <text v-if="!addr.isDefault" @click.stop="onSetDefault(addr)">设为默认</text>
          <text @click.stop="goEdit(addr)">编辑</text>
          <text class="delete" @click.stop="onDelete(addr)">删除</text>
        </view>
      </view>

      <view v-if="loading" class="loading">Loading...</view>
      <view v-if="!loading && addressList.length === 0" class="empty">
        <text>No addresses yet</text>
        <view class="btn btn-primary" @click="goEdit(null)">Add Address</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { addressApi } from '@/api'

export default {
  data() {
    return {
      addressList: [],
      selectedId: '',
      fromCheckout: false,
      loading: false,
    }
  },

  onLoad(query) {
    this.fromCheckout = query.from === 'checkout'
    this.loadAddresses()
  },

  methods: {
    async loadAddresses() {
      this.loading = true
      try {
        this.addressList = await addressApi.getAddressList()
      } catch (e) {
        console.warn('[address] load failed', e)
        this.addressList = []
      } finally {
        this.loading = false
      }
    },

    async onSelect(addr) {
      if (this.fromCheckout) {
        uni.setStorageSync('moyuyo_selected_address', addr)
        uni.navigateBack()
      } else {
        this.selectedId = addr.id
      }
    },

    async onSetDefault(addr) {
      try {
        await addressApi.setDefaultAddress(addr.id)
        this.addressList.forEach((a) => (a.isDefault = a.id === addr.id))
        uni.showToast({ title: 'Default updated', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: 'Failed', icon: 'none' })
      }
    },

    goEdit(addr) {
      const url = addr ? `/pages/user/address-edit?id=${addr.id}` : '/pages/user/address-edit'
      uni.navigateTo({ url })
    },

    async onDelete(addr) {
      uni.showModal({
        title: 'Delete address?',
        success: async (res) => {
          if (res.confirm) {
            try {
              await addressApi.deleteAddress(addr.id)
              this.addressList = this.addressList.filter((a) => a.id !== addr.id)
              uni.showToast({ title: 'Deleted', icon: 'success' })
            } catch (e) {
              uni.showToast({ title: 'Delete failed', icon: 'none' })
            }
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.address {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: var(--color-surface);
}

.title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.list {
  flex: 1;
  padding: 16rpx;
}

.address-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.address-card.active {
  border: 2rpx solid var(--color-primary);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.phone {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.default-tag {
  padding: 4rpx 12rpx;
  background: var(--color-primary);
  color: var(--color-text);
  font-size: 20rpx;
  border-radius: var(--radius-pill);
}

.detail {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.zip {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.actions {
  display: flex;
  gap: 32rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
  font-size: var(--font-size-sm);
  color: var(--color-primary-dark);
}

.actions .delete {
  color: var(--color-danger);
}

.empty {
  text-align: center;
  padding: 96rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  color: var(--color-text-tertiary);
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}
</style>
