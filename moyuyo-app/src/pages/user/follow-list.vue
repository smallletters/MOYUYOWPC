<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
      <text class="title">{{ mode === 'following' ? '我的关注' : '我的粉丝' }}</text>
      <view class="nav-placeholder" />
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: mode === t.value }"
        @tap="onTabChange(t.value)"
      >
        <text class="tab-text" :class="{ 'tab-active-text': mode === t.value }">{{ t.label }}</text>
        <view v-if="mode === t.value" class="tab-indicator" />
      </view>
    </view>

    <scroll-view scroll-y class="list" @scrolltolower="onLoadMore">
      <view v-if="loading && !list.length" class="status">
        <text class="status-text">加载中…</text>
      </view>
      <view v-else-if="!list.length" class="status">
        <text class="status-text">{{ emptyHint }}</text>
      </view>

      <view
        v-for="u in list"
        :key="u.followId"
        class="user-card"
        @tap="goProfile(u)"
      >
        <image
          v-if="u.avatar"
          :src="u.avatar"
          class="avatar"
          mode="aspectFill"
          lazy-load
          @error="onImgError"
        />
        <view v-else class="avatar avatar-fallback">{{ avatarChar(u.nickname) }}</view>
        <view class="user-info">
          <text class="user-name">{{ u.nickname || '匿名用户' }}</text>
          <text class="user-time">{{ formatTime(u.createdAt) }} {{ mode === 'following' ? '关注' : '关注了你' }}</text>
        </view>
        <view
          v-if="mode === 'following'"
          class="btn-unfollow"
          @tap.stop="onUnfollow(u)"
        >
          <text class="btn-text">已关注</text>
        </view>
        <view
          v-else
          class="btn-follow"
          @tap.stop="onFollow(u)"
        >
          <text class="btn-text-white">回关</text>
        </view>
      </view>

      <view v-if="!loading && list.length && noMore" class="status">
        <text class="status-text">— 没有更多了 —</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { followApi } from '@/api/follow'

const props = defineProps({
  initialMode: { type: String, default: 'following' },
})

const tabs = [
  { value: 'following', label: '关注' },
  { value: 'followers', label: '粉丝' },
]
const mode = ref(props.initialMode === 'followers' ? 'followers' : 'following')
const list = ref([])
const loading = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20

const emptyHint = computed(() => {
  if (mode.value === 'following') return '还没有关注任何人，去发现感兴趣的用户吧～'
  return '还没有粉丝，邀请好友来互动吧～'
})

async function loadList(reset = false) {
  if (reset) {
    page.value = 1
    noMore.value = false
    list.value = []
  }
  loading.value = true
  try {
    const fn = mode.value === 'following' ? followApi.listFollowing : followApi.listFollowers
    const res = await fn({ page: page.value, size: pageSize })
    const rows = Array.isArray(res) ? res : (res?.records || [])
    list.value.push(...rows)
    noMore.value = rows.length < pageSize
    page.value += 1
  } catch (e) {
    console.warn('[follow-list] load error', e)
  } finally {
    loading.value = false
  }
}

function onTabChange(v) {
  if (mode.value === v) return
  mode.value = v
  loadList(true)
}

function onLoadMore() {
  if (loading.value || noMore.value) return
  loadList(false)
}

async function onUnfollow(u) {
  uni.showModal({
    title: '取消关注',
    content: `确认取消关注「${u.nickname}」?`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await followApi.unfollow(u.targetId)
        list.value = list.value.filter((x) => x.targetId !== u.targetId)
        uni.showToast({ title: '已取消关注', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '操作失败', icon: 'none' })
      }
    },
  })
}

async function onFollow(u) {
  try {
    await followApi.follow(u.userId)
    list.value = list.value.filter((x) => x.userId !== u.userId)
    uni.showToast({ title: '已回关', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function goProfile(u) {
  const id = u.targetId || u.userId
  if (!id) return
  uni.navigateTo({ url: `/pages/user/user-profile-page?id=${id}` })
}

function goBack() {
  uni.navigateBack()
}

function avatarChar(name) {
  if (!name) return 'P'
  return name.substring(0, 1).toUpperCase()
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  } catch { return '' }
}

function onImgError(e) {
  const target = e?.target
  if (target && !target.src?.startsWith('data:')) {
    target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" fill="%23f2f2f7"/></svg>'
  }
}

onMounted(() => loadList(true))
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}
.header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: var(--color-text); }
.nav-placeholder { width: 60rpx; }

.tabs {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.tab {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.tab-text { font-size: 28rpx; color: var(--color-text-tertiary); font-weight: 500; }
.tab-active-text { color: var(--color-primary); font-weight: 600; }
.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-primary);
}

.list { flex: 1; padding: 16rpx; }
.status {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}
.status-text { font-size: 26rpx; color: var(--color-text-tertiary); }

.user-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
  margin-bottom: 12rpx;
}
.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.avatar-fallback {
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 600;
}
.user-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.user-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
}
.user-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.btn-unfollow {
  padding: 12rpx 24rpx;
  border: 1rpx solid var(--color-divider);
  border-radius: 999rpx;
  background: var(--color-background-200, #f2f2f7);
}
.btn-text { font-size: 24rpx; color: var(--color-text-secondary); }
.btn-follow {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
}
.btn-text-white { font-size: 24rpx; color: #fff; }
</style>