<template>
  <view class="help-center">
    <view class="page-header">
      <view class="back" @click="goBack">‹</view>
      <text class="title">{{ categoryName || 'FAQ' }}</text>
    </view>

    <scroll-view scroll-y class="content">
      <view class="search-bar">
        <text class="search-icon">🔍</text>
        <input v-model="keyword" placeholder="搜索常见问题" class="search-input" />
      </view>

      <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
      <view v-else-if="!filteredFAQ.length" class="empty"><text class="empty-text">暂无相关问题</text></view>
      <view v-else class="faq-list">
        <view v-for="(f, i) in filteredFAQ" :key="f.id" class="faq-item" @click="toggleFaq(i)">
          <view class="faq-q">
            <text class="faq-q-text">{{ f.title }}</text>
            <text class="faq-arrow" :class="{ rotated: f.expanded }">›</text>
          </view>
          <view v-if="f.expanded" class="faq-a">
            <text class="faq-a-text">{{ f.content }}</text>
            <view class="faq-helpful">
              <text class="faq-helpful-text">是否解决了您的问题？</text>
              <view class="helpful-btn" @tap.stop="vote(f, true)">👍 有帮助</view>
              <view class="helpful-btn no" @tap.stop="vote(f, false)">👎 没解决</view>
            </view>
          </view>
        </view>
      </view>

      <view class="contact-bar">
        <text class="contact-title">没找到答案？</text>
        <view class="btn-primary" @click="onContact">联系在线客服</view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { helpApi } from '@/api'

const keyword = ref('')
const loading = ref(false)
const articles = ref([])
const categoryId = ref(null)
const categoryName = ref('')

const filteredFAQ = computed(() => {
  let list = articles.value
  if (categoryId.value) list = list.filter((a) => String(a.categoryId) === String(categoryId.value))
  const k = keyword.value.trim().toLowerCase()
  if (k) list = list.filter((a) => (a.title || '').toLowerCase().includes(k) || (a.content || '').toLowerCase().includes(k))
  return list
})

async function load() {
  loading.value = true
  try {
    const res = await helpApi.listArticles({ size: 200 })
    const records = res?.records || res || []
    articles.value = records
  } catch (e) {
    console.warn('[help-center] load failed', e)
  } finally {
    loading.value = false
  }
}

function toggleFaq(i) {
  filteredFAQ.value[i].expanded = !filteredFAQ.value[i].expanded
}

async function vote(article, helpful) {
  try {
    await helpApi.helpful(article.id, helpful)
    uni.showToast({ title: '感谢您的反馈', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function onContact() { uni.navigateTo({ url: '/pages/user/customer-service' }) }
function goBack() { uni.navigateBack() }

onMounted(() => {
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const q = cur?.options || {}
    if (q.categoryId) categoryId.value = q.categoryId
    if (q.categoryName) categoryName.value = decodeURIComponent(q.categoryName)
  } catch (e) { /* ignore */ }
  load()
})
</script>

<style lang="scss" scoped>
.help-center { min-height: 100vh; background: var(--color-background); display: flex; flex-direction: column; }
.page-header { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.back { width: 60rpx; font-size: 44rpx; color: var(--color-primary); }
.title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.content { flex: 1; }
.search-bar { display: flex; align-items: center; gap: 8rpx; margin: 16rpx 24rpx; padding: 0 16rpx; height: 72rpx; background: var(--color-surface); border-radius: 36rpx; }
.search-icon { color: var(--color-text-tertiary); }
.search-input { flex: 1; font-size: 26rpx; }
.loading { padding: 40rpx; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.empty { padding: 80rpx; text-align: center; }
.empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.faq-list { padding: 0 24rpx; }
.faq-item { background: var(--color-surface); border-radius: 16rpx; padding: 16rpx 20rpx; margin-bottom: 12rpx; }
.faq-q { display: flex; justify-content: space-between; align-items: center; }
.faq-q-text { font-size: 28rpx; font-weight: 500; }
.faq-arrow { font-size: 32rpx; color: var(--color-text-tertiary); transition: transform 0.2s; }
.faq-arrow.rotated { transform: rotate(90deg); }
.faq-a { margin-top: 12rpx; padding-top: 12rpx; border-top: 1rpx solid var(--color-divider); }
.faq-a-text { font-size: 26rpx; color: var(--color-text-secondary); line-height: 1.6; }
.faq-helpful { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; }
.faq-helpful-text { font-size: 22rpx; color: var(--color-text-tertiary); }
.helpful-btn { padding: 4rpx 16rpx; background: #e6f7ff; color: var(--color-primary); border-radius: 999rpx; font-size: 22rpx; }
.helpful-btn.no { background: #f0f0f0; color: var(--color-text-secondary); }
.contact-bar { margin: 24rpx; padding: 24rpx; background: var(--color-surface); border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; gap: 12rpx; }
.contact-title { font-size: 26rpx; color: var(--color-text-secondary); }
.btn-primary { padding: 16rpx 48rpx; background: var(--color-primary); color: #fff; border-radius: 999rpx; font-size: 26rpx; }
</style>