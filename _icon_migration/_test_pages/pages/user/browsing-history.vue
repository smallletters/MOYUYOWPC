<template>
  <view class="browsing-history">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">浏览记录</text>
      <view class="header-btn right" @click="onToggleEdit">
        <text class="edit-text">{{ editMode ? '完成' : '清空' }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view v-if="filteredList.length === 0 && !editMode" class="empty-state">
        <view class="empty-icon">
          <view class="empty-box">
            <view class="empty-eye"><text class="luc luc-eye"></text></view>
          </view>
          <view class="empty-ring" />
        </view>
        <text class="empty-title">暂无浏览记录</text>
        <text class="empty-desc">去逛逛喜欢的商品吧</text>
        <view class="empty-btn" @click="goShopping">去购物</view>
      </view>

      <template v-if="editMode">
        <view
          v-for="item in filteredList"
          :key="item.id"
          class="edit-item"
          @click="toggleSelect(item)"
        >
          <view class="checkbox" :class="{ checked: selectedIds.includes(item.id) }">
            <text v-if="selectedIds.includes(item.id)" class="check-mark"><text class="luc luc-check"></text></text>
          </view>
          <image :src="item.image" mode="aspectFill" class="edit-img" />
          <view class="edit-info">
            <text class="edit-name">{{ item.name }}</text>
            <text class="edit-price">¥{{ item.price }}</text>
          </view>
        </view>
        <view v-if="selectedIds.length > 0" class="delete-bar">
          <view class="delete-btn" @click="onDeleteSelected">
            <text class="delete-text">删除选中 ({{ selectedIds.length }})</text>
          </view>
        </view>
      </template>

      <template v-else>
        <view v-for="(group, groupIndex) in groupedList" :key="groupIndex" class="timeline-group">
          <view class="timeline-header">
            <text class="timeline-date">{{ group.dateLabel }}</text>
          </view>
          <view class="timeline-items">
            <view
              v-for="item in group.items"
              :key="item.id"
              class="timeline-card"
              @click="goDetail(item)"
            >
              <image :src="item.image" mode="aspectFill" class="timeline-img" />
              <view class="timeline-info">
                <text class="timeline-name">{{ item.name }}</text>
                <text class="timeline-price">¥{{ item.price }}</text>
                <text class="timeline-time">{{ item.viewTime }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>

      <view v-if="!editMode && filteredList.length > 0" class="footer-hint">
        <text class="hint-text">记录保留 90 天，最多 500 条</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { browsingHistoryApi } from '@/api'

export default {
  data() {
    return {
      editMode: false,
      selectedIds: [],
      records: [],
    }
  },

  computed: {
    filteredList() {
      return this.records
    },

    groupedList() {
      const groups = []
      const map = {}
      for (const item of this.records) {
        if (!map[item.group]) {
          map[item.group] = { dateLabel: item.dateLabel, items: [] }
          groups.push(map[item.group])
        }
        map[item.group].items.push(item)
      }
      return groups
    },
  },

  onLoad() {
    this.loadHistory()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadHistory() {
      try {
        const res = await browsingHistoryApi.getBrowsingHistory()
        this.records = res.data || []
      } catch {
        this.records = []
      }
    },

    onToggleEdit() {
      if (this.editMode) {
        this.editMode = false
        this.selectedIds = []
      } else {
        if (this.records.length === 0) return
        this.editMode = true
      }
    },

    toggleSelect(item) {
      const idx = this.selectedIds.indexOf(item.id)
      if (idx > -1) {
        this.selectedIds.splice(idx, 1)
      } else {
        this.selectedIds.push(item.id)
      }
    },

    async onDeleteSelected() {
      if (this.selectedIds.length === 0) return
      uni.showModal({
        title: '确认删除',
        content: `确定要删除选中的 ${this.selectedIds.length} 条浏览记录吗？`,
        success: async (res) => {
          if (res.confirm) {
            try {
              await Promise.all(
                this.selectedIds.map((id) => browsingHistoryApi.deleteBrowsingHistory(id)),
              )
              this.records = this.records.filter((r) => !this.selectedIds.includes(r.id))
              this.selectedIds = []
              uni.showToast({ title: '删除成功', icon: 'success' })
              if (this.records.length === 0) {
                this.editMode = false
              }
            } catch {
              uni.showToast({ title: '删除失败，请重试', icon: 'none' })
            }
          }
        },
      })
    },

    goDetail(item) {
      if (this.editMode) return
      uni.navigateTo({ url: `/pages/goods/detail?id=${item.id}` })
    },

    goShopping() {
      uni.switchTab({ url: '/pages/tabbar/home' })
    },
  },
}
</script>

<style lang="scss" scoped>
.browsing-history {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn.right {
  left: auto;
  right: 16rpx;
  width: auto;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.edit-text {
  font-size: 26rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.scroll {
  padding: 16rpx 20rpx 32rpx;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 40rpx 80rpx;
}

.empty-icon {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  margin-bottom: 48rpx;
}

.empty-box {
  position: absolute;
  inset: 0;
  border-radius: var(--radius-md);
  background: var(--color-divider);
  border: 1rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-eye {
  font-size: 64rpx;
  line-height: 1;
}

.empty-ring {
  position: absolute;
  bottom: -8rpx;
  right: -8rpx;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--color-divider);
  border: 4rpx solid var(--color-surface);
}

.empty-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  margin-bottom: 12rpx;
}

.empty-desc {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  margin-bottom: 48rpx;
}

.empty-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  padding: 0 64rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
}

.timeline-group {
  margin-bottom: 32rpx;
}

.timeline-header {
  margin-bottom: 16rpx;
}

.timeline-date {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.timeline-items {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.timeline-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.99);
  }
}

.timeline-img {
  width: 144rpx;
  height: 144rpx;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.timeline-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.timeline-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-price {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.timeline-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.edit-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.checkbox {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s ease;

  &.checked {
    background: var(--color-primary);
    border-color: var(--color-primary);
  }
}

.check-mark {
  font-size: 24rpx;
  color: #ffffff;
  font-weight: var(--font-weight-bold);
  line-height: 1;
}

.edit-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.edit-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.edit-name {
  font-size: 26rpx;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edit-price {
  font-size: 26rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.delete-bar {
  position: sticky;
  bottom: 0;
  padding: 20rpx 0;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: var(--radius-md);
  background: var(--color-danger);
}

.delete-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}

.footer-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx 0;
}

.hint-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
