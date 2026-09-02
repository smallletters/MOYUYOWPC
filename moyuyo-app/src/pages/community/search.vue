<template>
  <view class="search">
    <!-- 顶部搜索栏 -->
    <view class="search-bar">
      <view class="search-input-wrap">
        <text class="luc luc-search search-icon" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索帖子、用户、话题…"
          confirm-type="search"
          :focus="true"
          @confirm="onSearch"
        >
        <text
          v-if="keyword"
          class="luc luc-x clear-btn"
          @click="keyword = ''"
        />
      </view>
      <text class="cancel" @click="onCancel">取消</text>
    </view>

    <!-- 三 Tab：帖子 / 用户 / 话题 -->
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

    <scroll-view scroll-y class="content" @scrolltolower="onLoadMore">
      <!-- 搜索结果为空时显示历史/热门 -->
      <template v-if="!keyword">
        <!-- 历史搜索 -->
        <view v-if="searchHistory.length" class="section">
          <view class="section-header">
            <text class="section-title">历史搜索</text>
            <text class="luc luc-trash" @click="onClearHistory" />
          </view>
          <view class="chips">
            <view
              v-for="h in searchHistory"
              :key="h"
              class="chip"
              @click="onQuickSearch(h)"
            >{{ h }}</view>
          </view>
        </view>

        <!-- 热门搜索 -->
        <view class="section">
          <view class="section-header">
            <text class="section-title">热门搜索</text>
          </view>
          <view class="chips">
            <view
              v-for="h in hotSearches"
              :key="h"
              class="chip hot"
              @click="onQuickSearch(h)"
            >{{ h }}</view>
          </view>
        </view>
      </template>

      <!-- 搜索中或加载中 -->
      <view v-else-if="loading && results.length === 0" class="status">
        <text class="status-text">搜索中…</text>
      </view>

      <!-- 无结果 -->
      <view
        v-else-if="results.length === 0"
        class="status"
      >
        <text class="status-text">没有找到相关内容</text>
      </view>

      <!-- ============= 帖子结果 ============= -->
      <template v-else-if="activeTab === 'post'">
        <view
          v-for="p in results"
          :key="p.id"
          class="post-card"
          @tap="goPostDetail(p.id)"
        >
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
          </view>
          <view class="post-content">
            <text class="post-text">{{ p.content || '(无内容)' }}</text>
            <view v-if="p.images && p.images.length" class="post-images">
              <image
                v-for="(img, idx) in p.images.slice(0, 3)"
                :key="idx"
                :src="resolveImageUrl(img)"
                class="post-thumb"
                mode="aspectFill"
              />
            </view>
          </view>
        </view>
      </template>

      <!-- ============= 用户结果 ============= -->
      <template v-else-if="activeTab === 'user'">
        <view
          v-for="u in results"
          :key="u.id"
          class="user-card"
          @tap="goUserProfile(u.id)"
        >
          <image
            v-if="u.avatar"
            :src="resolveImageUrl(u.avatar)"
            class="user-avatar"
            mode="aspectFill"
          />
          <view v-else class="user-avatar user-avatar-fallback">
            {{ avatarChar(u.nickname) }}
          </view>
          <view class="user-info">
            <text class="user-name">{{ u.nickname }}</text>
          </view>
        </view>
      </template>

      <!-- ============= 话题结果 ============= -->
      <template v-else-if="activeTab === 'topic'">
        <view
          v-for="t in results"
          :key="t.id"
          class="topic-card"
          @tap="onTopicClick(t)"
        >
          <text class="topic-name"># {{ t.name }}</text>
          <text v-if="t.description" class="topic-desc">{{ t.description }}</text>
          <text class="topic-meta">{{ t.postCount || 0 }} 帖 · {{ t.followCount || 0 }} 人关注</text>
        </view>
      </template>

      <!-- 加载更多占位 -->
      <view v-if="loading && results.length > 0" class="status">
        <text class="status-text">加载中…</text>
      </view>
      <view v-if="!hasMore && results.length > 0" class="status">
        <text class="status-text">— 没有更多了 —</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { communityApi } from '@/api'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'

const SEARCH_HISTORY_KEY = STORAGE_KEYS.SEARCH_HISTORY

export default {
  pageTitleKey: 'pageTitle.communitySearch',

  data() {
    return {
      keyword: '',
      activeTab: 'post',
      tabs: [
        { label: '帖子', value: 'post' },
        { label: '用户', value: 'user' },
        { label: '话题', value: 'topic' },
      ],
      searchHistory: getStorage(SEARCH_HISTORY_KEY, []),
      hotSearches: ['萌宠日常', '猫咪', '遛狗', '宠物用品', '寻宠启事'],
      results: [],
      loading: false,
      hasMore: true,
      page: 1,
      pageSize: 20,
      searchTimer: null,
    }
  },

  onLoad(query) {
    // 接受外部传入的 keyword,常见来源:详情页 #话题/@用户 段点击跳转
    if (query && query.keyword) {
      this.keyword = decodeURIComponent(query.keyword)
    }
  },

  watch: {
    keyword(newVal) {
      if (this.searchTimer) clearTimeout(this.searchTimer)
      if (!newVal) {
        this.results = []
        this.hasMore = true
        this.page = 1
        return
      }
      // 输入变化:统一 300ms 防抖,所有 Tab 都实时联想(post/user/topic 都能立即出结果)
      this.searchTimer = setTimeout(() => this.fetchSuggestions(), 300)
    },
    activeTab() {
      // 切换 Tab 时,如果当前有 keyword 立即重新查询
      if (this.keyword) {
        this.page = 1
        this.results = []
        this.hasMore = true
        this.fetchSuggestions()
      }
    },
  },

  methods: {
    onSearch() {
      if (!this.keyword) return
      // 保存历史
      const list = this.searchHistory.filter((k) => k !== this.keyword)
      list.unshift(this.keyword)
      this.searchHistory = list.slice(0, 10)
      setStorage(SEARCH_HISTORY_KEY, this.searchHistory)
      // 重置分页后立即查询
      this.page = 1
      this.results = []
      this.hasMore = true
      this.fetchSuggestions()
    },

    onQuickSearch(k) {
      this.keyword = k
      this.onSearch()
    },

    onCancel() {
      uni.navigateBack()
    },

    onClearHistory() {
      this.searchHistory = []
      removeStorage(SEARCH_HISTORY_KEY)
    },

    onTabChange(v) {
      if (this.activeTab !== v) this.activeTab = v
    },

    async fetchSuggestions(loadMore = false) {
      if (!this.keyword) return
      if (this.loading) return
      this.loading = true
      try {
        let res
        if (this.activeTab === 'post') {
          res = await communityApi.searchCommunityPosts({
            keyword: this.keyword,
            page: this.page,
            size: this.pageSize,
          })
          const records = res.records || []
          this.results = loadMore ? this.results.concat(records) : records
          this.hasMore = records.length >= this.pageSize
        } else if (this.activeTab === 'user') {
          res = await communityApi.searchCommunityUsers({
            keyword: this.keyword,
            page: this.page,
            size: this.pageSize,
          })
          const records = res.records || []
          this.results = loadMore ? this.results.concat(records) : records
          this.hasMore = records.length >= this.pageSize
        } else if (this.activeTab === 'topic') {
          // 话题不分页，一次性返回
          res = await communityApi.getCommunityTopics({ keyword: this.keyword })
          this.results = Array.isArray(res) ? res : (res.records || [])
          this.hasMore = false
        }
      } catch (e) {
        console.error('[community-search] fetch failed:', e)
        this.results = []
      } finally {
        this.loading = false
      }
    },

    onLoadMore() {
      if (!this.keyword) return
      if (this.activeTab === 'topic') return
      if (!this.hasMore || this.loading) return
      this.page++
      this.fetchSuggestions(true)
    },

    goPostDetail(id) {
      uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
    },

    goUserProfile(id) {
      uni.navigateTo({ url: `/pages/user/profile?id=${id}` })
    },

    onTopicClick(t) {
      // 点话题卡:把话题名作为关键词,留在本搜索页并自动切到话题 Tab 重新查询
      // (旧实现通过 eventCenter 跨页面通信,但社区首页未监听,事件丢失)
      this.keyword = (t && t.name) || ''
      this.activeTab = 'topic'
      this.page = 1
      this.results = []
      this.hasMore = true
      this.fetchSuggestions()
    },

    resolveImageUrl(url) {
      if (!url) return ''
      if (url.startsWith('http://') || url.startsWith('https://')) return url
      // 相对路径(/uploads/...) APP 端无 dev server，必须拼上后端 base
      if (url.startsWith('/')) {
        const base = process.env.VITE_ADMIN_API_BASE
        return base ? `${base}${url}` : url
      }
      return url
    },

    onImageError(e) {
      // 静默失败，前端 fallback 显示首字母
    },

    avatarChar(name) {
      if (!name) return 'P'
      return String(name).trim().charAt(0).toUpperCase()
    },

    formatTime(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      const now = new Date()
      const diff = (now - d) / 1000
      if (diff < 60) return '刚刚'
      if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
      if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
      if (diff < 7 * 86400) return Math.floor(diff / 86400) + '天前'
      const yyyy = d.getFullYear()
      const mm = String(d.getMonth() + 1).padStart(2, '0')
      const dd = String(d.getDate()).padStart(2, '0')
      return `${yyyy}-${mm}-${dd}`
    },
  },
}
</script>

<style lang="scss" scoped>
.search {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background, #f5f6f8);
}

/* 顶部搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface, #ffffff);
  padding-top: calc(16rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  border-bottom: 1rpx solid var(--color-divider, #ececec);
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: var(--color-background, #f5f6f8);
  border-radius: var(--radius-pill, 999rpx);
  padding: 12rpx 20rpx;
}

.search-icon {
  font-size: 32rpx;
  color: var(--color-text-tertiary, #999);
}

.search-input {
  flex: 1;
  font-size: var(--font-size-base, 28rpx);
  background: transparent;
}

.clear-btn {
  font-size: 36rpx;
  color: var(--color-text-tertiary, #999);
  padding: 4rpx 8rpx;
}

.cancel {
  font-size: var(--font-size-base, 28rpx);
  color: var(--color-text, #1a1a1a);
  flex-shrink: 0;
}

/* Tab */
.tabs {
  display: flex;
  background: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
}

.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0;
  position: relative;
}

.tab-text {
  font-size: var(--font-size-base, 28rpx);
  color: var(--color-text-secondary, #666);
}

.tab-text-active {
  color: var(--color-primary, #18b367);
  font-weight: var(--font-weight-semibold, 600);
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary, #18b367);
  border-radius: 2rpx;
}

/* 内容区 */
.content {
  flex: 1;
  padding: 24rpx;
}

.section {
  margin-bottom: 32rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.section-title {
  font-size: var(--font-size-base, 28rpx);
  font-weight: var(--font-weight-semibold, 600);
  color: var(--color-text, #1a1a1a);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.chip {
  padding: 10rpx 20rpx;
  font-size: var(--font-size-sm, 24rpx);
  background: var(--color-surface, #ffffff);
  color: var(--color-text-secondary, #666);
  border-radius: var(--radius-pill, 999rpx);
  border: 1rpx solid var(--color-divider, #ececec);
}

.chip.hot {
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary-dark, #b08c2a);
  border-color: transparent;
}

.status {
  text-align: center;
  padding: 48rpx 0;
  color: var(--color-text-tertiary, #999);
  font-size: var(--font-size-sm, 24rpx);
}

/* 帖子卡片 */
.post-card {
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.post-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.post-avatar,
.user-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.post-avatar-fallback,
.user-avatar-fallback {
  background: var(--color-primary, #18b367);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.post-user {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.post-username,
.user-name {
  font-size: var(--font-size-base, 28rpx);
  font-weight: var(--font-weight-semibold, 600);
  color: var(--color-text, #1a1a1a);
}

.post-time {
  font-size: var(--font-size-sm, 22rpx);
  color: var(--color-text-tertiary, #999);
  margin-top: 4rpx;
}

.post-text {
  font-size: var(--font-size-base, 28rpx);
  color: var(--color-text, #1a1a1a);
  line-height: 1.5;
  display: block;
}

.post-images {
  display: flex;
  gap: 8rpx;
  margin-top: 12rpx;
}

.post-thumb {
  width: 180rpx;
  height: 180rpx;
  border-radius: var(--radius-sm, 8rpx);
  object-fit: cover;
}

/* 用户卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.user-info {
  flex: 1;
}

/* 话题卡片 */
.topic-card {
  display: flex;
  flex-direction: column;
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.topic-name {
  font-size: var(--font-size-base, 30rpx);
  font-weight: var(--font-weight-semibold, 600);
  color: var(--color-primary, #18b367);
}

.topic-desc {
  margin-top: 8rpx;
  font-size: var(--font-size-sm, 24rpx);
  color: var(--color-text-secondary, #666);
  line-height: 1.4;
}

.topic-meta {
  margin-top: 12rpx;
  font-size: var(--font-size-sm, 22rpx);
  color: var(--color-text-tertiary, #999);
}
</style>
