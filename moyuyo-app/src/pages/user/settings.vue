<template>
  <view class="settings">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">{{ $t('common.settings') }}</text>
    </view>

    <view class="content">
      <!-- 账号与安全区域 -->
      <view class="section">
        <navigator url="/pages/user/security" class="section-header" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-lock" /></text>
            <text class="item-label">{{ $t('settings.security.title') }}</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right" /></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/profile" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-user" /></text>
            <text class="item-label">{{ $t('settings.profile.title') }}</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right" /></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/change-password" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-phone" /></text>
            <text class="item-label">{{ $t('settings.phone.title') }}</text>
          </view>
          <view class="item-right">
            <text class="item-value">{{ maskedPhone || $t('settings.phoneNotBound') }}</text>
            <text class="chevron"><text class="luc luc-chevron-right" /></text>
          </view>
        </navigator>
      </view>

      <!-- 偏好设置区域 -->
      <view class="section">
        <view class="item">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-bell" /></text>
            <text class="item-label">{{ $t('settings.notifications') }}</text>
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
            <text class="item-icon"><text class="luc luc-moon" /></text>
            <text class="item-label">{{ $t('settings.darkMode') }}</text>
          </view>
          <switch
            class="toggle"
            :checked="darkModeEnabled"
            color="var(--color-primary)"
            @change="onDarkModeChange"
          />
        </view>
        <view class="divider indent" />
        <!--
          语言入口:点击弹出内嵌选择器(uni.showActionSheet)
          右侧展示当前 locale 的语言名(自动随 locale 切换刷新)
        -->
        <view class="item" @click="showLanguagePicker">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-globe" /></text>
            <text class="item-label">{{ $t('settings.language.title') }}</text>
          </view>
          <view class="item-right">
            <text class="item-value">{{ currentLanguageName }}</text>
            <text class="chevron"><text class="luc luc-chevron-right" /></text>
          </view>
        </view>
      </view>

      <!-- 支持区域 -->
      <view class="section">
        <navigator url="/pages/user/help" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-help-circle" /></text>
            <text class="item-label">{{ $t('settings.help') }}</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right" /></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/feedback" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-message-circle" /></text>
            <text class="item-label">{{ $t('settings.feedback') }}</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right" /></text>
        </navigator>
        <view class="divider indent" />
        <navigator url="/pages/user/about" class="item" hover-class="item-hover">
          <view class="item-left">
            <text class="item-icon"><text class="luc luc-info" /></text>
            <text class="item-label">{{ $t('settings.about') }}</text>
          </view>
          <text class="chevron"><text class="luc luc-chevron-right" /></text>
        </navigator>
      </view>

      <!-- 退出登录按钮 -->
      <view class="logout-btn" @click="onLogout">
        <text class="logout-text">{{ $t('settings.logout') }}</text>
      </view>

      <!-- 版本信息 -->
      <text class="version">MOYUYO v1.0.0</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'
import { useThemeStore } from '@/store'
import { i18n, getSupportedLanguages } from '@/i18n'
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'
import { togglePushNotification } from '@/plugins/push'

export default {
  data() {
    return {
      // 本地通知开关,默认 true;从 STORAGE_KEYS.NOTIFICATION_ENABLED 读取
      notificationEnabled: getStorage(STORAGE_KEYS.NOTIFICATION_ENABLED, true) !== false,
      // 深色模式开关,初始化为 false,由 onLoad 中按 theme store 真值覆盖
      darkModeEnabled: false,
      // locale 版本号:locale 切换时自增,触发 computed 重算
      localeVersion: 0,
      // 当前可选语言列表(从 i18n 字典动态生成)
      supportedLanguages: [],
    }
  },

  computed: {
    /**
     * 当前 locale 的"自身显示名"
     * 例:locale=zh-CN 时返回 '简体中文';locale=en-US 时返回 'English'
     * localeVersion 让 computed 响应 locale 变化
     */
    currentLanguageName() {
      void this.localeVersion
      return i18n.currentLanguageName
    },

    userStore() {
      return useUserStore()
    },

    /**
     * 脱敏手机号:138****8888
     * 优先用 userStore.userInfo.phone,登录后才有值
     * 海外号码格式不固定,统一取后 4 位前缀脱敏
     */
    maskedPhone() {
      const raw = this.userStore?.userInfo?.phone
      if (!raw) return ''
      const s = String(raw).replace(/\s+/g, '')
      if (s.length < 7) return s
      return `${s.slice(0, 3)}****${s.slice(-4)}`
    },
  },

  onLoad() {
    const themeStore = useThemeStore()
    // 从 store 取真实 darkMode 状态作为开关初始值
    this.darkModeEnabled = !!themeStore.darkMode
    // 初始化可选语言列表
    this.supportedLanguages = getSupportedLanguages()
    // 订阅 locale 变化:让 currentLanguageName 跟着刷新
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onShow() {
    // 从「我的」等页面返回时刷新一次脱敏手机号
    this.darkModeEnabled = !!useThemeStore().darkMode
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async onNotificationChange(e) {
      const next = !!e.detail.value
      this.notificationEnabled = next
      // 1. 立即持久化,避免重启 / 返回后状态丢失
      setStorage(STORAGE_KEYS.NOTIFICATION_ENABLED, next)
      // 2. 同步到原生推送通道(APP 端);失败时给出友好提示
      try {
        const ok = await togglePushNotification(next)
        // H5 端 togglePushNotification 永远 resolve false,这里只在 APP 端当真结果用
        if (next && ok === false) {
          uni.showToast({ title: i18n.t('settings.notifPermissionDenied'), icon: 'none' })
          return
        }
      } catch (err) {
        console.warn('[settings] togglePushNotification failed', err)
        if (next) {
          uni.showToast({ title: i18n.t('settings.notifPermissionDenied'), icon: 'none' })
          return
        }
      }
      uni.showToast({
        title: next ? i18n.t('settings.notifOn') : i18n.t('settings.notifOff'),
        icon: 'none',
      })
    },

    onDarkModeChange(e) {
      const next = !!e.detail.value
      this.darkModeEnabled = next
      const themeStore = useThemeStore()
      themeStore.setDarkMode(next)
      uni.showToast({
        title: next ? i18n.t('settings.darkModeOn') : i18n.t('settings.darkModeOff'),
        icon: 'none',
      })
    },

    /**
     * 显示语言选择器
     * 用 uni.showActionSheet 在 iOS/Android/H5 都原生支持,无需自实现弹窗
     */
    showLanguagePicker() {
      const itemList = this.supportedLanguages.map((l) => l.name)
      uni.showActionSheet({
        itemList,
        success: (res) => {
          const picked = this.supportedLanguages[res.tapIndex]
          if (picked && picked.code !== i18n.locale) {
            // 切换语言:i18n 内部已自动持久化到 STORAGE_KEYS.LOCALE
            i18n.locale = picked.code
            // 提示用户已切换
            uni.showToast({
              title: i18n.t('settings.langChanged', { name: picked.name }),
              icon: 'success',
              duration: 1500,
            })
            // 强制本页立即刷新(其他页面会通过 localeVersion 自动响应)
            this.localeVersion += 1
          }
        },
      })
    },

    onLogout() {
      uni.showModal({
        title: i18n.t('settings.logoutTitle'),
        content: i18n.t('settings.logoutConfirm'),
        success: async (res) => {
          if (res.confirm) {
            await this.userStore.logout()
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
