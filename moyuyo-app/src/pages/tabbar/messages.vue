<template>
  <view class="messages-page">
    <!-- 顶部导航：标题 + 通知入口 + 发起私信 -->
    <view class="navbar">
      <text class="navbar-title">{{ t('messagesCenter.title') }}</text>
      <view class="navbar-actions">
        <view class="navbar-icon" @tap="goNotifications('all')">
          <text class="luc luc-bell" />
          <view v-if="unreadByType.all > 0" class="navbar-badge">
            {{ badgeText(unreadByType.all) }}
          </view>
        </view>
        <view class="navbar-icon" @tap="goGroupCreate">
          <text class="luc luc-users" />
        </view>
        <view class="navbar-icon" @tap="goStartChat">
          <text class="luc luc-user-plus" />
        </view>
      </view>
    </view>

    <!-- 未登录：先引导登录，避免接口 401 后页面空白 -->
    <view v-if="!userStore.isLoggedIn" class="login-card">
      <text class="login-title">{{ t('messagesCenter.loginTitle') }}</text>
      <view class="login-btn" @tap="goLogin">
        <text class="login-btn-text">{{ t('messagesCenter.loginAction') }}</text>
      </view>
    </view>

    <template v-else>
      <!-- 通知聚合：沿用现有通知中心类型（全部/互动/订单/系统） -->
      <view class="notice-card">
        <view
          v-for="item in noticeItems"
          :key="item.key"
          class="notice-item"
          @tap="goNotifications(item.key)"
        >
          <view class="notice-icon-wrap">
            <text class="notice-icon luc" :class="item.icon" />
            <view v-if="unreadByType[item.key] > 0" class="notice-dot" />
          </view>
          <text class="notice-label">{{ item.label }}</text>
        </view>
      </view>

      <!-- 私信会话列表（客服会话按最近消息时间混排，不单独置顶） -->
      <view class="section-head">
        <view class="section-left">
          <text class="section-title">{{ t('messagesCenter.dmTitle') }}</text>
          <view v-if="dmUnread > 0" class="section-badge">{{ badgeText(dmUnread) }}</view>
        </view>
        <text class="section-action" @tap="goStartChat">
          {{ t('messagesCenter.startChat') }}
        </text>
      </view>

      <view v-if="displayConversations.length" class="conv-list">
        <view
          v-for="conv in displayConversations"
          :key="conv.id"
          class="conv-item"
          @tap="openConversation(conv)"
          @longpress="onLongPressConversation(conv)"
        >
          <image
            v-if="conv.avatarUrl"
            :src="conv.avatarUrl"
            class="conv-avatar-img"
            mode="aspectFill"
          />
          <view v-else-if="conv.type === 'CS'" class="conv-avatar conv-avatar-cs">
            <text class="luc luc-headphones" />
          </view>
          <view v-else class="conv-avatar">{{ avatarLetter(conv) }}</view>
          <view class="conv-body">
            <view class="conv-row">
              <view class="conv-name-wrap">
                <text v-if="conv.pinned" class="conv-pin luc luc-bookmark" />
                <text class="conv-name">
                  {{ conv.name || t('messagesCenter.defaultName') }}
                </text>
              </view>
              <text class="conv-time">{{ formatTime(conv.updatedAt) }}</text>
            </view>
            <view class="conv-row">
              <text class="conv-preview">
                {{ convPreview(conv) }}
              </text>
              <view v-if="conv.unread > 0" class="conv-unread">{{ badgeText(conv.unread) }}</view>
            </view>
          </view>
        </view>
      </view>

      <!-- 会话列表分页加载 -->
      <view v-if="convHasMore" class="conv-load-more" @tap="loadConversations(false)">
        <text class="conv-load-more-text">
          {{ convLoadingMore ? t('messagesCenter.loadingMore') : t('messagesCenter.loadMore') }}
        </text>
      </view>

      <!-- 空态：引导去发起私信 -->
      <view v-if="!displayConversations.length" class="empty">
        <text class="empty-icon luc luc-message-square" />
        <text class="empty-text">{{ t('messagesCenter.dmEmpty') }}</text>
        <view class="empty-btn" @tap="goStartChat">
          <text class="empty-btn-text">{{ t('messagesCenter.startChat') }}</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import { messageApi, notificationApi, csApi } from '@/api'
import { useUserStore, useThemeStore } from '@/store'
import { i18n } from '@/i18n'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { connectDmSocket, onDmSocketEvent, syncMessageTabBadge } from '@/utils/dmSocket'

export default {
  pageTitleKey: 'pageTitle.tabbarMessages',

  data() {
    return {
      // locale 切换时自增，触发依赖文案的 computed 重算
      localeVersion: 0,
      // 本地私信会话列表
      conversations: [],
      // 会话列表分页：当前页 / 每页条数 / 总数 / 是否还有更多 / 加载中
      convPage: 1,
      convPageSize: 20,
      convTotal: 0,
      convHasMore: false,
      convLoadingMore: false,
      // 私信总未读数（后端 /unread-count）
      dmUnread: 0,
      // 各类通知未读数：all/activity/order/system
      unreadByType: { all: 0, activity: 0, order: 0, system: 0 },
      // 客服概览：最近会话 + 最后一条消息 + 未读数
      csOverview: null,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /** 模板用 t(key)：读 localeVersion 建立响应式依赖 */
    t() {
      return (key) => {
        void this.localeVersion
        return i18n.t(key)
      }
    },
    /** 通知聚合入口：类型与通知中心 tabs 对齐 */
    noticeItems() {
      void this.localeVersion
      return [
        { key: 'all', icon: 'luc-inbox', label: i18n.t('messagesCenter.noticeAll') },
        { key: 'activity', icon: 'luc-sparkles', label: i18n.t('messagesCenter.noticeActivity') },
        { key: 'order', icon: 'luc-package', label: i18n.t('messagesCenter.noticeOrder') },
        { key: 'system', icon: 'luc-bell', label: i18n.t('messagesCenter.noticeSystem') },
      ]
    },
    /** 客服未读数 */
    csUnread() {
      return Number((this.csOverview && this.csOverview.unreadCount) || 0)
    },
    /** 客服最近消息时间 */
    csLastAt() {
      const overview = this.csOverview
      if (!overview) return ''
      return (
        overview.lastMessageAt || (overview.lastMessage && overview.lastMessage.createTime) || ''
      )
    },
    /** 客服最近消息摘要：卡片/图片消息用占位文案 */
    csPreview() {
      void this.localeVersion
      const msg = this.csOverview && this.csOverview.lastMessage
      if (!msg) return ''
      const type = String(msg.contentType || 'TEXT').toUpperCase()
      if (type === 'PRODUCT') return i18n.t('messagesCenter.csProductCard')
      if (type === 'ORDER') return i18n.t('messagesCenter.csOrderCard')
      if (type === 'IMAGE') return i18n.t('messagesCenter.imageMessage')
      return msg.content || ''
    },
    /** 客服会话：仅在客服有消息时混入私信列表，无消息不展示 */
    csConversation() {
      void this.localeVersion
      const overview = this.csOverview || {}
      if (!overview.sessionId || !overview.lastMessage) return null
      return {
        id: 'cs-center',
        type: 'CS',
        sessionId: String(overview.sessionId),
        status: overview.status || '',
        name: i18n.t('messagesCenter.csTitle'),
        avatar: '',
        avatarUrl: '',
        lastMessage: this.csPreview,
        lastMessageType: 'TEXT',
        updatedAt: this.csLastAt,
        unread: this.csUnread,
        pinned: false,
      }
    },
    /** 私信列表 + 客服会话：置顶优先，其余按最近消息时间倒序 */
    displayConversations() {
      const list = this.conversations.slice()
      const csConv = this.csConversation
      if (csConv) list.push(csConv)
      return list.sort((a, b) => {
        if (!!a.pinned !== !!b.pinned) return a.pinned ? -1 : 1
        return this.timeValue(b.updatedAt) - this.timeValue(a.updatedAt)
      })
    },
  },

  onLoad() {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    // 订阅私信 WebSocket：收到新消息后防抖刷新列表与未读数
    this._unsubDmSocket = onDmSocketEvent(() => {
      if (this._dmRefreshTimer) clearTimeout(this._dmRefreshTimer)
      this._dmRefreshTimer = setTimeout(() => {
        this.loadConversations()
        this.loadDmUnread().then(() => this.syncTabBarBadge())
      }, 300)
    })
  },

  onShow() {
    // 原生 tabBar 不吃 CSS 变量，深色模式下必须在这里重新上色
    useThemeStore().syncTabBar()
    // 进入消息页建立 WebSocket（单例，重复调用无副作用）
    connectDmSocket()
    this.refresh()
  },

  onUnload() {
    if (this._unsubLocale) {
      this._unsubLocale()
      this._unsubLocale = null
    }
    if (this._unsubDmSocket) {
      this._unsubDmSocket()
      this._unsubDmSocket = null
    }
    if (this._dmRefreshTimer) {
      clearTimeout(this._dmRefreshTimer)
      this._dmRefreshTimer = null
    }
  },

  onPullDownRefresh() {
    this.refresh().finally(() => uni.stopPullDownRefresh())
  },

  /** 触底加载更多会话（后端分页） */
  onReachBottom() {
    this.loadConversations(false)
  },

  methods: {
    /** 刷新会话列表 + 私信未读数 + 通知未读数 */
    async refresh() {
      // 未登录时清空数据，避免残留上一次登录态的内容
      if (!this.userStore.isLoggedIn) {
        this.conversations = []
        this.dmUnread = 0
        this.csOverview = null
        this.unreadByType = { all: 0, activity: 0, order: 0, system: 0 }
        this.syncTabBarBadge()
        return
      }
      await Promise.all([
        this.loadConversations(),
        this.loadUnread(),
        this.loadDmUnread(),
        this.loadCsOverview(),
      ])
      this.syncTabBarBadge()
    },

    /** 拉取客服概览（最近会话 + 最后一条消息 + 未读数） */
    async loadCsOverview() {
      try {
        this.csOverview = await csApi.getOverview()
      } catch (e) {
        // 客服接口异常不影响通知与私信展示
        this.csOverview = null
      }
    },

    /** 进入客服会话：优先复用未关闭的最近会话，避免新建会话后丢失历史消息 */
    openCs() {
      if (!this.userStore.isLoggedIn) {
        this.goLogin()
        return
      }
      const { sessionId, status } = this.csConversation || {}
      const canResume = sessionId && status !== 'CLOSED'
      const url = canResume
        ? `/pages/user/customer-service?sessionId=${sessionId}`
        : '/pages/user/customer-service'
      uni.navigateTo({ url })
    },

    /** 会话时间转毫秒：无效时间按 0 处理，保证排序稳定 */
    timeValue(time) {
      if (!time) return 0
      const ms = new Date(time).getTime()
      return Number.isNaN(ms) ? 0 : ms
    },

    /** 后端会话列表：映射为页面展示结构（reset=true 重置到第一页） */
    async loadConversations(reset = true) {
      if (this.convLoadingMore) return
      if (!reset && !this.convHasMore) return
      const pageNo = reset ? 1 : this.convPage + 1
      if (!reset) this.convLoadingMore = true
      try {
        const page = await messageApi.listConversations({ page: pageNo, size: this.convPageSize })
        const records = (page && page.records) || []
        const mapped = records.map((conv) => ({
          id: String(conv.id),
          type: conv.type || 'SINGLE',
          targetUserId: conv.targetUserId ? String(conv.targetUserId) : '',
          name: conv.nickname || '',
          avatar: conv.avatar || '',
          avatarUrl: conv.avatar ? toAbsoluteImageUrl(conv.avatar) : '',
          lastMessage: conv.lastMessage || '',
          lastMessageType: conv.lastMessageType || 'TEXT',
          updatedAt: conv.lastMessageAt || conv.createTime || 0,
          unread: Number(conv.unreadCount || 0),
          // 置顶状态（后端已按置顶优先排序）
          pinned: !!conv.pinned,
        }))
        if (reset) {
          this.conversations = mapped
        } else {
          // 追加时按 id 去重，避免与 WebSocket 触发的刷新交叉产生重复项
          const existing = new Set(this.conversations.map((c) => c.id))
          this.conversations = this.conversations.concat(mapped.filter((c) => !existing.has(c.id)))
        }
        this.convPage = pageNo
        this.convTotal = Number((page && page.total) || 0)
        this.convHasMore = this.conversations.length < this.convTotal
      } catch (e) {
        console.warn('[messages] load conversations failed', e)
        if (reset) {
          this.conversations = []
          this.convTotal = 0
          this.convHasMore = false
        }
      } finally {
        this.convLoadingMore = false
      }
    },

    /** 私信总未读数（用于消息区块角标与 tabBar 红点） */
    async loadDmUnread() {
      try {
        this.dmUnread = Number((await messageApi.unreadCount()) || 0)
      } catch (e) {
        this.dmUnread = 0
      }
    },

    /** 同步消息 Tab 红点：只统计私信未读（通知未读由铃铛角标单独展示） */
    syncTabBarBadge() {
      syncMessageTabBadge(this.dmUnread)
    },

    /** 拉取通知列表并计算各类型未读数 */
    async loadUnread() {
      try {
        const page = await notificationApi.getNotifications({ page: 1, size: 50 })
        const records = (page && page.records) || []
        const counts = { all: 0, activity: 0, order: 0, system: 0 }
        records.forEach((item) => {
          // 后端 read: 0 未读 / 1 已读；兼容布尔值
          const unread = item.read === 0 || item.read === false
          if (!unread) return
          const type = String(item.type || '').toLowerCase()
          counts.all += 1
          if (counts[type] !== undefined) counts[type] += 1
        })
        // 整体替换对象，确保 Vue 2 响应式更新
        this.unreadByType = counts
      } catch (e) {
        // 未登录 401 / 网络异常时静默清零，不阻断会话列表展示
        this.unreadByType = { all: 0, activity: 0, order: 0, system: 0 }
      }
    },

    /** 进入通知中心；tab 非 all 时带查询参数直达对应分类 */
    goNotifications(tab) {
      const suffix = tab && tab !== 'all' ? `?tab=${tab}` : ''
      uni.navigateTo({ url: `/pages/user/notifications${suffix}` })
    },

    /** 发起私信：从关注列表选择聊天对象（列表项已自带私信入口） */
    goStartChat() {
      if (!this.userStore.isLoggedIn) {
        this.goLogin()
        return
      }
      uni.navigateTo({ url: '/pages/user/follow-list?mode=following' })
    },

    goLogin() {
      uni.navigateTo({ url: '/pages/user/login' })
    },

    /** 发起群聊：从已有私信联系人中选择成员 */
    goGroupCreate() {
      if (!this.userStore.isLoggedIn) {
        this.goLogin()
        return
      }
      uni.navigateTo({ url: '/pages/community/dm-group-create' })
    },

    /**
     * 打开已有会话
     * 注意：dm-chat 的 q.id 约定为「目标用户ID」，不是会话ID；
     * dm-chat 会按目标用户幂等 ensureConversation 后再拉消息。
     */
    openConversation(conv) {
      if (!conv) return
      // 客服会话：进入客服中心并复用会话
      if (conv.type === 'CS') {
        this.openCs()
        return
      }
      const name = conv.name || ''
      const letter = this.avatarLetter(conv)
      // 群聊：q.id 是会话ID（groupId），单聊：q.id 是目标用户ID
      if (conv.type === 'GROUP') {
        uni.navigateTo({
          url: `/pages/community/dm-chat?groupId=${conv.id}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(letter)}`,
        })
        return
      }
      if (!conv.targetUserId) return
      uni.navigateTo({
        url: `/pages/community/dm-chat?id=${conv.targetUserId}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(letter)}`,
      })
    },

    /** 长按会话：置顶/取消置顶 + 删除会话 */
    onLongPressConversation(conv) {
      if (!conv) return
      // 客服会话不支持置顶/删除（由后台会话管理）
      if (conv.type === 'CS') return
      const pinLabel = conv.pinned
        ? this.t('messagesCenter.unpinConversation')
        : this.t('messagesCenter.pinConversation')
      uni.showActionSheet({
        itemList: [pinLabel, this.t('messagesCenter.deleteConversation')],
        success: (res) => {
          if (res.tapIndex === 0) this.togglePin(conv)
          else if (res.tapIndex === 1) this.confirmDeleteConversation(conv)
        },
      })
    },

    /** 置顶/取消置顶：成功后重新拉取列表，保证排序立即生效 */
    async togglePin(conv) {
      try {
        await messageApi.pinConversation(conv.id, !conv.pinned)
        conv.pinned = !conv.pinned
        await this.loadConversations()
        uni.showToast({
          title: conv.pinned
            ? this.t('messagesCenter.pinnedToast')
            : this.t('messagesCenter.unpinnedToast'),
          icon: 'none',
        })
      } catch (e) {
        uni.showToast({ title: this.t('messagesCenter.operateFailed'), icon: 'none' })
      }
    },

    /** 删除会话前的二次确认 */
    confirmDeleteConversation(conv) {
      uni.showModal({
        title: this.t('messagesCenter.deleteConfirmTitle'),
        content: this.t('messagesCenter.deleteConfirmContent'),
        confirmText: this.t('common.confirm'),
        cancelText: this.t('common.cancel'),
        success: (res) => {
          if (res.confirm) this.removeConversation(conv)
        },
      })
    },

    /** 删除会话（仅自己隐藏）：本地移除并同步未读角标 */
    async removeConversation(conv) {
      try {
        await messageApi.deleteConversation(conv.id)
        this.conversations = this.conversations.filter((c) => c.id !== conv.id)
        await this.loadDmUnread()
        this.syncTabBarBadge()
        uni.showToast({ title: this.t('messagesCenter.deletedToast'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: this.t('messagesCenter.operateFailed'), icon: 'none' })
      }
    },

    /** 会话头像占位字母 */
    avatarLetter(conv) {
      if (!conv) return 'U'
      if (conv.name) return conv.name.charAt(0).toUpperCase()
      return 'U'
    },

    /** 超过 99 显示 99+ */
    badgeText(n) {
      return n > 99 ? '99+' : String(n)
    },

    /** 会话预览：媒体消息本地化占位，文本消息直接展示后端摘要 */
    convPreview(conv) {
      if (!conv) return ''
      const type = String(conv.lastMessageType || 'TEXT').toUpperCase()
      if (type === 'IMAGE') return i18n.t('messagesCenter.imageMessage')
      if (type === 'VOICE') return i18n.t('messagesCenter.voiceMessage')
      if (type === 'FILE') return i18n.t('messagesCenter.fileMessage')
      return conv.lastMessage || ''
    },

    /** 会话最近时间：相对时间 + 日期回退（复用 userCenter.time 文案） */
    formatTime(time) {
      if (!time) return ''
      void this.localeVersion
      const d = new Date(time)
      if (Number.isNaN(d.getTime())) return ''
      const diff = Date.now() - d.getTime()
      if (diff < 60_000) return i18n.t('userCenter.time.justNow')
      if (diff < 3_600_000) {
        return i18n.t('userCenter.time.minutesAgo', { count: Math.floor(diff / 60_000) })
      }
      if (diff < 86_400_000) {
        return i18n.t('userCenter.time.hoursAgo', { count: Math.floor(diff / 3_600_000) })
      }
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return i18n.t('userCenter.time.dateFmt', { y, m, d: day })
    },
  },
}
</script>

<style lang="scss" scoped>
.messages-page {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

/* 顶部导航栏 */
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  /* 状态栏安全区：取 status-bar-height 与 env 的较大值，避免重复叠加 */
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  min-height: calc(44px + var(--status-bar-height, 0px));
  min-height: calc(44px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  background: var(--color-surface);
  box-sizing: border-box;
  position: sticky;
  top: 0;
  z-index: 20;
}
.navbar-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.navbar-actions {
  display: flex;
  align-items: center;
  gap: 28rpx;
}
.navbar-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
}
.navbar-icon .luc {
  font-size: 40rpx;
  color: var(--color-text);
}
.navbar-badge {
  position: absolute;
  top: -4rpx;
  right: -8rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #ff4d4f;
  color: #fff;
  font-size: 20rpx;
  line-height: 28rpx;
  text-align: center;
  box-sizing: border-box;
}

/* 未登录卡片 */
.login-card {
  margin: 24rpx;
  padding: 48rpx 32rpx;
  border-radius: 24rpx;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}
.login-title {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}
.login-btn {
  padding: 18rpx 48rpx;
  border-radius: 999rpx;
  background: var(--color-primary, #18b367);
}
.login-btn-text {
  font-size: var(--font-size-base);
  color: #fff;
}

/* 通知聚合卡 */
.notice-card {
  display: flex;
  margin: 16rpx 24rpx;
  padding: 28rpx 0;
  border-radius: 24rpx;
  background: var(--color-surface);
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
}
.notice-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.notice-icon-wrap {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  /* 用中性底色，避免主色是浅金时图标与浅绿底对比度过低 */
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;
}
.notice-icon {
  font-size: 36rpx;
  color: var(--color-text);
}
.notice-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ff4d4f;
}
.notice-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

/* 私信区块标题 */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 24rpx 12rpx;
}
.section-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.section-badge {
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: #ff4d4f;
  color: #fff;
  font-size: 20rpx;
  line-height: 32rpx;
  text-align: center;
  box-sizing: border-box;
}
.section-action {
  font-size: var(--font-size-sm);
  color: var(--color-primary, #18b367);
}

/* 会话列表 */
.conv-list {
  margin: 0 24rpx;
  border-radius: 24rpx;
  background: var(--color-surface);
  overflow: hidden;
}
.conv-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
}
.conv-item:last-child {
  border-bottom: none;
}
.conv-item:active {
  background: rgba(0, 0, 0, 0.04);
}
.conv-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--color-primary, #18b367);
  color: #fff;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.conv-avatar-img {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-background);
}
/* 客服会话头像：耳机图标代替文字 */
.conv-avatar-cs .luc {
  font-size: 40rpx;
  color: #fff;
}
.conv-unread {
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: #ff4d4f;
  color: #fff;
  font-size: 20rpx;
  line-height: 32rpx;
  text-align: center;
  flex-shrink: 0;
  box-sizing: border-box;
}
.conv-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.conv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}
.conv-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
/* 会话名称行：置顶图标 + 名称 */
.conv-name-wrap {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex: 1;
  min-width: 0;
}
.conv-pin {
  font-size: 24rpx;
  color: var(--color-primary);
  flex-shrink: 0;
}
/* 会话列表分页加载 */
.conv-load-more {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
}
.conv-load-more-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.conv-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}
.conv-preview {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 空态 */
.empty {
  margin: 0 24rpx;
  padding: 96rpx 0;
  border-radius: 24rpx;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.empty-icon {
  font-size: 96rpx;
  color: var(--color-text-tertiary);
  opacity: 0.4;
}
.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
.empty-btn {
  margin-top: 8rpx;
  padding: 16rpx 44rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--color-primary, #18b367);
}
.empty-btn-text {
  font-size: var(--font-size-sm);
  color: var(--color-primary, #18b367);
}
</style>
