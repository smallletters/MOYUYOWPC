<template>
  <view class="help-center">
    <view class="header">
      <view class="header-btn" @click="goBack"><text class="back-icon">‹</text></view>
      <text class="header-title">帮助中心</text>
      <view class="header-btn" />
    </view>

    <view class="hero-section">
      <view class="hero-placeholder">
        <text class="hero-icon">❓</text>
        <text class="hero-text">有什么可以帮助你的？</text>
      </view>
    </view>

    <view class="search-bar">
      <view class="search-field">
        <text class="search-icon">⌕</text>
        <input class="search-input" type="text" placeholder="搜索常见问题" v-model="keyword" />
      </view>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="category-list">
      <view v-for="c in filteredCategories" :key="c.id" class="category-card" @tap="openCategory(c)">
        <text class="category-icon">{{ c.icon || '📘' }}</text>
        <view class="category-info">
          <text class="category-name">{{ c.name }}</text>
          <text class="category-count">{{ c.count || 0 }} 个问题</text>
        </view>
        <text class="category-arrow">›</text>
      </view>
      <view v-if="!filteredCategories.length" class="empty">
        <text class="empty-text">暂无相关问题</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { helpApi } from '@/api'

const keyword = ref('')
const categories = ref([])
const articles = ref([])
const loading = ref(false)

const filteredCategories = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return categories.value
  return categories.value.filter((c) => (c.name || '').toLowerCase().includes(k))
})

async function load() {
  loading.value = true
  try {
    const [cats, arts] = await Promise.all([helpApi.listCategories(), helpApi.listArticles({ size: 100 })])
    categories.value = Array.isArray(cats) ? cats : []
    const records = arts?.records || arts || []
    articles.value = records
    // 给分类挂上问题数
    categories.value = categories.value.map((c) => ({
      ...c,
      count: records.filter((a) => a.categoryId === c.id).length,
    }))
  } catch (e) {
    console.warn('[help] load failed', e)
  } finally {
    loading.value = false
  }
}

function openCategory(c) {
  // 跳到帮助中心详细列表（FAQ 列表）
  uni.navigateTo({ url: `/pages/user/help-center?categoryId=${c.id}&categoryName=${encodeURIComponent(c.name)}` })
}

function goBack() { uni.navigateBack() }

onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.help-center { min-height: 100vh; background: var(--color-background); }
.header { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.header-btn { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.header-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.hero-section { padding: 32rpx 24rpx; }
.hero-placeholder { display: flex; align-items: center; gap: 16rpx; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; }
.hero-icon { font-size: 48rpx; }
.hero-text { font-size: 28rpx; color: var(--color-text-secondary); }
.search-bar { padding: 0 24rpx 16rpx; }
.search-field { display: flex; align-items: center; gap: 8rpx; padding: 0 16rpx; height: 72rpx; background: var(--color-surface); border-radius: 36rpx; }
.search-icon { color: var(--color-text-tertiary); }
.search-input { flex: 1; font-size: 26rpx; }
.loading { padding: 40rpx; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.category-list { padding: 16rpx; display: flex; flex-direction: column; gap: 12rpx; }
.category-card { display: flex; align-items: center; gap: 16rpx; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; }
.category-icon { font-size: 40rpx; }
.category-info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.category-name { font-size: 28rpx; font-weight: 600; }
.category-count { font-size: 22rpx; color: var(--color-text-tertiary); }
.category-arrow { font-size: 36rpx; color: var(--color-text-tertiary); }
.empty { padding: 60rpx; text-align: center; }
.empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
</style>