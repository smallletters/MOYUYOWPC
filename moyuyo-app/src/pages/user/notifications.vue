<template>
  <view class="notifications">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">通知</text>
      <view class="header-action" @click="markAllRead">
        <text class="header-action-text">全部已读</text>
      </view>
    </view>

    <!-- Tab 栏 -->
    <scroll-view class="tabs" scroll-x :show-scrollbar="false">
      <view class="tabs-inner">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: currentTab === tab.key }"
          @click="onTabChange(tab.key)"
        >
          <text class="tab-label">{{ tab.label }}</text>
          <view v-if="currentTab === tab.key" class="tab-indicator" />
        </view>
      </view>
    </scroll-view>

    <!-- 通知列表 -->
    <scroll-view class="list" scroll-y>
      <view v-if="filteredList.length > 0" class="list-inner">
        <view
          v-for="item in filteredList"
          :key="item.id"
          class="notification-item"
          :class="{ unread: item.unread }"
          hover-class="item-hover"
          @click="onNotificationClick(item)"
        >
          <!-- 未读指示器 -->
          <view v-if="item.unread" class="unread-dot" />
          <view v-else class="unread-dot-placeholder" />

          <!-- 图标 -->
          <view class="notif-icon" :style="{ background: item.iconBg }">
            <text class="notif-icon-text luc" :class="$luc(item.icon)" />
          </view>

          <!-- 内容 -->
          <view class="notif-content">
            <text class="notif-title">{{ item.title }}</text>
            <text class="notif-desc">{{ item.description }}</text>
            <text class="notif-time">{{ item.time }}</text>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <text class="empty-icon luc-bell" />
        <text class="empty-text">暂无通知</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { notificationApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userNotifications',

  data() {
    return {
      currentTab: 'all',
      // tabs 改为 computed
      notifications: [],
      localeVersion: 0,
    }
  },

  computed: {
    tabs() {
      void this.localeVersion
      return [
        { key: 'all', label: i18n.t('notifications.tabs.all') },
        { key: 'order', label: i18n.t('notifications.tabs.order') },
        { key: 'activity', label: i18n.t('notifications.tabs.activity') },
        { key: 'system', label: i18n.t('notifications.tabs.system') },
      ]
    },
    // 根据当前 tab 过滤后的通知列表
    filteredList() {
      if (this.currentTab === 'all') {
        return this.notifications
      }
      return this.notifications.filter((item) => item.type === this.currentTab)
    },
  },

  onLoad() {
    // 订阅语言切换，触发 tabs 重新计算
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.loadNotifications()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onTabChange(key) {
      this.currentTab = key
    },

    async loadNotifications() {
      try {
        const res = await notificationApi.getNotifications()
        this.notifications = res.data || []
      } catch {
        this.notifications = []
      }
    },

    async markAllRead() {
      try {
        await notificationApi.readAllNotifications()
        this.notifications.forEach((item) => {
          item.unread = false
        })
        uni.showToast({ title: i18n.t('notifications.markedAllRead'), icon: 'none' })
      } catch {
        uni.showToast({ title: 'Failed, please retry', icon: 'none' })
      }
    },

    async onNotificationClick(item) {
      if (item.unread) {
        try {
          await notificationApi.readNotification(item.id)
          item.unread = false
        } catch {
          // 静默失败
        }
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.notifications {
  min-height: 100vh;
  background: var(--color-background);
}

/* 顶部导航栏 */
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

.back-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.header-action {
  position: absolute;
  right: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  padding: 0 16rpx;
}

.header-action-text {
  font-size: 28rpx;
  color: var(--color-primary);
}

/* Tab 栏 */
.tabs {
  border-bottom: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.tabs-inner {
  display: flex;
  padding: 0 16rpx;
}

.tab-item {
  flex-shrink: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  padding: 0 32rpx;
}

.tab-label {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.tab-item.active .tab-label {
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-primary);
}

/* 通知列表 */
.list {
  height: calc(100vh - 176rpx);
}

.list-inner {
  padding: 24rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

/* 通知条目 */
.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx;
  border-radius: var(--radius-md);
  border: 1rpx solid var(--color-divider);
  background: var(--color-surface);
}

.notification-item.unread {
  background: var(--color-primary-light);
  border-color: transparent;
}

/* 未读指示器 */
.unread-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
  margin-top: 8rpx;
}

.unread-dot-placeholder {
  width: 16rpx;
  flex-shrink: 0;
}

/* 图标 */
.notif-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.notif-icon-text {
  font-size: 32rpx;
  line-height: 1;
}

/* 内容 */
.notif-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.notif-title {
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item:not(.unread) .notif-title {
  font-weight: var(--font-weight-medium);
}

.notif-desc {
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notif-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 320rpx;
  gap: 24rpx;
}

.empty-icon {
  font-size: 96rpx;
  line-height: 1;
}

.empty-text {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}

/* 点击态 */
.item-hover {
  opacity: 0.7;
}
</style>
