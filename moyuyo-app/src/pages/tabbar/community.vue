<template>
  <view class="community-page">
    <!-- 顶部导航栏（状态栏 + 标题 + 操作图标） -->
    <view class="top-bar">
      <text class="top-title">{{ $t('community.title') }}</text>
      <view class="top-actions">
        <view class="icon-btn" aria-label="搜索" @tap="goSearch">
          <text class="icon luc-search" />
        </view>
        <view class="icon-btn relative" aria-label="通知" @tap="goNotifications">
          <text class="icon luc-bell" />
          <view class="badge-dot" />
        </view>
      </view>
    </view>

    <!-- Tab：推荐 / 关注 / 话题 -->
    <view class="tabs">
      <view
        v-for="tab in tabList"
        :key="tab.value"
        class="tab"
        :class="{ active: activeTab === tab.value }"
        @tap="onTabChange(tab.value)"
      >
        <text class="tab-text" :class="{ 'tab-text-active': activeTab === tab.value }">
          {{ tab.label }}
        </text>
        <view v-if="activeTab === tab.value" class="tab-indicator" />
      </view>
    </view>

    <!-- 搜索条（仅在「推荐」Tab 显示） -->
    <view v-if="activeTab === 'recommend'" class="search-bar">
      <view class="search-field" @tap="goSearchPage">
        <text class="search-icon luc luc-search" />
        <text class="search-placeholder">{{ $t('community.searchPlaceholder') }}</text>
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
        v-for="topic in topicTags"
        :key="topic.id"
        class="topic-tag"
        @tap="onTopicClick(topic)">
        #{{ topicName(topic.name) }}
      </view>
    </scroll-view>

    <!-- 帖子信息流
         关键：与首页一致,不用 <scroll-view scroll-y>,改用普通 <view>,
         由 uni-app page 原生接管滚动。Android 端 scroll-view 与上方
         横向 scroll-view(话题栏)嵌套时,touch-slop(≈20px)会被 WebView
         用来判定"切断惯性滚动",体感就是"先下滑一点才能上滑"。
         下拉刷新走 page 级 onPullDownRefresh,触底加载走 page 级 onReachBottom。 -->
    <view class="feed">
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
            <text class="action-icon luc-message-circle" />
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
    </view>

    <!-- 浮动发布按钮 -->
    <view class="fab" aria-label="发布帖子" @tap="goCreate">
      <text class="fab-icon luc-camera" />
    </view>
  </view>
</template>

<script>
import { getCurrentInstance } from 'vue'
import { communityApi } from '@/api'
import { get } from '@/utils/request'
import { useUserStore } from '@/store'
import { usePageTitle } from '@/utils/i18nPageMixin'
import { i18n } from '@/i18n'

/**
 * 社区页 - Options API 写法（与 home.vue 一致）
 *
 * 关键：uni-app Vue3 下,page 生命周期（onPullDownRefresh / onReachBottom）
 * 必须放在 Options API 的 methods 之外的 page 级配置中才会被编译器识别,
 * 单纯在 <script setup> 顶层声明 function onPullDownRefresh() 不会被识别,
 * 结果就是下拉触发原生转圈后,uni-app 找不到回调,uni.stopPullDownRefresh
 * 永远不被调用,转圈一直不消失。
 *
 * 因此本页采用 Options + setup() 双段写法：
 *  - setup() 仅构造响应式状态并 return（与原 <script setup> 行为等价）
 *  - page 生命周期与所有方法放在 Options 部分,与 home.vue 完全一致
 */
export default {
  pageTitleKey: 'pageTitle.tabbarCommunity',

  setup() {
    // 仅做 page title 设置 + locale 监听同步。
    // 业务方法全部走 Options.methods,与 home.vue 保持一致。
    usePageTitle('pageTitle.tabbarCommunity')

    // 监听 locale 变化,把版本号同步到 data.localeVersion,触发依赖它的 computed 重算
    i18n.subscribe(() => {
      const inst = getCurrentInstance()
      if (inst && inst.proxy) {
        inst.proxy.localeVersion = (inst.proxy.localeVersion || 0) + 1
      }
    })

    return {}
  },

  data() {
    return {
      activeTab: 'recommend',
      topicTags: [],
      posts: [],
      loading: false,
      noMore: false,
      page: 1,
      pageSize: 20,
      deleting: [],
      // localeVersion 用于触发依赖 i18n 文案的 computed/方法在 locale 切换时重算
      localeVersion: 0,
    }
  },

  computed: {
    /**
     * Tab 文案走 i18n:locale 切换时 label 跟随刷新。
     * 用 computed 而非 data,显式依赖 localeVersion 以建立响应式依赖。
     */
    tabList() {
      void this.localeVersion
      return [
        { value: 'recommend', label: i18n.t('community.tabs.recommend') },
        { value: 'follow', label: i18n.t('community.tabs.follow') },
        { value: 'topic', label: i18n.t('community.tabs.topic') },
      ]
    },
    emptyHint() {
      void this.localeVersion
      if (this.activeTab === 'follow') return '还没有关注的人，去发现感兴趣的用户吧～'
      if (this.activeTab === 'topic') return '选择上方话题标签查看帖子'
      return '还没有帖子，发一个吧～'
    },
  },

  onLoad() {
    this.loadTopicTags()
    this.loadPosts(true)
  },

  /**
   * page 级 onPullDownRefresh：与 pages.json 的 enablePullDownRefresh 配对。
   * 用法：触发原生下拉刷新 → 拉数据 → uni.stopPullDownRefresh 收起。
   */
  onPullDownRefresh() {
    this.loadPosts(true)
      .catch(() => {})
      .finally(() => {
        uni.stopPullDownRefresh()
      })
  },

  /**
   * page 级 onReachBottom：原生滚动触底回调,配合下方 onLoadMore 即可。
   */
  onReachBottom() {
    this.onLoadMore()
  },

  methods: {
    /**
     * 话题 tag 显示名:key 为后端返回的中文 name,value 在字典里查当前 locale 的展示文本。
     * 查不到时回落原中文名(运营新增话题但前端还没翻译,不会显示空白)。
     */
    topicName(rawName) {
      void this.localeVersion
      const translated = i18n.t(`community.topicTags.${rawName}`)
      if (translated && translated !== `community.topicTags.${rawName}`) {
        return translated
      }
      return rawName
    },

    /** 未登录点赞拦截 */
    onLikeClickGuard() {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        uni.showModal({
          title: i18n.t('community.loginToLike'),
          content: i18n.t('community.goLoginPrompt'),
          confirmText: i18n.t('community.goLogin'),
          cancelText: i18n.t('community.maybeLater'),
          success: (res) => {
            if (res.confirm) {
              uni.reLaunch({ url: '/pages/user/login' })
            }
          },
        })
        return false
      }
      return true
    },

    /**
     * 根据当前 Tab 拉取数据源。
     *  - recommend：GET /community/posts（带可选手 topic）
     *  - follow：    GET /follows/feed（关注的人发布的帖子，未登录返回空）
     *  - topic：     初次进入等价 recommend（已加白名单可匿名浏览），等用户点具体话题再过滤
     */
    async loadPosts(reset = false) {
      if (reset) {
        this.page = 1
        this.noMore = false
        this.posts = []
      }
      this.loading = true
      try {
        const params = { page: this.page, size: this.pageSize }
        const userStore = useUserStore()
        let res
        if (this.activeTab === 'follow') {
          if (!userStore.isLoggedIn) {
            this.posts = []
            this.noMore = true
            return
          }
          res = await communityApi.getFollowFeed(params)
        } else {
          res = await communityApi.getCommunityPosts(params)
        }
        const list = res?.records || res || []
        this.posts.push(...list)
        this.noMore = list.length < this.pageSize
        this.page += 1
      } catch (e) {
        console.warn('[community] load error', e)
      } finally {
        this.loading = false
      }
    },

    onTabChange(value) {
      if (this.activeTab === value) return
      this.activeTab = value
      this.loadPosts(true)
    },

    onTopicClick(t) {
      uni.navigateTo({
        url: `/pages/user/community-topic?id=${t.id}&name=${encodeURIComponent(t.name)}`,
      })
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.loadPosts(false)
    },

    async onLike(p) {
      if (!this.onLikeClickGuard()) return
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
        uni.showToast({ title: i18n.t('common.actionFailed'), icon: 'none' })
      }
    },

    onShare(p) {
      const shareUrl = `/pages/community/detail?id=${p.id}`
      uni.setClipboardData({
        data: shareUrl,
        success: () => {
          uni.showToast({ title: '链接已复制', icon: 'success' })
        },
        fail: () => {
          uni.showToast({ title: '复制失败,请重试', icon: 'none' })
        },
      })
    },

    onMore(p) {
      const userStore = useUserStore()
      const me = userStore.userInfo && userStore.userInfo.id
      const isMine = me != null && String(p.userId) === String(me)
      const itemList = isMine
        ? [
            this.$t('community.actions.delete'),
            this.$t('community.actions.notInterested'),
            this.$t('community.actions.report'),
          ]
        : [this.$t('community.actions.notInterested'), this.$t('community.actions.report')]
      uni.showActionSheet({
        itemList,
        success: async (res) => {
          const chosen = isMine
            ? ['delete', 'notInterested', 'report'][res.tapIndex]
            : ['notInterested', 'report'][res.tapIndex]
          if (chosen === 'delete') {
            await this.onDeletePost(p)
          } else if (chosen === 'report') {
            uni.showToast({ title: this.$t('community.actions.report') + ' 已提交', icon: 'none' })
          } else {
            uni.showToast({
              title: this.$t('community.actions.notInterested') + ' 已提交',
              icon: 'none',
            })
          }
        },
      })
    },

    /**
     * 删除帖子（仅自己的帖子会进入这里）。
     */
    async onDeletePost(p) {
      uni.showModal({
        title: this.$t('community.actions.delete'),
        content: this.$t('community.actions.deleteConfirm'),
        confirmText: this.$t('community.actions.delete'),
        cancelText: this.$t('community.actions.cancel'),
        confirmColor: '#ff4d4f',
        success: async (res) => {
          if (!res.confirm) return
          if (this.deleting.includes(p.id)) return
          this.deleting.push(p.id)
          try {
            await communityApi.deletePost(p.id)
            uni.showToast({ title: this.$t('community.actions.deleteSuccess'), icon: 'success' })
            this.posts = this.posts.filter((x) => x.id !== p.id)
          } catch (e) {
            uni.showToast({ title: this.$t('community.actions.deleteFailed'), icon: 'none' })
          } finally {
            this.deleting = this.deleting.filter((id) => id !== p.id)
          }
        },
      })
    },

    avatarChar(name) {
      if (!name) return 'P'
      return name.substring(0, 1).toUpperCase()
    },

    /**
     * 根据图片数量决定九宫格样式类
     */
    getImageGridClass(count) {
      if (count <= 1) return 'single'
      if (count <= 3) return 'col-2'
      return 'col-3'
    },

    onImageError(e) {
      const target = e?.target
      if (target && target.src && !target.src.includes('data:')) {
        target.src =
          'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 3"><rect width="4" height="3" fill="%23f2f2f7"/></svg>'
      }
    },

    /**
     * 解析帖子图片 URL
     */
    resolveImageUrl(url) {
      const PLACEHOLDER_IMG =
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 3"><rect width="4" height="3" fill="%23eaeef2"/><text x="2" y="1.7" font-size="0.4" text-anchor="middle" fill="%23888" font-family="sans-serif">图片</text></svg>'
      if (!url) return PLACEHOLDER_IMG
      if (url.startsWith('http://') || url.startsWith('https://')) return url
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
      if (url.startsWith('/')) {
        const base = process.env.VITE_ADMIN_API_BASE
        return base ? `${base}${url}` : url
      }
      return PLACEHOLDER_IMG
    },

    /**
     * 帖子时间格式化
     */
    formatTime(time) {
      if (!time) return ''
      void this.localeVersion
      const d = new Date(time)
      const now = Date.now()
      const diffMs = now - d.getTime()
      if (diffMs < 60_000) return i18n.t('community.time.justNow')
      if (diffMs < 3_600_000) {
        return i18n.t('community.time.minutesAgo', { n: Math.floor(diffMs / 60_000) })
      }
      if (diffMs < 86_400_000) {
        return i18n.t('community.time.hoursAgo', { n: Math.floor(diffMs / 3_600_000) })
      }
      if (diffMs < 7 * 86_400_000) {
        return i18n.t('community.time.daysAgo', { n: Math.floor(diffMs / 86_400_000) })
      }
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },

    goDetail(id, scrollToComment = false) {
      const suffix = scrollToComment ? '&focus=comment' : ''
      uni.navigateTo({ url: `/pages/community/detail?id=${id}${suffix}` })
    },

    goCreate() {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        uni.showModal({
          title: i18n.t('community.loginToPost'),
          content: i18n.t('community.goLoginPrompt'),
          confirmText: i18n.t('community.goLogin'),
          cancelText: i18n.t('community.maybeLater'),
          success: (res) => {
            if (res.confirm) {
              uni.reLaunch({ url: '/pages/user/login' })
            }
          },
        })
        return
      }
      uni.navigateTo({ url: '/pages/community/create' })
    },

    goSearch() {
      uni.navigateTo({ url: '/pages/community/search' })
    },

    goSearchPage() {
      uni.navigateTo({ url: '/pages/community/search' })
    },

    goNotifications() {
      uni.navigateTo({ url: '/pages/user/notifications' })
    },

    async loadTopicTags() {
      try {
        const list = await get('/api/v1/community/topics')
        this.topicTags = Array.isArray(list) ? list.slice(0, 8) : []
      } catch (e) {
        this.topicTags = [
          { id: 'fallback-1', name: '宠物穿搭' },
          { id: 'fallback-2', name: '夏日护理' },
          { id: 'fallback-3', name: '新品速递' },
          { id: 'fallback-4', name: '猫咪专区' },
          { id: 'fallback-5', name: '狗狗日常' },
        ]
      }
    },
  },
}
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
  /* 状态栏安全区：与 home.vue .navbar 保持一致，避免"社区"标题被状态栏遮挡 */
  padding-top: calc(12rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
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

/* 信息流:由 page 原生接管滚动,这里只是普通块级容器 */
.feed {
  padding: 0 16rpx;
  /* 显式宽度,确保内容跟随屏幕 */
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
