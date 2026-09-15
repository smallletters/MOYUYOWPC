<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="nav-title">{{ $t('userProfilePage.title') }}</text>
      <view class="nav-placeholder" />
    </view>

    <view v-if="loading" class="loading">
      <text class="loading-text">{{ $t('userProfilePage.loading') }}</text>
    </view>
    <view v-else-if="!profile" class="empty">
      <text class="empty-text">{{ $t('userProfilePage.notFound') }}</text>
    </view>
    <view v-else class="profile">
      <view class="header">
        <image
          v-if="profile.avatar"
          :src="profile.avatar"
          class="avatar"
          mode="aspectFill" />
        <view v-else class="avatar avatar-text">{{ (profile.nickname || 'U')[0] }}</view>
        <view class="info">
          <text class="nick">{{ profile.nickname || $t('userProfilePage.defaultNickname') }}</text>
          <text v-if="profile.country || profile.gender" class="meta">
            {{ profile.country || '' }} {{ profile.gender ? '· ' + profile.gender : '' }}
          </text>
          <text v-if="profile.bio" class="bio">{{ profile.bio }}</text>
        </view>
      </view>

      <view class="stats">
        <view class="stat">
          <text class="stat-num">{{ profile.following || 0 }}</text>
          <text class="stat-label">{{ $t('userProfilePage.follow') }}</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ profile.followers || 0 }}</text>
          <text class="stat-label">{{ $t('userProfilePage.followers') }}</text>
        </view>
        <view class="stat">
          <text class="stat-num">{{ profile.points || 0 }}</text>
          <text class="stat-label">{{ $t('userProfilePage.points') }}</text>
        </view>
      </view>

      <view class="actions">
        <view class="btn primary" @tap="toggleFollow">
          <text class="btn-text">
            {{ following ? $t('userProfilePage.followed') : $t('userProfilePage.follow') }}
          </text>
        </view>
        <view class="btn" @tap="chat">
          <text class="btn-text">{{ $t('userProfilePage.message') }}</text>
        </view>
        <view class="btn danger" @tap="blockUser">
          <text class="btn-text">{{ $t('userProfilePage.block') }}</text>
        </view>
      </view>

      <view class="section">
        <text class="section-title">{{ $t('userProfilePage.pointsDetailPlaceholder') }}</text>
        <text class="section-hint">{{ $t('userProfilePage.sectionHint') }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { followApi, blockApi } from '@/api'
import { i18n } from '@/i18n'

const loading = ref(false)
const profile = ref(null)
const following = ref(false)
const targetUserId = ref(null)

async function loadProfile(id) {
  loading.value = true
  try {
    profile.value = await followApi.userProfile(id)
    const status = await followApi.followStatus(id)
    following.value = !!status?.following
  } catch (e) {
    console.warn('[user-profile] load failed', e)
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  const id = targetUserId.value
  if (!id) return
  try {
    if (following.value) {
      await followApi.unfollow(id)
      following.value = false
    } else {
      await followApi.follow(id)
      following.value = true
    }
    uni.showToast({
      title: following.value
        ? i18n.t('userProfilePage.followed')
        : i18n.t('userProfilePage.unfollowed'),
      icon: 'none',
    })
  } catch (e) {
    uni.showToast({ title: i18n.t('userProfilePage.operationFailed'), icon: 'none' })
  }
}

function chat() {
  const u = profile.value
  uni.navigateTo({
    url: `/pages/community/dm-chat?id=${targetUserId.value}&name=${encodeURIComponent(u?.nickname || '')}&avatar=${encodeURIComponent((u?.nickname || 'U')[0])}`,
  })
}

async function blockUser() {
  const id = targetUserId.value
  if (!id) return
  uni.showModal({
    title: i18n.t('userProfilePage.blockConfirmTitle'),
    content: i18n.t('userProfilePage.blockConfirmContent'),
    success: async (res) => {
      if (res.confirm) {
        try {
          await blockApi.blockUser(id)
          uni.showToast({ title: i18n.t('userProfilePage.blocked'), icon: 'none' })
        } catch (e) {
          uni.showToast({ title: i18n.t('userProfilePage.operationFailed'), icon: 'none' })
        }
      }
    },
  })
}

function goBack() {
  uni.navigateBack()
}

onMounted(() => {
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const q = cur?.options || {}
    if (q.id) {
      targetUserId.value = q.id
      loadProfile(q.id)
    }
  } catch (e) {
    /* ignore */
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
}
.nav-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.nav-back {
  width: 60rpx;
}
.back-icon {
  font-size: 44rpx;
  color: var(--color-primary);
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
.nav-placeholder {
  width: 60rpx;
}
.loading,
.empty {
  padding: 80rpx 24rpx;
  text-align: center;
}
.loading-text,
.empty-text {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
.profile {
  padding: 24rpx;
}
.header {
  display: flex;
  gap: 20rpx;
  align-items: center;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 20rpx;
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--color-primary);
}
.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 48rpx;
  font-weight: 600;
}
.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.nick {
  font-size: 32rpx;
  font-weight: 600;
}
.meta {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.bio {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-top: 8rpx;
}
.stats {
  display: flex;
  justify-content: space-around;
  margin-top: 16rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 20rpx;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}
.stat-num {
  font-size: 32rpx;
  font-weight: 600;
}
.stat-label {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.actions {
  display: flex;
  gap: 12rpx;
  margin-top: 24rpx;
}
.btn {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid var(--color-divider);
}
.btn.primary {
  background: var(--color-primary);
  border-color: transparent;
}
.btn.danger {
  background: #ffecec;
  border-color: transparent;
}
.btn-text {
  font-size: 26rpx;
  color: var(--color-text);
}
.btn.primary .btn-text {
  color: #fff;
}
.btn.danger .btn-text {
  color: #c0392b;
}
.section {
  margin-top: 24rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: 20rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
}
.section-hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
