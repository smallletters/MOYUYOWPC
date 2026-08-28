<template>
  <view class="community-page">
    <!-- 顶部导航栏（状态栏 + 标题 + 操作图标） -->
    <view class="status-bar">
      <text class="status-time">9:41</text>
      <view class="status-icons">
        <text class="status-icon">▲</text>
        <text class="status-icon">▣</text>
        <view class="battery" />
      </view>
    </view>

    <view class="top-bar">
      <text class="top-title">社区</text>
      <view class="top-actions">
        <view class="icon-btn" aria-label="搜索" @tap="goSearch">
          <text class="icon"><text class="luc luc-search" /></text>
        </view>
        <view class="icon-btn relative" aria-label="通知" @tap="goNotifications">
          <text class="icon"><text class="luc luc-bell" /></text>
          <view class="badge-dot" />
        </view>
      </view>
    </view>

    <!-- Tab：推荐 / 关注 / 话题 -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @tap="onTabChange(t.value)"
      >
        <text class="tab-text" :class="{ 'tab-text-active': activeTab === t.value }">
          {{ t.label }}
        </text>
        <view v-if="activeTab === t.value" class="tab-indicator" />
      </view>
    </view>

    <!-- 搜索条（仅在「推荐」Tab 显示） -->
    <view v-if="activeTab === 'recommend'" class="search-bar">
      <view class="search-field" @tap="goSearchPage">
        <text class="search-icon luc luc-search" />
        <text class="search-placeholder">搜索帖子、用户、话题…</text>
      </view>
    </view>

    <!-- 话题标签横向滚动（仅「推荐」Tab 显示） -->
    <scroll-view
      v-if="activeTab === 'recommend'"
      scroll-x
      class="topic-bar"
      :show-scrollbar="false"
    >
      <view
        v-for="t in topicTags"
        :key="t.id"
        class="topic-tag"
        @tap="onTopicClick(t)">
        #{{ t.name }}
      </view>
    </scroll-view>

    <!-- 帖子信息流 -->
    <scroll-view scroll-y class="feed" @scrolltolower="onLoadMore">
      <view v-if="loading && !posts.length" class="status">
        <text class="status-text">加载中…</text>
      </view>

      <view v-else-if="!posts.length" class="status">
        <text class="status-text">{{ emptyHint }}</text>
      </view>

      <view
        v-for="p in posts"
        :key="p.id"
        class="post-card"
        @tap="goDetail(p.id)">
        <!-- 用户信息行 -->
        <view class="post-header">
          <image
            v-if="p.avatar"
            :src="resolveImageUrl(p.avatar)"
            class="post-avatar"
            mode="aspectFill"
            @error="onImageError"
          />
          <view v-else class="post-avatar post-avatar-fallback">
            {{ avatarChar(p.username) }}
          </view>
          <view class="post-user">
            <text class="post-username">{{ p.username || 'Pet Lover' }}</text>
            <text class="post-time">{{ formatTime(p.createTime) }}</text>
          </view>
          <view class="more-btn" @tap.stop="onMore(p)">
            <text class="more-icon">⋯</text>
          </view>
        </view>

        <!-- 帖子图片:1张大图 / 2-9张九宫格(参考小红书) -->
        <view
          v-if="p.images && p.images.length"
          class="post-image-wrap"
          :class="['grid-' + getImageGridClass(p.images.length)]"
        >
          <image
            v-for="(img, idx) in p.images"
            :key="idx"
            :src="resolveImageUrl(img)"
            class="post-image"
            mode="aspectFill"
            lazy-load
            :show-menu-by-longpress="false"
            @error="onImageError"
            @tap="goDetail(p.id)"
          />
        </view>

        <!-- 帖子正文 -->
        <view class="post-content-wrap">
          <text class="post-content">{{ p.content }}</text>
        </view>

        <!-- 互动行：点赞 / 评论 / 分享 -->
        <view class="post-actions">
          <view class="action" :class="{ liked: p.liked }" @tap.stop="onLike(p)">
            <text class="action-icon luc" :class="$luc(p.liked ? 'heart' : 'heart')" />
            <text class="action-count">{{ p.likes || 0 }}</text>
          </view>
          <view class="action" @tap.stop="goDetail(p.id, true)">
            <text class="action-icon"><text class="luc luc-message-circle" /></text>
            <text class="action-count">{{ p.comments || 0 }}</text>
          </view>
          <view class="action" @tap.stop="onShare(p)">
            <text class="action-icon luc luc-external-link" />
          </view>
        </view>
      </view>

      <view v-if="!loading && posts.length && noMore" class="status">
        <text class="status-text">— 没有更多了 —</text>
      </view>
      <view v-if="loading && posts.length" class="status">
        <text class="status-text">加载中…</text>
      </view>
    </scroll-view>

    <!-- 浮动发布按钮 -->
    <view class="fab" aria-label="发布帖子" @tap="goCreate">
      <text class="fab-icon"><text class="luc luc-camera" /></text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { communityApi } from '@/api'
import { get } from '@/utils/request'

const tabs = [
  { value: 'recommend', label: '推荐' },
  { value: 'follow', label: '关注' },
  { value: 'topic', label: '话题' },
]
const activeTab = ref('recommend')

// 话题标签（来自后端 mo_community_topic_v2 表）
const topicTags = ref([])

// 帖子列表
const posts = ref([])
const loading = ref(false)
const noMore = ref(false)
const page = ref(1)
const pageSize = 20

/** 不同 Tab 的空态提示 */
const emptyHint = computed(() => {
  if (activeTab.value === 'follow') return '还没有关注的人，去发现感兴趣的用户吧～'
  if (activeTab.value === 'topic') return '选择上方话题标签查看帖子'
  return '还没有帖子，发一个吧～'
})

/**
 * 根据当前 Tab 拉取数据源。
 *  - recommend：GET /community/posts（带可选手 topic）
 *  - follow：    GET /follows/feed（关注的人发布的帖子）
 *  - topic：     默认等价 recommend，等待点击具体话题再过滤
 */
async function loadPosts(reset = false) {
  if (reset) {
    page.value = 1
    noMore.value = false
    posts.value = []
  }
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    let res
    if (activeTab.value === 'follow') {
      res = await communityApi.getFollowFeed(params)
    } else {
      res = await communityApi.getCommunityPosts(params)
    }
    const list = res?.records || res || []
    posts.value.push(...list)
    noMore.value = list.length < pageSize
    page.value += 1
  } catch (e) {
    console.warn('[community] load error', e)
  } finally {
    loading.value = false
  }
}

function onTabChange(value) {
  if (activeTab.value === value) return
  activeTab.value = value
  loadPosts(true)
}

function onTopicClick(t) {
  uni.navigateTo({
    url: `/pages/user/community-topic?id=${t.id}&name=${encodeURIComponent(t.name)}`,
  })
}

function onLoadMore() {
  if (loading.value || noMore.value) return
  loadPosts(false)
}

async function onLike(p) {
  try {
    if (p.liked) {
      await communityApi.unlikePost(p.id)
      p.liked = false
      p.likes = Math.max(0, (p.likes || 1) - 1)
    } else {
      await communityApi.likePost(p.id)
      p.liked = true
      p.likes = (p.likes || 0) + 1
    }
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function onShare(p) {
  uni.showToast({ title: '分享链接已复制', icon: 'none' })
}

function onMore(p) {
  uni.showActionSheet({
    itemList: ['不感兴趣', '举报'],
    success: (res) => {
      uni.showToast({ title: ['不感兴趣', '举报'][res.tapIndex] + ' 已提交', icon: 'none' })
    },
  })
}

function avatarChar(name) {
  if (!name) return 'P'
  return name.substring(0, 1).toUpperCase()
}

/**
 * 根据图片数量决定九宫格样式类:
 *  - 1 张:单列大图
 *  - 2-3 张:2 列
 *  - 4-9 张:3 列(标准九宫格,4 张时为 2x2,9 张时为 3x3)
 *  - 超过 9 张:截断 9 张后按 3 列展示
 */
function getImageGridClass(count) {
  if (count <= 1) return 'single'
  if (count <= 3) return 'col-2'
  return 'col-3'
}

/**
 * 帖子图片加载失败回退：替换为占位图占住卡片高度，避免布局抖动。
 * 真实生产中应上传到 CDN（file:// 路径在浏览器/小程序中会失败）。
 */
function onImageError(e) {
  const target = e?.target
  if (target && target.src && !target.src.includes('data:')) {
    target.src =
      'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 3"><rect width="4" height="3" fill="%23f2f2f7"/></svg>'
  }
}

/**
 * 解析帖子图片 URL：
 *  - http(s):// 开头直接返回
 *  - 设备本地路径（blob:、wxfile://、wxlocalfile://、file://、http://tmp/）返回占位图,
 *    因为发布时前端只传本地路径,后端原样存进数据库,再次读取浏览器无法加载
 *  - /uploads/... 等相对路径走 dev proxy / 同源(prod nginx) → 直接返回相对路径即可
 *  - 兜底:返回占位图
 */
const PLACEHOLDER_IMG =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 3"><rect width="4" height="3" fill="%23eaeef2"/><text x="2" y="1.7" font-size="0.4" text-anchor="middle" fill="%23888" font-family="sans-serif">图片</text></svg>'

function resolveImageUrl(url) {
  if (!url) return PLACEHOLDER_IMG
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  // 设备本地路径(发布时上传但未真实上传到服务器的情况)用占位图
  if (
    url.startsWith('blob:') ||
    url.startsWith('wxfile://') ||
    url.startsWith('wxlocalfile://') ||
    url.startsWith('file://') ||
    url.startsWith('data:') ||
    url.startsWith('http://tmp/')
  ) {
    return PLACEHOLDER_IMG
  }
  // 相对路径(/uploads/...) dev 用 vite proxy / prod nginx 同源 → 直接返回
  if (url.startsWith('/')) return url
  return PLACEHOLDER_IMG
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = Date.now()
  const diffMs = now - d.getTime()
  if (diffMs < 60_000) return '刚刚'
  if (diffMs < 3_600_000) return Math.floor(diffMs / 60_000) + '分钟前'
  if (diffMs < 86_400_000) return Math.floor(diffMs / 3_600_000) + '小时前'
  if (diffMs < 7 * 86_400_000) return Math.floor(diffMs / 86_400_000) + '天前'
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function goDetail(id, scrollToComment = false) {
  const suffix = scrollToComment ? '&focus=comment' : ''
  uni.navigateTo({ url: `/pages/community/detail?id=${id}${suffix}` })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/community/create' })
}

function goSearch() {
  uni.navigateTo({ url: '/pages/goods/search?scope=community' })
}

function goSearchPage() {
  uni.navigateTo({ url: '/pages/goods/search?scope=community' })
}

function goNotifications() {
  uni.navigateTo({ url: '/pages/user/notifications' })
}

async function loadTopicTags() {
  try {
    const list = await get('/api/v1/community/topics')
    topicTags.value = Array.isArray(list) ? list.slice(0, 8) : []
  } catch (e) {
    topicTags.value = [
      { id: 'fallback-1', name: '宠物穿搭' },
      { id: 'fallback-2', name: '夏日护理' },
      { id: 'fallback-3', name: '新品速递' },
      { id: 'fallback-4', name: '猫咪专区' },
      { id: 'fallback-5', name: '狗狗日常' },
    ]
  }
}

onMounted(() => {
  loadTopicTags()
  loadPosts(true)
})
</script>

<style lang="scss" scoped>
.community-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom, 0));
  /* 显式宽度跟随屏幕,确保内部 scroll-view 跟随 */
  width: 100%;
  box-sizing: border-box;
}

/* 状态栏 */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44rpx;
  padding: 0 20rpx;
  background: var(--color-background);
  font-size: 24rpx;
  color: var(--color-text);
  font-weight: 600;
}
.status-time {
  font-size: 26rpx;
  font-weight: 600;
}
.status-icons {
  display: flex;
  align-items: center;
  gap: 6rpx;
}
.status-icon {
  font-size: 22rpx;
}
.battery {
  width: 48rpx;
  height: 20rpx;
  border-radius: 4rpx;
  background: var(--color-text);
  margin-left: 4rpx;
}

/* 顶部标题栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 20rpx 16rpx;
  background: var(--color-background);
}
.top-title {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--color-text);
}
.top-actions {
  display: flex;
  gap: 12rpx;
  align-items: center;
}
.icon-btn {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  position: relative;
}
.icon {
  font-size: 36rpx;
  color: var(--color-text-secondary);
}
.badge-dot {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--color-danger);
}

/* Tab 栏 */
.tabs {
  display: flex;
  background: var(--color-background);
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
.tab-text {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
  font-weight: 500;
}
.tab-text-active {
  color: var(--color-primary);
  font-weight: 600;
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

/* 搜索条 */
.search-bar {
  padding: 16rpx 20rpx 0;
}
.search-field {
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 72rpx;
  padding: 0 20rpx;
  background: var(--color-background-200, #f2f2f7);
  border-radius: 36rpx;
  color: var(--color-text-tertiary);
}
.search-icon {
  font-size: 30rpx;
}
.search-placeholder {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}

/* 话题标签 */
.topic-bar {
  white-space: nowrap;
  padding: 16rpx 16rpx;
  background: var(--color-background);
  /* 横向 scroll-view 显式宽度 + box-sizing */
  width: 100%;
  box-sizing: border-box;
}
.topic-tag {
  display: inline-flex;
  align-items: center;
  height: 56rpx;
  padding: 0 24rpx;
  margin-right: 12rpx;
  border-radius: 999rpx;
  background: var(--color-primary-bg, #e8f2ff);
  color: var(--color-primary);
  font-size: 24rpx;
  font-weight: 500;
}

/* 信息流 */
.feed {
  flex: 1;
  padding: 0 16rpx;
  /* 显式宽度,确保 scroll-view 内部跟随屏幕 */
  width: 100%;
  box-sizing: border-box;
}
.status {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}
.status-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}

/* 帖子卡片 */
.post-card {
  background: var(--color-surface);
  border-radius: 24rpx;
  border: 1rpx solid var(--color-divider);
  margin-top: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

/* 用户信息行 */
.post-header {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 20rpx 16rpx;
}
.post-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.post-avatar-fallback {
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: 600;
}
.post-user {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.post-username {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}
.post-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.more-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.more-icon {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  letter-spacing: -2rpx;
}

/* 帖子图片:九宫格布局(1张大图 / 2列 / 3列,宽高全部1:1一致)
   关键技巧——用 vw 直接算 cell 边长,绕开 uni-image 自带尺寸与 aspect-ratio 在 grid 中失效的问题 */
.post-image-wrap {
  padding: 0 20rpx 12rpx;
  display: grid;
  gap: 8rpx;
  width: 100%;
  box-sizing: border-box;
}
.post-image-wrap.grid-single {
  grid-template-columns: 1fr;
}
.post-image-wrap.grid-single .post-image {
  width: 100%;
  height: 480rpx;
  border-radius: 20rpx;
  background: var(--color-background);
}
/* 列宽计算:(屏宽 vw - 左右 40rpx padding - 间隙) / 列数
   2 列间隙 1 个,3 列间隙 2 个 */
.post-image-wrap.grid-col-2 {
  grid-template-columns: repeat(2, 1fr);
  /* 显式行高 = 列宽,确保 1:1 方形(uni-image 在 grid 中不响应 aspect-ratio) */
  grid-auto-rows: calc((100vw - 40rpx - 8rpx) / 2);
}
.post-image-wrap.grid-col-3 {
  grid-template-columns: repeat(3, 1fr);
  grid-auto-rows: calc((100vw - 40rpx - 16rpx) / 3);
}
/* 2-3 列时图片固定为 grid 行高(等于列宽),保持视觉统一 */
.post-image-wrap.grid-col-2 .post-image,
.post-image-wrap.grid-col-3 .post-image {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
  background: var(--color-background);
  overflow: hidden;
  position: relative;
  display: block;
}
/* 强制 uni-image 内层 div + img 填满父容器,避免被原图比例拉伸 */
.post-image-wrap.grid-col-2 .post-image ::v-deep div,
.post-image-wrap.grid-col-3 .post-image ::v-deep div,
.post-image-wrap.grid-col-2 .post-image ::v-deep img,
.post-image-wrap.grid-col-3 .post-image ::v-deep img {
  width: 100% !important;
  height: 100% !important;
  object-fit: cover !important;
}

/* 帖子正文 */
.post-content-wrap {
  padding: 0 20rpx 20rpx;
}
.post-content {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.5;
}

/* 互动行 */
.post-actions {
  display: flex;
  align-items: center;
  gap: 40rpx;
  padding: 0 20rpx 20rpx;
}
.action {
  display: flex;
  align-items: center;
  gap: 12rpx;
  color: var(--color-text-secondary);
}
.action.liked {
  color: var(--color-danger);
}
.action-icon {
  font-size: 30rpx;
}
.action-count {
  font-size: 24rpx;
  font-weight: 500;
}

/* 浮动发布按钮 */
.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(98rpx + env(safe-area-inset-bottom, 0));
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(0, 122, 255, 0.35);
  z-index: 50;
}
.fab-icon {
  font-size: 48rpx;
}
</style>
