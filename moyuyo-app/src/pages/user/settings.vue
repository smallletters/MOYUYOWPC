<template>
  <view class="settings">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('common.settings') }}</text>
    </view>

    <view class="content">
      <!-- 账号与安全区域
        H5 下 <navigator> 默认渲染为 <a>(inline),其内层 flex 容器在浏览器里失效,
        改为 <view @click> + uni.navigateTo 替代,样式按 .item/.section-header 预期渲染 -->
      <view class="section">
        <view class="section-header" hover-class="item-hover" @click="navTo('/pages/user/security')">
          <view class="item-left">
            <text class="item-icon luc luc-lock" />
            <text class="item-label">{{ $t('settings.security.title') }}</text>
          </view>
          <text class="chevron luc luc-chevron-right" />
        </view>
        <view class="divider indent" />
        <view class="item" hover-class="item-hover" @click="navTo('/pages/user/profile')">
          <view class="item-left">
            <text class="item-icon luc luc-user" />
            <text class="item-label">{{ $t('settings.profile.title') }}</text>
          </view>
          <text class="chevron luc luc-chevron-right" />
        </view>
        <view class="divider indent" />
        <view class="item" hover-class="item-hover" @click="navTo('/pages/user/change-password')">
          <view class="item-left">
            <text class="item-icon luc luc-phone" />
            <text class="item-label">{{ $t('settings.phone.title') }}</text>
          </view>
          <view class="item-right">
            <text class="item-value">{{ maskedPhone || $t('settings.phoneNotBound') }}</text>
            <text class="chevron luc luc-chevron-right" />
          </view>
        </view>
      </view>

      <!-- 偏好设置区域 -->
      <view class="section">
        <view class="item">
          <view class="item-left">
            <text class="item-icon luc luc-bell" />
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
            <text class="item-icon luc luc-moon" />
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
            <text class="item-icon luc luc-globe" />
            <text class="item-label">{{ $t('settings.language.title') }}</text>
          </view>
          <view class="item-right">
            <text class="item-value">{{ currentLanguageName }}</text>
            <text class="chevron luc luc-chevron-right" />
          </view>
        </view>
      </view>

      <!-- 支持区域 -->
      <view class="section">
        <view class="item" hover-class="item-hover" @click="navTo('/pages/user/help')">
          <view class="item-left">
            <text class="item-icon luc luc-help-circle" />
            <text class="item-label">{{ $t('settings.help') }}</text>
          </view>
          <text class="chevron luc luc-chevron-right" />
        </view>
        <view class="divider indent" />
        <view class="item" hover-class="item-hover" @click="navTo('/pages/user/feedback')">
          <view class="item-left">
            <text class="item-icon luc luc-message-circle" />
            <text class="item-label">{{ $t('settings.feedback') }}</text>
          </view>
          <text class="chevron luc luc-chevron-right" />
        </view>
        <view class="divider indent" />
        <view class="item" hover-class="item-hover" @click="navTo('/pages/user/about')">
          <view class="item-left">
            <text class="item-icon luc luc-info" />
            <text class="item-label">{{ $t('settings.about') }}</text>
          </view>
          <text class="chevron luc luc-chevron-right" />
        </view>
      </view>

      <!-- 退出登录按钮 -->
      <view class="logout-btn" @click="onLogout">
        <text class="logout-text">{{ $t('settings.logout') }}</text>
      </view>

      <!-- 版本信息 -->
      <text class="version">MOYUYO v1.0.0</text>
    </view>

    <!--
      语言选择弹窗(自定义实现,替代 uni.showActionSheet)
      原因:APP 端 uni.showActionSheet 走原生 plus.nativeUI.ActionSheet,
      取消按钮文本不可改,会显示硬编码的"取消/Cancel"甚至占位符,
      这里改用 Vue 模板渲染的弹层,按钮文案完全由 i18n 控制。
    -->
    <view v-if="langPickerVisible" class="lang-picker-mask" @click="closeLanguagePicker">
      <view class="lang-picker" @click.stop>
        <view class="lang-picker-header">
          <text class="lang-picker-title">{{ $t('settings.language.title') }}</text>
        </view>
        <view class="lang-picker-list">
          <view
            v-for="(lang, idx) in supportedLanguages"
            :key="lang.code"
            class="lang-picker-item"
            :class="{ active: lang.code === i18nLocale }"
            hover-class="lang-picker-item-hover"
            @click="onPickLanguage(idx)"
          >
            <text class="lang-picker-item-name">{{ lang.name }}</text>
            <text v-if="lang.code === i18nLocale" class="lang-picker-check luc luc-check" />
          </view>
        </view>
        <view class="lang-picker-cancel-wrap">
          <view class="lang-picker-cancel" hover-class="lang-picker-item-hover" @click="closeLanguagePicker">
            <text class="lang-picker-cancel-text">{{ $t('common.cancel') }}</text>
          </view>
        </view>
      </view>
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
  pageTitleKey: 'pageTitle.userSettings',

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
      // 自定义语言选择弹窗的可见性(替代 uni.showActionSheet)
      langPickerVisible: false,
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

    /**
     * 当前 locale 代码,用于弹窗内高亮当前语言项
     */
    i18nLocale() {
      void this.localeVersion
      return i18n.locale
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

    /** 统一跳转(替代 <navigator>,避免 H5 下 <a> 默认 inline 破坏 flex 布局) */
    navTo(url) {
      uni.navigateTo({ url })
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
     * 改用自定义 Vue 弹层(替代 uni.showActionSheet):
     * APP 端 uni.showActionSheet 走原生 plus.nativeUI.ActionSheet,
     * 取消按钮文本不可被 i18n 覆盖,会出现"取消"硬编码甚至占位符;
     * 自实现弹层后所有文案都走 i18n,各端一致。
     */
    showLanguagePicker() {
      this.langPickerVisible = true
    },

    /** 关闭语言选择弹窗 */
    closeLanguagePicker() {
      this.langPickerVisible = false
    },

    /**
     * 点击弹窗内某语言项:切换 locale,关闭弹窗,给提示
     */
    onPickLanguage(idx) {
      const picked = this.supportedLanguages[idx]
      this.langPickerVisible = false
      if (!picked || picked.code === i18n.locale) return
      // 切换语言:i18n 内部已自动持久化到 STORAGE_KEYS.LOCALE
      i18n.locale = picked.code
      uni.showToast({
        title: i18n.t('settings.langChanged', { name: picked.name }),
        icon: 'success',
        duration: 1500,
      })
      // 强制本页立即刷新(其他页面会通过 localeVersion 自动响应)
      this.localeVersion += 1
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

/* 顶部导航栏
   APP 端因为 navigationStyle:custom 自渲染 header,
   需要为系统状态栏(刘海/灵动岛/胶囊)预留顶部空间,
   加上 var(--status-bar-height) 与 env(safe-area-inset-top) 兜底 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: calc(88rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  /* 把内容下移避开状态栏,back-btn 仍贴顶 */
  padding-top: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  box-sizing: border-box;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  /* 跟随 header 的状态栏 padding,保持返回按钮在标题同一水平基线 */
  top: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
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

/* 行项目
  - H5 下 <navigator> 编译为 <a>,默认 inline;这里用 display:flex 强制成为 flex 容器,
    让 .item-left 与 chevron / item-right 在同一行横向排列
  - .item 与 .section-header 是 flex 容器,justify-content: space-between */
.item,
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  height: 104rpx;
  flex-wrap: nowrap;
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

/* 右侧箭头:H5 下 <text> 默认 display:block,会被 flex 容器视为独占整行的 item,导致换行
  这里显式设为 inline-flex,让其在 .item 的横向布局里与 .item-left 处于同一行 */
.chevron {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
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

/* 语言选择弹窗(自实现,绕开 uni.showActionSheet 在 APP 端无法改取消按钮文案的问题) */
.lang-picker-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}

.lang-picker {
  width: 100%;
  background: var(--color-surface);
  border-top-left-radius: 24rpx;
  border-top-right-radius: 24rpx;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
}

.lang-picker-header {
  padding: 24rpx 32rpx 8rpx;
  text-align: center;
}

.lang-picker-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}

.lang-picker-list {
  display: flex;
  flex-direction: column;
}

.lang-picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  height: 104rpx;
  border-top: 1rpx solid var(--color-divider);
}

.lang-picker-item.active .lang-picker-item-name {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.lang-picker-item-name {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.lang-picker-check {
  font-size: 36rpx;
  color: var(--color-primary);
  line-height: 1;
}

.lang-picker-cancel-wrap {
  padding: 16rpx 16rpx 24rpx;
}

.lang-picker-cancel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: var(--radius-md);
  background: var(--color-background);
}

.lang-picker-cancel-text {
  font-size: var(--font-size-base);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.lang-picker-item-hover {
  background: var(--color-divider);
  opacity: 0.7;
}
</style>