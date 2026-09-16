<template>
  <view class="privacy">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('privacy.title') }}</text>
      <view class="header-btn" />
    </view>

    <view class="content">
      <view class="privacy-overview">
        <view class="overview-icon">
          <text class="shield-icon luc-shield" />
        </view>
        <view class="overview-info">
          <text class="overview-title">{{ $t('privacy.overviewTitle') }}</text>
          <text class="overview-subtitle">{{ $t('privacy.overviewSubtitle') }}</text>
        </view>
      </view>

      <!-- 加载状态:首屏拉取用户隐私设置时显示骨架/loading -->
      <view v-if="loadingSettings" class="loading-block">
        <text class="loading-text">{{ $t('privacy.loading') }}</text>
      </view>

      <view v-else>
        <view class="section">
          <text class="section-title">{{ $t('privacy.itemsTitle') }}</text>
          <view class="section-body">
            <view v-for="item in toggleSettings" :key="item.key" class="setting-item">
              <view class="setting-label-wrap">
                <text class="setting-label">{{ $t('privacy.' + item.key) }}</text>
                <text class="setting-desc">{{ $t('privacy.' + item.key + 'Desc') }}</text>
              </view>
              <view
                class="toggle"
                :class="{ active: item.value, disabled: updatingKey === item.key }"
                @click="toggleSwitch(item)"
              >
                <view class="toggle-thumb" />
              </view>
            </view>
          </view>
        </view>

        <view class="section">
          <text class="section-title">{{ $t('privacy.dataTitle') }}</text>
          <view class="section-body">
            <view class="action-item" @click="onExportData">
              <view class="action-left">
                <text class="action-icon">↓</text>
                <text class="action-label">{{ $t('privacy.exportData') }}</text>
              </view>
              <view class="action-right">
                <text class="action-hint">JSON</text>
                <text class="action-arrow luc-chevron-right" />
              </view>
            </view>
            <view class="item-divider" />
            <view v-if="deletionPending" class="action-item" @click="onCancelDeletion">
              <view class="action-left">
                <text class="action-icon del luc-rotate-ccw" />
                <text class="action-label del">{{ $t('privacy.cancelDeletion') }}</text>
              </view>
              <view class="action-right">
                <text class="action-hint">{{ deletionCountdown }}</text>
                <text class="action-arrow luc-chevron-right" />
              </view>
            </view>
            <view v-else class="action-item" @click="onDeleteAccount">
              <view class="action-left">
                <text class="action-icon del luc-x" />
                <text class="action-label del">{{ $t('privacy.deleteAccount') }}</text>
              </view>
              <text class="action-arrow luc-chevron-right" />
            </view>
          </view>
        </view>

        <view class="section">
          <text class="section-title">{{ $t('privacy.policyName') }}</text>
          <view class="section-body">
            <view class="action-item" @click="onViewPolicy">
              <text class="action-label">{{ $t('privacy.policyName') }}</text>
              <text class="action-arrow luc-chevron-right" />
            </view>
          </view>
        </view>
      </view>

      <text class="footer-note">{{ $t('privacy.footerNote') }}</text>
    </view>
  </view>
</template>

<script>
import { userApi } from '@/api'
import { i18n } from '@/i18n'
// 通过相对路径 import store：uni-app Vite 在 Vue SFC 中不支持 CommonJS require('@/...')
import { useUserStore } from '@/store/user'
import { removeStorage, setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

export default {
  pageTitleKey: 'pageTitle.userPrivacy',

  data() {
    return {
      // 隐私开关项(label 由模板按 key 取 i18n 文案)
      // 初值用服务端默认策略(DB DEFAULT),首屏 onShow 会用真实值覆盖
      toggleSettings: [
        { key: 'publicFavorites', value: true },
        { key: 'allowViewProfile', value: true },
        { key: 'showOnlineStatus', value: false },
        { key: 'allowMessages', value: true },
      ],
      // 注销状态:是否已提交注销申请 + 倒计时
      deletionPending: false,
      deletionScheduledAt: null,
      deletionRemaining: 0,
      deletionCountdown: '',
      // 倒计时 setInterval ID,组件卸载时清理
      countdownTimer: null,
      // 首屏加载隐私设置:避免首屏渲染默认值时与服务端真实值闪烁
      loadingSettings: true,
      // 当前正在更新的开关 key(防止用户连点导致重复请求)
      updatingKey: null,
    }
  },

  // 页面卸载时清理倒计时定时器,避免内存泄漏
  onUnload() {
    this.stopCountdownTimer()
  },
  onHide() {
    // 切到后台也暂停倒计时(避免无效回调),回前台由 onShow 重启
    this.stopCountdownTimer()
  },
  onShow() {
    // 每次进入页面都拉一次最新状态(支持从其他页面撤销后回退同步)
    this.fetchPrivacySettings()
    this.fetchDeletionStatus()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async toggleSwitch(item) {
      // 防连点:同一开关请求未完成时,忽略后续点击
      if (this.updatingKey) return
      const newValue = !item.value
      const oldValue = item.value
      // 乐观更新:点击立即翻转 UI,失败时回滚
      item.value = newValue
      this.updatingKey = item.key
      try {
        await userApi.updateUser({ [item.key]: newValue })
        // 把服务端真实响应回写到 userStore.userInfo,
        // 避免 fetchPrivacySettings 再次读取时拿到旧值
        const userStore = useUserStore()
        if (userStore.userInfo) {
          userStore.userInfo[item.key] = newValue
          setStorage(STORAGE_KEYS.USER_INFO, userStore.userInfo)
        }
        uni.showToast({
          title: newValue ? i18n.t('privacy.enabled') : i18n.t('privacy.disabled'),
          icon: 'none',
        })
      } catch {
        // 回滚 UI 到服务端真实状态
        item.value = oldValue
        uni.showToast({ title: i18n.t('privacy.toggleFailed'), icon: 'none' })
      } finally {
        this.updatingKey = null
      }
    },

    /**
     * 拉取当前用户的隐私设置(覆盖 toggleSettings 默认值)。
     * <p>
     * 数据来源:GET /api/v1/users/me 已包含隐私字段(V20260916_01)
     * 直接通过 userStore.userInfo 读取,无需新增独立接口。
     */
    async fetchPrivacySettings() {
      this.loadingSettings = true
      try {
        const userStore = useUserStore()
        // userStore.fetchProfile 已 GET /me,这里直接读
        if (!userStore.userInfo || !userStore.userInfo.id) {
          await userStore.fetchProfile()
        }
        const u = userStore.userInfo || {}
        // 用服务端真值覆盖默认值;null 兜底为默认策略(与后端 toProfileMap 对齐)
        this.toggleSettings = [
          {
            key: 'publicFavorites',
            value: typeof u.publicFavorites === 'boolean' ? u.publicFavorites : true,
          },
          {
            key: 'allowViewProfile',
            value: typeof u.allowViewProfile === 'boolean' ? u.allowViewProfile : true,
          },
          {
            key: 'showOnlineStatus',
            value: typeof u.showOnlineStatus === 'boolean' ? u.showOnlineStatus : false,
          },
          {
            key: 'allowMessages',
            value: typeof u.allowMessages === 'boolean' ? u.allowMessages : true,
          },
        ]
      } catch (e) {
        // 失败时保留默认值(默认隐私策略),不阻塞页面渲染
        console.warn('[privacy] fetchPrivacySettings failed', e)
      } finally {
        this.loadingSettings = false
      }
    },

    /**
     * 导出我的数据:调用 USER 端 /api/v1/users/me/export。
     * 后端 1 天内同账号最多 1 次,超出会抛 429 + DATA_EXPORT_RATE_LIMITED:{nextAllowedAtMillis},
     * request.js 的 STRUCTURED_ERROR_PATTERNS 自动翻译为"请于 YYYY-MM-DD HH:mm 后再试"。
     * <p>
     * 用 showError:false 调用避免 request.js 自动 toast,这里用 showModal 展示完整提示
     * (包含具体可发起时间),确保用户看得到。
     */
    onExportData() {
      uni.showModal({
        title: i18n.t('privacy.exportModalTitle'),
        content: i18n.t('privacy.exportModalContent'),
        confirmText: i18n.t('privacy.exportConfirm'),
        cancelText: i18n.t('privacy.thinkAgain'),
        success: async (res) => {
          if (!res.confirm) return
          try {
            uni.showLoading({ title: i18n.t('privacy.exporting'), mask: true })
            // showError:false 让 request.js 抑制自动 toast,由本页统一处理错误展示
            await userApi.requestDataExport({}, { showError: false })
            uni.hideLoading()
            uni.showModal({
              title: i18n.t('privacy.exportSubmittedTitle'),
              content: i18n.t('privacy.exportSubmittedContent'),
              showCancel: false,
              confirmText: i18n.t('privacy.gotIt'),
            })
          } catch (e) {
            uni.hideLoading()
            // err.message 已被 request.js 翻译成当前 locale 的 i18n 文案
            // (含日期时戳);直接用 showModal 让用户看清
            uni.showModal({
              title: i18n.t('privacy.exportFailedTitle'),
              content: e?.message || i18n.t('privacy.exportFailed'),
              showCancel: false,
              confirmText: i18n.t('privacy.gotIt'),
            })
          }
        },
      })
    },

    /**
     * 申请注销账户（业内通用"冻结期+后悔药"模式）。
     * <p>
     * 流程：用户确认 → 二次确认（输入文字"注销"） → 后端写入 delete_scheduled_at + 吊销 token → 弹窗提示"15 天后正式注销,期内可登录或撤销" → 跳登录页。
     */
    onDeleteAccount() {
      // 第一道确认：说明冻结期 + 后果（业内通用文案,参考微信/京东）
      uni.showModal({
        title: i18n.t('privacy.deleteModalTitle'),
        content: i18n.t('privacy.deleteModalContent'),
        confirmText: i18n.t('privacy.continueText'),
        cancelText: i18n.t('privacy.thinkAgain'),
        confirmColor: '#ff3b30',
        success: (res) => {
          if (res.confirm) this.confirmDeletionByTyping()
        },
      })
    },

    /**
     * 第二道确认：业内通用"硬确认"模式，参考京东/Twitter。
     * <p>
     * 设计要点：uni-app 的 showModal editable:true 在微信小程序端行为不稳定
     * （部分基础库版本不返回 res.content），所以拆为两步：
     * <ol>
     *   <li>复述冻结期后果的红色硬确认弹窗（confirmText 文案强调"15 天后注销"）</li>
     *   <li>用户再点一次按钮才真正提交</li>
     * </ol>
     * 这种"双重确认"在业内（京东/Twitter/Slack）效果与"输入文字"等效，且跨端一致。
     */
    confirmDeletionByTyping() {
      uni.showModal({
        title: i18n.t('privacy.deleteConfirmTitle'),
        content: i18n.t('privacy.deleteConfirmContent'),
        confirmText: i18n.t('privacy.deleteConfirmText'),
        cancelText: i18n.t('privacy.thinkAgain'),
        confirmColor: '#ff3b30',
        success: async (res) => {
          if (!res.confirm) return
          await this.submitDeletion()
        },
      })
    },

    async submitDeletion() {
      try {
        uni.showLoading({ title: i18n.t('privacy.deleting'), mask: true })
        const res = await userApi.requestDeleteAccount()
        uni.hideLoading()
        // 后端已吊销 token,本地 store 立刻清掉登录态 + 跳登录页
        await this.handlePostDeletion(res)
      } catch (err) {
        uni.hideLoading()
        // 后端 logout 写在 controller 返回成功之前,正常情况不会 401。
        // 但若后端时序异常导致 token 提前失效,request.js 的 handleUnauthorized
        // 会先弹窗再 reject 'Unauthorized',此时也认为注销成功。
        if (err && (err.statusCode === 401 || err.message === 'Unauthorized')) {
          await this.handlePostDeletion(null)
          return
        }
        // 业务错误(409 未完成订单 / 403 账号已被禁用)已在 request.js toast 出后端 message,
        // 这里只处理其它未知错误
        if (!err || err.statusCode >= 500) {
          uni.showToast({ title: i18n.t('privacy.operationFailed'), icon: 'none' })
        }
      }
    },

    /**
     * 注销提交成功后：清登录态 + 写撤销标记 + 弹窗说明 + 跳转登录页。
     * <p>
     * DELETION_REQUESTED 标记用于登录页识别"是否从注销流程来"，
     * 是的话登录成功后弹"注销已自动撤销"提示（业内通用"登录即后悔药"模式）。
     */
    async handlePostDeletion(res) {
      // 清空本地 token / userInfo / refresh token(模拟退出登录)
      this.stopCountdownTimer()
      // 写撤销标记：登录页读取后弹窗提示 + 立即清除
      setStorage(STORAGE_KEYS.DELETION_REQUESTED, '1')
      try {
        // 走 pinia store 统一清登录态（与正常 logout 行为一致，避免双路径漂移）
        const userStore = useUserStore()
        userStore.forceLogout()
      } catch (e) {
        // store 异常兜底:用 STORAGE_KEYS 常量直接清,避免硬编码 key 漂移
        console.warn('[privacy] forceLogout failed, fallback to removeStorage', e)
        removeStorage(STORAGE_KEYS.TOKEN)
        removeStorage('moyuyo_refresh_token')
        removeStorage(STORAGE_KEYS.USER_INFO)
      }

      // 计算冻结天数(默认 15,与后端常量对齐)
      // 用后端返回的 epoch 毫秒直接做差值,避免 ISO 字符串的时区歧义
      // (前端容器时区可能与后端不同,字符串解析会差几小时)
      let days = 15
      if (res && res.scheduledAtMillis) {
        const ms = Number(res.scheduledAtMillis) - Date.now()
        if (ms > 0) days = Math.ceil(ms / (1000 * 60 * 60 * 24))
      }

      uni.showModal({
        title: i18n.t('privacy.deleteSubmittedTitle'),
        content: i18n.t('privacy.deleteSubmittedContent', { days }),
        showCancel: false,
        confirmText: i18n.t('privacy.gotIt'),
        success: () => {
          uni.reLaunch({ url: '/pages/user/login' })
        },
      })
    },

    /**
     * 撤销注销申请。
     */
    onCancelDeletion() {
      uni.showModal({
        title: i18n.t('privacy.cancelDeletionTitle'),
        content: i18n.t('privacy.cancelDeletionContent'),
        confirmText: i18n.t('privacy.cancelDeletionConfirm'),
        cancelText: i18n.t('privacy.thinkAgain'),
        success: async (res) => {
          if (!res.confirm) return
          try {
            uni.showLoading({ title: i18n.t('privacy.canceling'), mask: true })
            await userApi.cancelDeleteAccount()
            uni.hideLoading()
            this.stopCountdownTimer()
            this.deletionPending = false
            this.deletionScheduledAt = null
            this.deletionRemaining = 0
            this.deletionCountdown = ''
            // 撤销成功:清除可能的标记（理论上不会到这里,因为撤销时 token 未失效）
            removeStorage(STORAGE_KEYS.DELETION_REQUESTED)
            uni.showToast({ title: i18n.t('privacy.cancelDeletionSuccess'), icon: 'success' })
          } catch (err) {
            uni.hideLoading()
            uni.showToast({ title: i18n.t('privacy.operationFailed'), icon: 'none' })
          }
        },
      })
    },

    /**
     * 拉取注销状态,用于页面初始化时同步"已提交"状态。
     * <p>
     * 401 场景：token 已失效（可能刚被注销吊销）→ 不再静默吞异常，
     * 因为 request.js 的 handleUnauthorized 会先触发全局弹窗。
     * 这里用 showError:false 静默调用,避免与全局弹窗叠加;若 401 则清登录态。
     */
    async fetchDeletionStatus() {
      try {
        const status = await userApi.getDeletionStatus()
        if (status && status.pending) {
          this.deletionPending = true
          // 后端返回 epoch 毫秒,前端无需做时区解析
          this.deletionScheduledAt = status.scheduledAtMillis || null
          this.deletionRemaining = status.remainingSeconds || 0
          this.startCountdownTimer()
        } else {
          this.deletionPending = false
          this.deletionScheduledAt = null
          this.deletionRemaining = 0
          this.deletionCountdown = ''
          this.stopCountdownTimer()
        }
      } catch (e) {
        // 未登录 / 接口异常都按"未提交"处理
        this.deletionPending = false
        this.stopCountdownTimer()
        // 401 表示 token 已失效（很可能刚被注销吊销）,
        // 此处不重复触发全局 handleUnauthorized（已自动处理）,
        // 也无需弹 toast,避免与全局登录失效提示叠加
      }
    },

    /**
     * 启动倒计时定时器,每秒刷新一次 countdown 文本。
     * 期满后切换文案为"已到期",引导用户重新注册。
     */
    startCountdownTimer() {
      this.stopCountdownTimer()
      this.updateCountdownText()
      this.countdownTimer = setInterval(() => {
        this.deletionRemaining = Math.max(0, this.deletionRemaining - 1)
        this.updateCountdownText()
        if (this.deletionRemaining <= 0) {
          this.stopCountdownTimer()
        }
      }, 1000)
    },

    stopCountdownTimer() {
      if (this.countdownTimer) {
        clearInterval(this.countdownTimer)
        this.countdownTimer = null
      }
    },

    /**
     * 把剩余秒数格式化为 "X 天 Y 时 Z 分"。
     */
    updateCountdownText() {
      if (this.deletionRemaining <= 0) {
        this.deletionCountdown = i18n.t('privacy.deleteExpired')
        return
      }
      const d = Math.floor(this.deletionRemaining / 86400)
      const h = Math.floor((this.deletionRemaining % 86400) / 3600)
      const m = Math.floor((this.deletionRemaining % 3600) / 60)
      this.deletionCountdown = i18n.t('privacy.deleteCountdownFmt', { d, h, m })
    },

    /**
     * 查看隐私政策：跳转到静态协议页(/pages/user/terms-document?type=privacy)。
     * <p>
     * terms-document.vue 在 onLoad(options) 中读取 type,按当前 locale
     * 渲染对应协议正文（来自 i18n.documents.types.privacy）。
     * 若未来后端提供正式协议 URL（.env VITE_PRIVACY_POLICY_URL）,
     * 可改为 uni.navigateTo({ url: webviewUrl }) 走 webview 容器。
     */
    onViewPolicy() {
      uni.navigateTo({ url: '/pages/user/terms-document?type=privacy' })
    },
  },
}
</script>

<style lang="scss" scoped>
.privacy {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
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

.header-btn:last-child {
  left: auto;
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

.content {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.privacy-overview {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx;
  background: #e9f9ee;
  border: 1rpx solid #34c759;
  border-radius: var(--radius-md);
}

.overview-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #34c759;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shield-icon {
  font-size: 40rpx;
  line-height: 1;
}

.overview-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.overview-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.overview-subtitle {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.section-title {
  padding-left: 8rpx;
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.section-body {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 20rpx 28rpx;
  border-bottom: 1rpx solid var(--color-divider);

  &:last-child {
    border-bottom: none;
  }
}

/* 开关左侧文字:主标题 + 副标题两行结构,留出充足视觉层级 */
.setting-label-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
  padding-right: 16rpx;
}

.setting-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.setting-desc {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  line-height: 1.4;
}

.toggle {
  position: relative;
  width: 88rpx;
  height: 52rpx;
  border-radius: 26rpx;
  background: var(--color-divider);
  transition: background-color 0.2s ease;
  flex-shrink: 0;
  cursor: pointer;

  &.active {
    background: var(--color-primary);
  }

  /* 切换请求中:降低透明度 + 禁用 pointer,防止连点 */
  &.disabled {
    opacity: 0.5;
    pointer-events: none;
  }
}

/* 首屏加载隐私设置时显示的占位,避免默认值闪烁 */
.loading-block {
  padding: 64rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.toggle-thumb {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s ease;

  .active & {
    transform: translateX(36rpx);
  }
}

.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 28rpx;
  border-bottom: 1rpx solid var(--color-divider);

  &:last-child {
    border-bottom: none;
  }
}

.action-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.action-icon {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
  width: 40rpx;
  text-align: center;

  &.del {
    color: var(--color-danger);
  }
}

.action-label {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);

  &.del {
    color: var(--color-danger);
  }
}

.action-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.action-hint {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.action-arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.item-divider {
  height: 1rpx;
  background: var(--color-divider);
  margin-left: 88rpx;
}

.footer-note {
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
