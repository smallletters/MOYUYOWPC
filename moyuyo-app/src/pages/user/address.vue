<template>
  <view class="address">
    <view v-if="!fromCheckout" class="header-bar">
      <text class="title">Shipping Address</text>
      <view class="btn btn-primary" @click="goEdit(null)">+ Add</view>
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
            <text class="name">{{ addr.first_name }} {{ addr.last_name }}</text>
            <text class="phone">{{ addr.phone }}</text>
            <view v-if="addr.isDefault" class="default-tag">Default</view>
          </view>
          <text class="detail">{{ addr.country }} {{ addr.state }} {{ addr.city }} {{ addr.address_1 }} {{ addr.address_2 }} {{ addr.postcode }}</text>
        </view>
        <view v-if="!fromCheckout" class="actions">
          <text @click.stop="onSetDefault(addr)" v-if="!addr.isDefault">Set Default</text>
          <text @click.stop="goEdit(addr)">Edit</text>
          <text class="delete" @click.stop="onDelete(addr)">Delete</text>
        </view>
      </view>

      <view v-if="addressList.length === 0" class="empty">
        <text>No addresses yet</text>
        <view class="btn btn-primary" @click="goEdit(null)">Add Address</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

export default {
  data() {
    return {
      addressList: getStorage(STORAGE_KEYS.ADDRESS_LIST, []),
      selectedId: '',
      fromCheckout: false
    }
  },

  onLoad(query) {
    this.fromCheckout = query.from === 'checkout'
  },

  methods: {
    onSelect(addr) {
      if (this.fromCheckout) {
        // 把选中的地址回传给 checkout（通过 globalData + setStorage）
        setStorage('moyuyo_selected_address', addr)
        uni.navigateBack()
      } else {
        this.selectedId = addr.id
      }
    },

    onSetDefault(addr) {
      this.addressList.forEach((a) => (a.isDefault = a.id === addr.id))
      this.persist()
      uni.showToast({ title: 'Default updated', icon: 'success' })
    },

    goEdit(addr) {
      const url = addr
        ? `/pages/user/address-edit?id=${addr.id}`
        : '/pages/user/address-edit'
      uni.navigateTo({ url })
    },

    onDelete(addr) {
      uni.showModal({
        title: 'Delete address?',
        success: (res) => {
          if (res.confirm) {
            this.addressList = this.addressList.filter((a) => a.id !== addr.id)
            this.persist()
          }
        }
      })
    },

    persist() {
      setStorage(STORAGE_KEYS.ADDRESS_LIST, this.addressList)
    }
  }
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
</style>
