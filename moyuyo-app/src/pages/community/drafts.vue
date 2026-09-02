<template>
  <view class="drafts-page">
    <view class="navbar">
      <view class="navbar__back" @click="onBack">
        <text class="navbar__back-icon">‹</text>
      </view>
      <text class="navbar__title">我的草稿 ({{ drafts.length }})</text>
      <view v-if="drafts.length" class="navbar__action" @click="onClearAll">
        <text class="navbar__action-text">清空</text>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>

    <view v-else-if="!drafts.length" class="empty">
      <text class="empty__icon">📭</text>
      <text class="empty__title">还没有草稿</text>
      <text class="empty__desc">编辑帖子时点击右上角"存草稿"即可保存</text>
      <view class="empty__btn" @click="onNewPost">
        <text>新建帖子</text>
      </view>
    </view>

    <scroll-view v-else scroll-y class="list">
      <view
        v-for="d in drafts"
        :key="d.id"
        class="draft-item"
        @click="onPick(d)"
        @longpress="onLongPress(d)"
      >
        <view class="draft-item__main">
          <text class="draft-item__preview">{{ previewOf(d) }}</text>
          <view class="draft-item__meta">
            <text v-if="d.topic" class="draft-item__topic"># {{ d.topic }}</text>
            <text v-if="d.location" class="draft-item__location">📍 {{ d.location }}</text>
            <text class="draft-item__time">{{ formatTime(d.savedAt) }}</text>
          </view>
        </view>
        <view class="draft-item__delete" @click.stop="onDelete(d)">
          <text>×</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { STORAGE_KEYS } from '@/utils/storage'

/**
 * 草稿列表页:展示 / 恢复 / 删除 / 清空本地社区帖子草稿。
 * 数据源:uni.getStorageSync(COMMUNITY_POST_DRAFTS) 数组
 */
export default {
  pageTitleKey: 'pageTitle.communityDrafts',

  data() {
    return {
      drafts: [],
      loading: true,
    }
  },

  onShow() {
    // 每次显示都重新读取(用户可能从 create 页面保存了新的)
    this.loadDrafts()
  },

  methods: {
    /** 读取本地草稿列表 */
    loadDrafts() {
      this.loading = true
      try {
        const list = uni.getStorageSync(STORAGE_KEYS.COMMUNITY_POST_DRAFTS)
        this.drafts = Array.isArray(list) ? list : []
      } catch (e) {
        this.drafts = []
      } finally {
        this.loading = false
      }
    },

    /** 草稿预览:取 content 前 60 字符,空时显示话题或位置 */
    previewOf(d) {
      const c = (d.content || '').trim()
      if (c) return c.length > 60 ? c.slice(0, 60) + '…' : c
      if (d.topic) return `# ${d.topic}`
      if (d.location) return `📍 ${d.location}`
      return '(空内容)'
    },

    formatTime(ts) {
      if (!ts) return ''
      const diff = Date.now() - ts
      const min = Math.floor(diff / 60000)
      if (min < 1) return '刚刚'
      if (min < 60) return `${min} 分钟前`
      const hr = Math.floor(min / 60)
      if (hr < 24) return `${hr} 小时前`
      const day = Math.floor(hr / 24)
      if (day < 30) return `${day} 天前`
      const dt = new Date(ts)
      return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
    },

    /** 点击草稿 → 进入创建页并恢复 */
    onPick(d) {
      // 跳转到创建页,通过 query 传 draftId 让 create.vue 自动恢复
      uni.redirectTo({
        url: `/pages/community/create?draftId=${encodeURIComponent(d.id)}`,
      })
    },

    /** 长按草稿 → 弹删除确认 */
    onLongPress(d) {
      uni.showActionSheet({
        itemList: ['删除此草稿'],
        success: (res) => {
          if (res.tapIndex === 0) this.onDelete(d)
        },
      })
    },

    /** 删除单条 */
    onDelete(d) {
      uni.showModal({
        title: '删除草稿',
        content: '确认删除该草稿?',
        confirmText: '删除',
        cancelText: '取消',
        success: (res) => {
          if (!res.confirm) return
          const next = this.drafts.filter((x) => x.id !== d.id)
          uni.setStorageSync(STORAGE_KEYS.COMMUNITY_POST_DRAFTS, next)
          this.drafts = next
          uni.showToast({ title: '已删除', icon: 'success' })
        },
      })
    },

    /** 清空全部 */
    onClearAll() {
      uni.showModal({
        title: '清空草稿',
        content: `确认删除全部 ${this.drafts.length} 份草稿?`,
        confirmText: '清空',
        cancelText: '取消',
        success: (res) => {
          if (!res.confirm) return
          uni.setStorageSync(STORAGE_KEYS.COMMUNITY_POST_DRAFTS, [])
          this.drafts = []
          uni.showToast({ title: '已清空', icon: 'success' })
        },
      })
    },

    /** 新建帖子:清空当前草稿,创建新的 */
    onNewPost() {
      uni.redirectTo({ url: '/pages/community/create' })
    },

    onBack() {
      uni.navigateBack()
    },
  },
}
</script>

<style lang="scss" scoped>
.drafts-page {
  min-height: 100vh;
  background: var(--color-background, #f5f6f8);
  display: flex;
  flex-direction: column;
}

.navbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
}
.navbar__back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.navbar__back-icon {
  font-size: 56rpx;
  line-height: 1;
}
.navbar__title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
}
.navbar__action {
  padding: 0 16rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
}
.navbar__action-text {
  font-size: 28rpx;
  color: #ff4d4f;
}

.loading,
.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 96rpx 24rpx;
}
.empty__icon {
  font-size: 96rpx;
  margin-bottom: 24rpx;
}
.empty__title {
  font-size: 32rpx;
  color: var(--color-text-primary);
  margin-bottom: 12rpx;
  font-weight: 500;
}
.empty__desc {
  font-size: 26rpx;
  color: var(--color-text-tertiary, #999);
  margin-bottom: 32rpx;
}
.empty__btn {
  padding: 16rpx 48rpx;
  background: var(--color-primary, #18b367);
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 28rpx;
}

.list {
  flex: 1;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
}
.draft-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-surface, #ffffff);
  border-radius: 12rpx;
  margin-bottom: 16rpx;
}
.draft-item:active {
  background: var(--color-background, #f5f6f8);
}
.draft-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.draft-item__preview {
  font-size: 28rpx;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
}
.draft-item__meta {
  display: flex;
  gap: 16rpx;
  font-size: 22rpx;
  color: var(--color-text-tertiary, #999);
  align-items: center;
  flex-wrap: wrap;
}
.draft-item__topic {
  color: var(--color-primary, #18b367);
}
.draft-item__delete {
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  background: var(--color-background, #f5f6f8);
  color: var(--color-text-tertiary, #999);
  border-radius: 50%;
  font-size: 32rpx;
  flex-shrink: 0;
}
.draft-item__delete:active {
  background: #ffe7e7;
  color: #ff4d4f;
}
</style>
