<template>
  <view class="notifications">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('notifications.title') }}</text>
      <view class="header-actions">
        <view class="header-action" @click="markAllRead">
          <text class="header-action-text">{{ $t('notifications.markAllRead') }}</text>
        </view>
        <view class="header-action" @click="clearAll">
          <text class="header-action-text danger">{{ $t('notifications.clearAll') }}</text>
        </view>
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
    <scroll-view
      class="list"
      scroll-y
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
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

          <!-- 删除：@click.stop 避免触发整条通知的跳转 -->
          <view class="notif-del" @click.stop="onDelete(item)">
            <text class="luc luc-trash-2" />
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <text class="empty-icon luc-bell" />
        <text class="empty-text">{{ $t('notifications.empty') }}</text>
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
      // scroll-view 下拉刷新状态（本页滚动由 scroll-view 接管）
      refreshing: false,
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

  onLoad(query = {}) {
    // 消息 Tab 可携带 ?tab=activity/order/system 直达对应分类
    const tab = query && query.tab
    if (['all', 'order', 'activity', 'system'].includes(tab)) {
      this.currentTab = tab
    }
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

    // 下拉刷新：重新拉取通知列表
    async onRefresh() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        await this.loadNotifications()
      } finally {
        this.refreshing = false
      }
    },

    async loadNotifications() {
      try {
        const page = await notificationApi.getNotifications()
        // 后端返回 MyBatis-Plus 分页对象（{ records, total, ... }）
        const list = (page && page.records) || []
        this.notifications = list.map((item) => this.mapNotification(item))
      } catch {
        this.notifications = []
      }
    },

    // 把后端 NotificationEntity 映射为页面展示结构
    mapNotification(item) {
      const type = String(item.type || '').toLowerCase()
      // 图标与底色按通知类型区分，未知类型回退到系统
      const visual = {
        order: { icon: 'package', iconBg: 'rgba(59,130,246,0.12)' },
        activity: { icon: 'sparkles', iconBg: 'rgba(16,185,129,0.12)' },
        system: { icon: 'bell', iconBg: 'rgba(107,114,128,0.12)' },
      }[type] || { icon: 'bell', iconBg: 'rgba(107,114,128,0.12)' }
      return {
        id: item.id,
        title: item.title || '',
        description: item.content || '',
        time: this.formatTime(item.createTime),
        // 后端 read 字段：0 未读，1 已读
        unread: item.read === 0,
        type,
        // 业务类型与业务主键：用于点击后跳转订单/售后详情
        bizType: String(item.bizType || '').toLowerCase(),
        relatedId: item.relatedId,
        icon: visual.icon,
        iconBg: visual.iconBg,
      }
    },

    // 后端 LocalDateTime 字符串格式化为 YYYY-MM-DD HH:mm
    formatTime(value) {
      if (!value) return ''
      const str = String(value).replace('T', ' ')
      return str.length >= 16 ? str.slice(0, 16) : str
    },

    async markAllRead() {
      try {
        await notificationApi.readAllNotifications()
        this.notifications.forEach((item) => {
          item.unread = false
        })
        uni.showToast({ title: i18n.t('notifications.markedAllRead'), icon: 'none' })
      } catch {
        uni.showToast({ title: i18n.t('notifications.failedRetry'), icon: 'none' })
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
      // 业务通知直接跳到对应详情页：
      // - bizType=after_sale → 售后详情（relatedId 为售后单 ID）
      // - bizType=order 或历史数据未记录 bizType 的订单通知 → 订单详情（relatedId 为订单 ID）
      // - 其它（活动/系统）保持原有消息详情页
      if (item.relatedId && item.bizType === 'after_sale') {
        uni.navigateTo({ url: `/pages/order/after-sales-detail?id=${item.relatedId}` })
        return
      }
      if (
        item.relatedId &&
        (item.bizType === 'order' || (!item.bizType && item.type === 'order'))
      ) {
        uni.navigateTo({ url: `/pages/order/detail?id=${item.relatedId}` })
        return
      }
      uni.navigateTo({ url: `/pages/user/messages?id=${item.id}` })
    },

    /** 清空当前 tab 的通知：全部 tab 清空所有，其它 tab 只清该分类 */
    clearAll() {
      const tab = this.currentTab
      if (!this.filteredList.length) {
        uni.showToast({ title: i18n.t('notifications.empty'), icon: 'none' })
        return
      }
      const tabLabel = this.tabs.find((item) => item.key === tab)?.label || ''
      uni.showModal({
        title: i18n.t('notifications.clearConfirmTitle'),
        content:
          tab === 'all'
            ? i18n.t('notifications.clearConfirmContent')
            : i18n.t('notifications.clearTabConfirmContent', { name: tabLabel }),
        success: async (res) => {
          if (!res.confirm) return
          try {
            // 非"全部"tab 时只删除该分类，后端按 UPPER(type) 忽略大小写匹配
            await notificationApi.deleteNotifications(undefined, tab === 'all' ? undefined : tab)
            this.notifications =
              tab === 'all' ? [] : this.notifications.filter((n) => n.type !== tab)
            uni.showToast({ title: i18n.t('notifications.cleared'), icon: 'success' })
          } catch {
            uni.showToast({ title: i18n.t('notifications.clearFailed'), icon: 'none' })
          }
        },
      })
    },

    /** 删除单条通知：二次确认后调用接口，成功才从列表移除 */
    onDelete(item) {
      uni.showModal({
        title: i18n.t('notifications.deleteConfirmTitle'),
        content: i18n.t('notifications.deleteConfirmContent'),
        success: async (res) => {
          if (!res.confirm) return
          try {
            await notificationApi.deleteNotification(item.id)
            this.notifications = this.notifications.filter((n) => n.id !== item.id)
            uni.showToast({ title: i18n.t('notifications.deleted'), icon: 'success' })
          } catch {
            uni.showToast({ title: i18n.t('notifications.deleteFailed'), icon: 'none' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.notifications {
  /* 视口锁定外壳：整页锁定视口高度 + 裁剪溢出，唯一滚动权威是内部 scroll-view。
     100vh 写在 100dvh 之前做兜底。约定见 goods/detail.vue。 */
  display: flex;
  flex-direction: column;
  /* 视口高度：三层渐进增强。
     1) 100vh  —— 兜底，所有环境合法
     2) 100dvh —— H5 移动浏览器动态视口（地址栏）
     3) var(--window-top) —— uni-app 顶部窗口偏移：非 custom 页 = 导航栏高 + 状态栏高，custom 页 = 0。
        必须扣除，否则顶部内容会顶到导航栏/状态栏底下。
     只减 --window-top 不减 --window-bottom：后者在 H5 被归一化为 env(safe-area-inset-bottom)，
     会与页面自身底栏的 safe-area padding 重复预留。本批无 tabBar 页，故安全。 */
  height: 100vh;
  height: 100dvh;
  height: calc(100dvh - var(--window-top, 0px));
  overflow: hidden;
  background: var(--color-background);
}

/* 顶部导航栏：常流 flex 子项，天然吸顶（sticky 已随父级 overflow:hidden 失效，删除）。
   flex-shrink:0 防止被压缩塌陷；position:relative 为 .back-btn/.header-action
   的绝对定位保留包含块（原由 sticky 提供）。 */
.header {
  position: relative;
  flex-shrink: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 顶部状态栏安全区：取 --status-bar-height 与 env(safe-area-inset-top) 较大值，避免 iOS 双重计算；第二条为老 WebView 兜底 */
  height: calc(88rpx + var(--status-bar-height, 0px));
  height: calc(88rpx + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
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

.header-actions {
  position: absolute;
  right: 8rpx;
  /* 绝对定位元素吃不到父级 padding，需自行下移状态栏高度 */
  top: var(--status-bar-height, 0px);
  top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  display: inline-flex;
  align-items: center;
  height: 72rpx;
}

.header-action {
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

.header-action-text.danger {
  color: var(--color-danger);
}

/* Tab 栏 */
.tabs {
  /* 横向 scroll-x 行：作为外壳的非滚动直接子元素，必须 flex-shrink:0，
     否则在列向 flex 中会被压缩塌陷（本方案第一大坑）。 */
  flex-shrink: 0;
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
  /* 唯一滚动权威：flex:1 占满外壳剩余空间，不再硬算高度（原 calc(100vh-176rpx) 已删除）。 */
  flex: 1;
  min-height: 0;
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

/* 删除按钮 */
.notif-del {
  flex-shrink: 0;
  width: 56rpx;
  height: 56rpx;
  margin-top: 4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: var(--color-text-tertiary);
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
