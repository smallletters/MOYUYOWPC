<template>
  <view class="profile">
    <view class="avatar-section">
      <image :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar" />
      <text class="name">{{ userStore.userInfo?.nickname || 'User' }}</text>
      <text class="email">{{ userStore.userInfo?.email }}</text>
      <view class="btn btn-outline change-avatar">Change Avatar</view>
    </view>

    <view class="card">
      <view class="form-row">
        <text class="label">Nickname</text>
        <input v-model="form.nickname" class="input">
      </view>
      <view class="form-row">
        <text class="label">Email</text>
        <text class="readonly">{{ userStore.userInfo?.email }}</text>
      </view>
      <view class="form-row">
        <text class="label">Phone</text>
        <input v-model="form.phone" class="input" type="number">
      </view>
      <view class="form-row">
        <text class="label">Birthday</text>
        <picker
          mode="date"
          :value="form.birthday"
          :end="today"
          @change="form.birthday = $event.detail.value"
        >
          <view class="input picker">{{ form.birthday || 'Select' }}</view>
        </picker>
      </view>
    </view>

    <view class="btn btn-primary save-btn" @click="onSave">Save Changes</view>

    <view class="btn btn-outline logout-btn" @click="onLogout">Sign Out</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      defaultAvatar: 'https://i.pravatar.cc/200?img=20',
      form: {
        nickname: '',
        phone: '',
        birthday: '',
      },
    }
  },

  computed: {
    today() {
      return new Date().toISOString().split('T')[0]
    },
    userStore() {
      return useUserStore()
    },
  },

  onLoad() {
    if (this.userStore.userInfo) {
      this.form.nickname = this.userStore.userInfo.nickname || ''
      this.form.phone = this.userStore.userInfo.phone || ''
      this.form.birthday = this.userStore.userInfo.birthday || ''
    }
  },

  methods: {
    async onSave() {
      try {
        await this.userStore.updateProfile(this.form)
        uni.showToast({ title: 'Saved', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: 'Save failed', icon: 'none' })
      }
    },

    onLogout() {
      uni.showModal({
        title: 'Sign out?',
        success: async (res) => {
          if (res.confirm) {
            await this.userStore.logout()
            uni.reLaunch({ url: '/pages/tabbar/user' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.profile {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 24rpx;
  background: var(--color-surface);
}

.avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: var(--color-background);
  margin-bottom: 16rpx;
}

.name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.email {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.change-avatar {
  margin-top: 24rpx;
  padding: 12rpx 32rpx;
  font-size: var(--font-size-sm);
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin: 16rpx;
  padding: 0 24rpx;
}

.form-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.form-row:last-child {
  border-bottom: none;
}

.label {
  width: 200rpx;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.input {
  flex: 1;
  font-size: var(--font-size-base);
  text-align: right;
}

.readonly {
  flex: 1;
  text-align: right;
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.picker {
  min-height: 44rpx;
}

.save-btn,
.logout-btn {
  margin: 16rpx 24rpx;
  padding: 24rpx 0;
  font-size: var(--font-size-md);
}

.logout-btn {
  color: var(--color-danger);
  border-color: var(--color-danger);
}
</style>
