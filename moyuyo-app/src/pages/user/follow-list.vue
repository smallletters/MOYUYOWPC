<template>
  <view class="page">
    <!-- 搜索栏(默认折叠,点击右上角图标展开) -->
    <view v-if="showSearch" class="search-bar">
      <view class="search-input-wrap">
        <text class="luc luc-search search-icon" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索昵称、简介或ID…"
          confirm-type="search"
          :focus="true"
          @confirm="onSearchConfirm"
        >
        <text v-if="keyword" class="luc luc-x clear-btn" @tap="clearKeyword" />
      </view>
    </view>

    <!-- Tab 切换 -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: mode === t.value }"
        @tap="onTabChange(t.value)"
      >
        <text class="tab-text" :class="{ 'tab-active-text': mode === t.value }">
          {{ t.label }}
          <text v-if="total > 0" class="tab-count">{{ total }}</text>
        </text>
        <view v-if="mode === t.value" class="tab-indicator" />
      </view>
      <!-- 搜索触发按钮:与 tab 并列吸顶,展开后变为关闭图标 -->
      <view class="tab-search" @tap="onToggleSearch">
        <text class="luc" :class="showSearch ? 'luc-x' : 'luc-search'" />
      </view>
    </view>

    <!-- 列表 -->
    <scroll-view
      scroll-y
      class="list"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @scrolltolower="onLoadMore"
      @refresherrefresh="onRefresh"
    >
      <!-- 搜索结果摘要(只有关键词时显示) -->
      <view v-if="searchSummary" class="search-summary">
        <text class="search-summary-text">
          匹配 {{ searchSummary.matched }} / 共 {{ searchSummary.total }}
        </text>
      </view>

      <!-- 加载中 -->
      <view v-if="loading && !filteredList.length" class="status">
        <text class="status-text">加载中…</text>
      </view>

      <!-- 空结果 -->
      <view v-else-if="!filteredList.length && !loading" class="empty">
        <text class="empty-emoji">
          {{ isSearching ? '🔍' : mode === 'following' ? '🐾' : '💌' }}
        </text>
        <text class="empty-title">{{ emptyHint }}</text>
        <!-- 搜索无结果:给一个「清空搜索」按钮 -->
        <text v-if="isSearching" class="empty-action" @tap="onClearSearchFromEmpty">清空搜索</text>
        <!-- 非搜索空态(关注/粉丝列表本身为空)时,只有 following 给引导按钮 -->
        <text v-else-if="mode === 'following'" class="empty-action" @tap="goDiscover">
          去发现感兴趣的人
        </text>
      </view>

      <!-- 列表项 -->
      <view
        v-for="u in filteredList"
        :key="u.followId || u.userId || u.id"
        class="user-card"
        @tap="goProfile(u)"
        @longpress="onLongPress(u)"
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
          <view class="user-name-row">
            <text class="user-name">{{ u.nickname || '匿名用户' }}</text>
            <text v-if="u.mutualFollowed" class="mutual-badge">互相关注</text>
          </view>
          <text v-if="u.bio" class="user-bio">{{ u.bio }}</text>
          <text v-else class="user-bio user-bio--placeholder">
            {{
              mode === 'following'
                ? formatRelativeTime(u.createdAt) + ' 关注了TA'
                : formatRelativeTime(u.createdAt) + ' 关注了你'
            }}
          </text>
        </view>

        <!-- 操作按钮 -->
        <view v-if="mode === 'following'" class="btn btn-unfollow" @tap.stop="onUnfollow(u)">
          <text class="btn-text">已关注</text>
        </view>
        <view v-else-if="!u.followed" class="btn btn-follow" @tap.stop="onFollow(u)">
          <text class="btn-text-white">回关</text>
        </view>
        <view v-else class="btn btn-mutual" @tap.stop="onChat(u)">
          <text class="btn-text-white">发消息</text>
        </view>
      </view>

      <!-- 加载更多 / 到底 -->
      <view v-if="loading && filteredList.length" class="status">
        <text class="status-text">加载中…</text>
      </view>
      <view v-if="!loading && filteredList.length && noMore" class="status">
        <text class="status-text">— 没有更多了 —</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { followApi } from '@/api/follow'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userFollowList')

const tabs = [
  { value: 'following', label: '关注' },
  { value: 'followers', label: '粉丝' },
]

// 从 query.mode 读取初始 tab;uniapp vue3 setup 阶段 options 可能未注入,这里兜底读 onLoad
function resolveInitialMode() {
  try {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const cur = pages[pages.length - 1]
    const q = (cur && cur.options) || {}
    return q.mode === 'followers' ? 'followers' : 'following'
  } catch (e) {
    return 'following'
  }
}

const mode = ref(resolveInitialMode())
const list = ref([])
const loading = ref(false)
const refreshing = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)

// 搜索
const showSearch = ref(false)
const keyword = ref('')

/**
 * 搜索过滤逻辑
 * 匹配规则:
 *   - 关键词先 trim + 小写化;全空白视作空
 *   - 命中规则(任一即可):
 *     1. 昵称包含关键词(中文/英文都走 includes,中文 toLowerCase 无意义但不影响)
 *     2. 用户简介 bio 包含关键词
 *     3. 用户 ID(targetId/userId)包含关键词(纯数字搜索)
 *   - 输入关键词里若包含空白,自动把两边空白压缩后比较,避免「空格干扰命中」
 * 注意:仅在客户端已加载的列表中过滤(不会调后端搜索接口),
 *       已分页加载的本地全量才参与匹配。
 */
const filteredList = computed(() => {
  const q = (keyword.value || '').trim().toLowerCase()
  if (!q) return list.value
  // 把内部多余空白去掉,避免「空格」干扰中英文昵称命中
  const qClean = q.replace(/\s+/g, '')
  return list.value.filter((u) => {
    const nick = (u.nickname || '').toLowerCase()
    const bio = (u.bio || '').toLowerCase()
    const nickClean = nick.replace(/\s+/g, '')
    const uid = String(u.targetId || u.userId || u.id || '')
    if (nick.includes(q)) return true
    if (nickClean && qClean && nickClean.includes(qClean)) return true
    if (bio.includes(q)) return true
    if (uid && uid.includes(q)) return true
    return false
  })
})

/** 是否处于「搜索中」状态(有关键词) */
const isSearching = computed(() => (keyword.value || '').trim().length > 0)

/** 搜索结果摘要:「匹配 X / 共 Y」,无关键词时为 null(UI 隐藏) */
const searchSummary = computed(() => {
  if (!isSearching.value) return null
  return { matched: filteredList.value.length, total: list.value.length }
})

/**
 * 空态文案分两种场景:
 *   - 未搜索时:根据 mode 提示「还没有关注 / 还没有粉丝」
 *   - 搜索无结果:提示「未找到匹配的用户」+ 「清空搜索」操作
 */
const emptyHint = computed(() => {
  if (isSearching.value) return '未找到匹配的用户'
  if (mode.value === 'following') return '还没有关注任何人'
  return '还没有粉丝'
})

// 在 setup 顶层注册 onLoad 钩子(uniapp vue3 setup 语法)
onMounted(() => {
  // 兜底:onMounted 时 page options 通常已就绪,如果还没读取到 query,再读一次
  const m = resolveInitialMode()
  if (mode.value !== m) mode.value = m
  loadList(true)
})

// 监听 pageshow:用户从其他页返回时刷新关注状态(如取消关注后回退)
function onShowHook() {
  if (list.value.length > 0) {
    loadList(true)
  }
}

// 兜底:在某些端 onShow 通过 onUnmounted 模拟
onUnmounted(() => {})

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
    const rows = Array.isArray(res?.records) ? res.records : Array.isArray(res) ? res : []
    list.value.push(...rows)
    // 优先 total;否则按行数估算
    const t = Number(res?.total)
    if (Number.isFinite(t) && t >= 0) {
      total.value = t
      noMore.value = list.value.length >= t
    } else {
      noMore.value = rows.length < pageSize
      total.value = list.value.length
    }
    if (rows.length > 0) page.value += 1
  } catch (e) {
    console.warn('[follow-list] load error', e)
  } finally {
    loading.value = false
    if (refreshing.value) {
      refreshing.value = false
      uni.stopPullDownRefresh()
    }
  }
}

function onTabChange(v) {
  if (mode.value === v) return
  mode.value = v
  // 切 tab 时清掉搜索词
  keyword.value = ''
  loadList(true)
}

function onLoadMore() {
  if (loading.value || noMore.value) return
  loadList(false)
}

/** 下拉刷新 */
function onRefresh() {
  refreshing.value = true
  loadList(true)
}

/** 切换搜索栏 */
function onToggleSearch() {
  showSearch.value = !showSearch.value
  if (!showSearch.value) keyword.value = ''
}

/** 触发 computed 重算(trim 一下) */
function onSearchConfirm() {
  keyword.value = (keyword.value || '').trim()
}

/** 清空搜索词(v-model 自动驱动 filteredList 重算) */
function clearKeyword() {
  keyword.value = ''
}

/** 空态下点「清空搜索」按钮 */
function onClearSearchFromEmpty() {
  clearKeyword()
  // 焦点回到输入框,方便继续输入
  showSearch.value = true
}

async function onUnfollow(u) {
  uni.showModal({
    title: '取消关注',
    content: `确认取消关注「${u.nickname}」?`,
    confirmText: '取消关注',
    cancelText: '再想想',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await followApi.unfollow(u.targetId || u.userId)
        list.value = list.value.filter((x) => (x.targetId || x.userId) !== (u.targetId || u.userId))
        total.value = Math.max(0, total.value - 1)
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
    // 关注成功:列表项标记为已互关(更新视图)
    const idx = list.value.findIndex((x) => (x.userId || x.id) === (u.userId || u.id))
    if (idx >= 0) {
      list.value[idx].followed = true
    }
    uni.showToast({ title: '已关注', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function onChat(u) {
  // 跳到 DM 聊天页(已注册: pages/chat/dm-chat)
  uni.navigateTo({ url: `/pages/chat/dm-chat?targetId=${u.userId || u.id}` })
}

/** 长按:弹操作菜单(主流 APP 通用模式) */
function onLongPress(u) {
  uni.showActionSheet({
    itemList:
      mode.value === 'following'
        ? ['取消关注', '设置分组', '推荐给朋友']
        : ['回关', '拉黑', '推荐给朋友'],
    success: async (res) => {
      if (mode.value === 'following') {
        if (res.tapIndex === 0) onUnfollow(u)
        else if (res.tapIndex === 1) {
          uni.showToast({ title: '分组功能即将上线', icon: 'none' })
        } else if (res.tapIndex === 2) {
          uni.setClipboardData({
            data: `@${u.nickname}`,
            success: () => uni.showToast({ title: '昵称已复制', icon: 'none' }),
          })
        }
      } else {
        if (res.tapIndex === 0) onFollow(u)
        else if (res.tapIndex === 1) {
          uni.showToast({ title: '拉黑功能即将上线', icon: 'none' })
        } else if (res.tapIndex === 2) {
          uni.setClipboardData({
            data: `@${u.nickname}`,
            success: () => uni.showToast({ title: '昵称已复制', icon: 'none' }),
          })
        }
      }
    },
  })
}

/** 跳到用户主页(本项目无独立 profile 页时,跳到 search 页用昵称搜,主流 APP 兜底策略) */
function goProfile(u) {
  const id = u.targetId || u.userId || u.id
  const name = u.nickname
  // 优先 id 跳转,无 id 时按昵称走搜索
  if (id) {
    uni.navigateTo({ url: `/pages/user/profile?id=${id}&name=${encodeURIComponent(name || '')}` })
  } else if (name) {
    uni.navigateTo({ url: `/pages/community/search?keyword=${encodeURIComponent(name)}` })
  }
}

function goDiscover() {
  // 跳到社区首页(发现页)寻找新用户
  uni.switchTab({ url: '/pages/tabbar/community' })
}

function avatarChar(name) {
  if (!name) return 'P'
  return String(name).substring(0, 1).toUpperCase()
}

/** 相对时间格式:刚刚 / X分钟前 / X小时前 / X天前 / YYYY-MM-DD */
function formatRelativeTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const now = new Date()
    const diff = Math.floor((now - d) / 1000)
    if (diff < 60) return '刚刚'
    if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
    if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
    if (diff < 7 * 86400) return `${Math.floor(diff / 86400)}天前`
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return `${yyyy}-${mm}-${dd}`
  } catch {
    return ''
  }
}

function onImgError(e) {
  const target = e?.target
  if (target && !target.src?.startsWith('data:')) {
    target.src =
      'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1" fill="%23f2f2f7"/></svg>'
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}

/* 搜索栏 */
.search-bar {
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: var(--color-background);
  border-radius: 999rpx;
  padding: 12rpx 20rpx;
}
.search-icon {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
.search-input {
  flex: 1;
  font-size: 26rpx;
  background: transparent;
}
.clear-btn {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
  padding: 0 8rpx;
}

/* Tab */
.tabs {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}
.tab {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.tab-text {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
  font-weight: 500;
}
.tab-active-text {
  color: var(--color-primary);
  font-weight: 600;
}
.tab-count {
  font-size: 22rpx;
  margin-left: 6rpx;
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-normal, 400);
}
.tab-active-text .tab-count {
  color: var(--color-primary);
  opacity: 0.7;
}
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
.tab-search {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 36rpx;
  color: var(--color-text-secondary);
}

/* 列表 */
.list {
  flex: 1;
  padding: 16rpx;
}
.status {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}

/* 搜索结果摘要 */
.search-summary {
  padding: 8rpx 8rpx 16rpx;
}
.search-summary-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 空态 */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 32rpx;
  gap: 16rpx;
}
.empty-emoji {
  font-size: 96rpx;
  margin-bottom: 8rpx;
}
.empty-title {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}
.empty-action {
  margin-top: 16rpx;
  padding: 16rpx 48rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 26rpx;
  border-radius: 999rpx;
  font-weight: 500;
}

/* 用户卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx;
  background: var(--color-surface);
  border-radius: 16rpx;
  margin-bottom: 12rpx;
  transition:
    background 0.15s ease,
    transform 0.15s ease;
}
.user-card:active {
  background: var(--color-background);
  transform: scale(0.99);
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
.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  overflow: hidden;
}
.user-name-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.user-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
  max-width: 280rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mutual-badge {
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  background: rgba(219, 201, 138, 0.18);
  color: var(--color-primary-dark, #b08c2a);
  border-radius: 4rpx;
  font-weight: 500;
}
.user-bio {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-bio--placeholder {
  color: var(--color-text-tertiary);
}

/* 操作按钮 */
.btn {
  flex-shrink: 0;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 500;
  text-align: center;
  min-width: 120rpx;
}
.btn-unfollow {
  border: 1rpx solid var(--color-divider);
  background: var(--color-background);
}
.btn-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}
.btn-follow {
  background: var(--color-primary);
}
.btn-text-white {
  font-size: 24rpx;
  color: #fff;
}
.btn-mutual {
  background: transparent;
  border: 1rpx solid var(--color-primary);
}
.btn-mutual .btn-text-white {
  color: var(--color-primary);
}
</style>
