<template>
  <view class="search">
    <view class="search-bar">
      <view class="search-input-wrap">
        <u-icon name="search" color="#9A948C" size="16" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="Search for products, brands..."
          confirm-type="search"
          @confirm="onSearch"
        >
        <u-icon
          v-if="keyword"
          name="close-circle-fill"
          color="#9A948C"
          size="16"
          @click="keyword = ''"
        />
      </view>
      <text class="cancel" @click="onCancel">Cancel</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 历史搜索 -->
      <view v-if="!keyword && searchHistory.length" class="section">
        <view class="section-header">
          <text class="section-title">Recent Searches</text>
          <u-icon
            name="trash"
            color="#9A948C"
            size="16"
            @click="onClearHistory" />
        </view>
        <view class="chips">
          <view
            v-for="h in searchHistory"
            :key="h"
            class="chip"
            @click="onQuickSearch(h)">
            {{ h }}
          </view>
        </view>
      </view>

      <!-- 热门搜索 -->
      <view v-if="!keyword" class="section">
        <view class="section-header">
          <text class="section-title">Trending</text>
        </view>
        <view class="chips">
          <view
            v-for="h in hotSearches"
            :key="h"
            class="chip hot"
            @click="onQuickSearch(h)">
            {{ h }}
          </view>
        </view>
      </view>

      <!-- 搜索结果（实时联想） -->
      <view v-else>
        <view v-if="searching" class="status">Searching...</view>
        <view v-else-if="suggestions.length === 0" class="status">No suggestions</view>
        <view v-else>
          <view
            v-for="s in suggestions"
            :key="s.id"
            class="suggestion-item"
            @click="goDetail(s.id)"
          >
            <u-icon name="search" color="#9A948C" size="16" />
            <text>{{ s.name }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { productApi } from '@/api'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'

export default {
  data() {
    return {
      keyword: '',
      searchHistory: getStorage(STORAGE_KEYS.SEARCH_HISTORY, []),
      hotSearches: ['Pet Carrier', 'Harness', 'Bathing Set', 'Cat Toy', 'Reflective Gear'],
      suggestions: [],
      searching: false,
      searchTimer: null,
    }
  },

  watch: {
    keyword(newVal) {
      if (this.searchTimer) clearTimeout(this.searchTimer)
      if (!newVal) {
        this.suggestions = []
        return
      }
      this.searchTimer = setTimeout(() => this.fetchSuggestions(), 300)
    },
  },

  methods: {
    onSearch() {
      if (!this.keyword) return
      // 添加到历史
      const list = this.searchHistory.filter((k) => k !== this.keyword)
      list.unshift(this.keyword)
      this.searchHistory = list.slice(0, 10)
      setStorage(STORAGE_KEYS.SEARCH_HISTORY, this.searchHistory)
      // 跳到列表
      uni.navigateTo({ url: `/pages/goods/list?keyword=${encodeURIComponent(this.keyword)}` })
    },

    onQuickSearch(keyword) {
      this.keyword = keyword
      this.onSearch()
    },

    onCancel() {
      uni.navigateBack()
    },

    onClearHistory() {
      this.searchHistory = []
      removeStorage(STORAGE_KEYS.SEARCH_HISTORY)
    },

    async fetchSuggestions() {
      if (!this.keyword) return
      this.searching = true
      try {
        const res = await productApi.searchProducts(this.keyword, { size: 10 })
        this.suggestions = res.records || res || []
      } catch (e) {
        this.suggestions = []
      } finally {
        this.searching = false
      }
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },
  },
}
</script>

<style lang="scss" scoped>
.search {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  padding-top: calc(16rpx + env(safe-area-inset-top));
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: var(--color-background);
  border-radius: var(--radius-pill);
  padding: 16rpx 24rpx;
}

.search-input {
  flex: 1;
  font-size: var(--font-size-base);
  background: transparent;
}

.cancel {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

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
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.chip {
  padding: 12rpx 24rpx;
  font-size: var(--font-size-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  border-radius: var(--radius-pill);
}

.chip.hot {
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary-dark);
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
  font-size: var(--font-size-base);
}

.status {
  text-align: center;
  padding: 48rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
