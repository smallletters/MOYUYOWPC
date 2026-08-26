<template>
  <view class="settings">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">设置</text>
    </view>

    <view class="content">
      <!-- 账号与安全区域 -->
      <view class="section">
        <navigator url="/pages/user/security" class="section-header" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-lock"></text></text>
            <text class="item-label">账号与安全</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right"></text></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/profile" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-user"></text></text>
            <text class="item-label">个人信息</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right"></text></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/change-password" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-label">手机号</text>
          </view>
          <view class="item-right">
            <text class="item-value">138****8888</text>
            <text class="chevron"><text class="luc luc-chevron-right"></text></text>
          </view>
        </navigator>
      </view>

      <!-- 偏好设置区域 -->
      <view class="section">
        <view class="item">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-bell"></text></text>
            <text class="item-label">通知设置</text>
          </view>
          <switch
            class="toggle"
            :checked="notificationEnabled"
            color="var(--color-primary)"
            @change="onNotificationChange"
          />
        </view>
        <view class="divider indent" />
        <view class="item">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-moon"></text></text>
            <text class="item-label">深色模式</text>
          </view>
          <switch
            class="toggle"
            :checked="darkModeEnabled"
            color="var(--color-primary)"
            @change="onDarkModeChange"
          />
        </view>
        <view class="divider indent" />
        <navigator url="/pages/user/language" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-globe"></text></text>
            <text class="item-label">语言</text>
          </view>
          <view class="item-right">
            <text class="item-value">简体中文</text>
            <text class="chevron"><text class="luc luc-chevron-right"></text></text>
          </view>
        </navigator>
      </view>

      <!-- 支持区域 -->
      <view class="section">
        <navigator url="/pages/user/help" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-help-circle"></text></text>
            <text class="item-label">帮助中心</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right"></text></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/feedback" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-message-circle"></text></text>
            <text class="item-label">意见反馈</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right"></text></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/about" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon">ℹ</text>
            <text class="item-label">关于我们</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right"></text></text>
        </navigator>
      </view>

      <!-- 退出登录按钮 -->
      <view class="logout-btn" @click="onLogout">
        <text class="logout-text">退出登录</text>
      </view>

      <!-- 版本信息 -->
      <text class="version">MOYUYO v1.0.0</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { useThemeStore } from '@/store'

export default {
  data() {
    return {
      notificationEnabled: true,
      darkModeEnabled: false,
    }
  },

  onLoad() {
    const themeStore = useThemeStore()
    this.darkModeEnabled = themeStore.darkMode || false
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onNotificationChange(e) {
      this.notificationEnabled = e.detail.value
      uni.showToast({
        title: this.notificationEnabled ? '通知已开启' : '通知已关闭',
        icon: 'none',
      })
    },

    onDarkModeChange(e) {
      this.darkModeEnabled = e.detail.value
      const themeStore = useThemeStore()
      themeStore.setDarkMode(this.darkModeEnabled)
    },

    onLogout() {
      uni.showModal({
        title: '提示',
        content: '确定要退出登录吗？',
        success: async (res) => {
          if (res.confirm) {
            const userStore = useUserStore()
            await userStore.logout()
            uni.reLaunch({ url: '/pages/tabbar/user' })
          }
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.settings {
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

/* 内容区 */
.content {
  padding: 32rpx 32rpx 64rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

/* 卡片区域 */
.section {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  overflow: hidden;
}

/* 行项目 */
.item,
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  height: 104rpx;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
  flex: 1;
  min-width: 0;
}

.item-icon {
  font-size: 36rpx;
  width: 40rpx;
  text-align: center;
  flex-shrink: 0;
  line-height: 1;
}

.item-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.item-value {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.chevron {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

/* 分割线 */
.divider {
  height: 1rpx;
  background: var(--color-divider);
}

.divider.indent {
  margin-left: 96rpx;
}

/* Switch 开关 */
.toggle {
  transform: scale(0.85);
  transform-origin: right center;
}

/* 退出登录按钮 */
.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
}

.logout-text {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-danger);
}

/* 版本信息 */
.version {
  display: block;
  padding-top: 32rpx;
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* 点击态 */
.item-hover {
  background: var(--color-divider);
  opacity: 0.6;
}
</style>
