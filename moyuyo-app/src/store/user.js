import { defineStore } from 'pinia'
import { userApi, deviceApi } from '@/api'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'
import { getDeviceFingerprint } from '@/utils/deviceFingerprint'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getStorage(STORAGE_KEYS.TOKEN, ''),
    refreshToken: getStorage('moyuyo_refresh_token', ''),
    userInfo: getStorage(STORAGE_KEYS.USER_INFO, null),
    deviceList: getStorage(STORAGE_KEYS.DEVICE_LIST, []),
  }),

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.userInfo,
    userId: (state) => state.userInfo?.id || null,
  },

  actions: {
    async login(credentials) {
      const result = await userApi.login(credentials.username, credentials.password)
      this.token = result.accessToken
      this.refreshToken = result.refreshToken
      setStorage(STORAGE_KEYS.TOKEN, result.accessToken)
      setStorage('moyuyo_refresh_token', result.refreshToken)
      await this.fetchProfile()
      // 注册/刷新当前设备到后端;登录路径同步等待,避免进入"我的设备页"看不到本机
      // upsertCurrentDevice 内部已 try/catch,失败不阻断登录
      await this.upsertCurrentDevice()
      if (this.userInfo?.twoFactorEnabled) {
        return { requiresTwoFactor: true }
      }
      return true
    },

    async register(userData) {
      const result = await userApi.register(userData)
      this.token = result.accessToken
      this.refreshToken = result.refreshToken
      setStorage(STORAGE_KEYS.TOKEN, result.accessToken)
      setStorage('moyuyo_refresh_token', result.refreshToken)
      await this.fetchProfile()
      await this.upsertCurrentDevice()
      return true
    },

    async fetchProfile() {
      try {
        const data = await userApi.getUserInfo()
        this.userInfo = {
          id: data.id,
          email: data.email,
          nickname: data.nickname || '',
          avatar: data.avatar || '',
          phone: data.phone || '',
          birthday: data.birthday || '',
          country: data.country || '',
          emailVerified: data.emailVerified || false,
          twoFactorEnabled: data.twoFactorEnabled || false,
          // 隐私开关 4 项（V20260916_01）：
          // 后端 GET /me 已返回这些字段,这里 pick 进 store,
          // 避免 /pages/user/privacy 首屏读取时全是 undefined 走默认值
          publicFavorites: data.publicFavorites !== false,
          allowViewProfile: data.allowViewProfile !== false,
          showOnlineStatus: data.showOnlineStatus === true,
          allowMessages: data.allowMessages !== false,
        }
        setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
        return this.userInfo
      } catch (e) {
        console.error('[user] fetchProfile error', e)
        if (this.token) {
          const cached = getStorage(STORAGE_KEYS.USER_INFO)
          if (cached) this.userInfo = cached
        }
        return this.userInfo
      }
    },

    async updateProfile(data) {
      const updated = await userApi.updateUser(data)
      this.userInfo = { ...this.userInfo, ...updated }
      setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
      return updated
    },

    async refreshTokenAction() {
      if (!this.refreshToken) throw new Error('No refresh token')
      const result = await userApi.refreshToken(this.refreshToken)
      this.token = result.accessToken
      this.refreshToken = result.refreshToken
      setStorage(STORAGE_KEYS.TOKEN, result.accessToken)
      setStorage('moyuyo_refresh_token', result.refreshToken)
    },

    /**
     * 清空本地登录态与设备缓存(纯客户端,不发请求)
     * logout() 调服务端成功后、forceLogout() token 已失效时复用
     */
    _clearLocalState() {
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      this.deviceList = []
      removeStorage(STORAGE_KEYS.TOKEN)
      removeStorage(STORAGE_KEYS.USER_INFO)
      removeStorage('moyuyo_refresh_token')
      // 清 deviceList 缓存,防止下一账号冷启动读到本账号的列表
      removeStorage(STORAGE_KEYS.DEVICE_LIST)
    },

    async logout() {
      // 登出前先调服务端吊销 token(已在 userApi.logout 中实现)
      try {
        await userApi.logout()
      } catch (e) {
        /* ignore */
      }
      // 同时清理当前设备记录(避免同账号登出后留下"幽灵"设备)
      // 用 removeByDeviceId + 服务端 (user_id, device_id) 复合条件删除,
      // 严格限制为本人设备,避免 store.deviceList 残留来自别的账号的记录导致误删
      // 不阻断 logout 主流程,失败仅打 warn
      try {
        const fingerprint = getDeviceFingerprint()
        await deviceApi.removeByDeviceId(fingerprint)
      } catch (e) {
        console.warn('[user] remove current device on logout failed', e)
      }
      this._clearLocalState()
    },

    forceLogout() {
      // token 已失效场景:不发任何请求,直接清本地态
      this._clearLocalState()
    },

    async sendEmailVerification(email) {
      await userApi.sendEmailVerification(email)
    },

    async confirmEmailVerification(email, code) {
      await userApi.confirmEmailVerification(email, code)
      if (this.userInfo) {
        this.userInfo.emailVerified = true
        setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
      }
    },

    async forgotPassword(email) {
      await userApi.forgotPassword(email)
    },

    async resetPassword(token, newPassword) {
      await userApi.resetPassword(token, newPassword)
    },

    async changePassword(oldPassword, newPassword) {
      await userApi.changePassword(oldPassword, newPassword)
    },

    async sendMagicLink(email) {
      await userApi.sendMagicLink(email)
    },

    async verifyMagicLink(token) {
      const result = await userApi.verifyMagicLink(token)
      this.token = result.accessToken
      this.refreshToken = result.refreshToken
      setStorage(STORAGE_KEYS.TOKEN, result.accessToken)
      setStorage('moyuyo_refresh_token', result.refreshToken)
      await this.fetchProfile()
      // 与 login/register 一致,魔法链接登录也要注册当前设备
      await this.upsertCurrentDevice()
      return true
    },

    async toggle2FA(enabled) {
      // 1. 乐观更新本地 UI,保证点击即响应
      const prev = this.userInfo ? this.userInfo.twoFactorEnabled : false
      if (this.userInfo) {
        this.userInfo.twoFactorEnabled = enabled
        setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
      }
      // 2. 调后端 PUT /api/v1/auth/2fa 持久化;失败回滚并抛出错误由调用方处理 toast
      try {
        const updated = await userApi.setTwoFactorEnabled(enabled)
        // 3. 用服务端返回值覆盖 userInfo 的关键字段,
        //    避免前端乐观值与后端不一致(网络抖动 / 多设备并发切换)
        if (this.userInfo && updated) {
          this.userInfo.twoFactorEnabled = !!updated.twoFactorEnabled
          if (updated.id) this.userInfo.id = updated.id
          if (updated.email) this.userInfo.email = updated.email
          if (typeof updated.nickname === 'string') this.userInfo.nickname = updated.nickname
          if (typeof updated.avatar === 'string') this.userInfo.avatar = updated.avatar
          setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
        }
      } catch (e) {
        // 回滚到切换前状态
        if (this.userInfo) {
          this.userInfo.twoFactorEnabled = prev
          setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
        }
        throw e
      }
    },

    async sendTwoFactorCode() {
      await userApi.sendTwoFactorCode()
    },

    async verifyTwoFactorCode(code) {
      await userApi.verifyTwoFactorCode(code)
    },

    /**
     * 开启 2FA 的完整流程(顺序化,任一步失败抛出错误并被上层 toast):
     * 1) 校验验证码:前端调用方(security.vue 的 openVerifyModal)已经在打开弹窗时
     *    调过一次 sendTwoFactorCode,这里只 verify 即可;若此处再 send 一次,
     *    会让后端 Redis 覆盖式存储一个新码,导致用户输入上一封邮件的旧码时校验失败。
     * 2) PUT /api/v1/auth/2fa {enabled:true}(服务端校验 verified 缓存存在)
     */
    async enable2FAWithCode(code) {
      if (!code || !/^\d{6}$/.test(code)) {
        throw new Error('invalid_code')
      }
      // 1. 跳过发送:security.vue 已经在 openVerifyModal 时 send 过了,这里直接 verify 即可
      await this.verifyTwoFactorCode(code)
      // 2. 持久化(直接调 setTwoFactorEnabled 而不走 toggle2FA:本方法顺序化,
      //    调用方已负责展示 loading / 错误状态,不需要 toggle2FA 的乐观回滚)
      const updated = await userApi.setTwoFactorEnabled(true)
      if (this.userInfo && updated) {
        this.userInfo.twoFactorEnabled = !!updated.twoFactorEnabled
        if (updated.id) this.userInfo.id = updated.id
        if (updated.email) this.userInfo.email = updated.email
        if (typeof updated.nickname === 'string') this.userInfo.nickname = updated.nickname
        if (typeof updated.avatar === 'string') this.userInfo.avatar = updated.avatar
        setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
      }
      return updated
    },

    async fetchDevices() {
      // 从后端拉取真实设备列表(page=1 size=50 一次性拉满;支持后续分页)
      try {
        const res = await deviceApi.listDevices({ page: 1, size: 50 })
        // IPage 结构 {records, total, ...};兼容老版 array 返回
        // 防御:响应结构不合法时(无 records 字段且非数组)保留旧缓存,
        // 避免一次性脏响应把 deviceList 清成空,造成"假消失"
        const list =
          res && Array.isArray(res.records) ? res.records : Array.isArray(res) ? res : null
        if (list == null) {
          console.warn('[user] fetchDevices got invalid response, keep cache', res)
          return this.deviceList
        }
        this.deviceList = list
        setStorage(STORAGE_KEYS.DEVICE_LIST, list)
      } catch (e) {
        console.warn('[user] fetchDevices failed', e)
      }
      return this.deviceList
    },

    /**
     * 标记设备为 2FA 可信设备
     * @param {number|string} id 后端返回的设备主键 id
     */
    async trustDevice(id) {
      await deviceApi.trustDevice(id)
      const dev = this.deviceList.find((d) => d.id === id)
      if (dev) dev.trusted = 1
      setStorage(STORAGE_KEYS.DEVICE_LIST, this.deviceList)
    },

    /**
     * 取消设备的 2FA 可信标记
     * 后端 /trust 端点接受 body.trusted 字段(见 DeviceController.setTrust)
     */
    async untrustDevice(id) {
      await deviceApi.setTrust(id, false)
      const dev = this.deviceList.find((d) => d.id === id)
      if (dev) dev.trusted = 0
      setStorage(STORAGE_KEYS.DEVICE_LIST, this.deviceList)
    },

    /**
     * 删除指定设备记录(主键 id)
     * @param {number|string} id 后端返回的设备主键 id
     */
    async removeDevice(id) {
      await deviceApi.removeDevice(id)
      this.deviceList = this.deviceList.filter((d) => d.id !== id)
      setStorage(STORAGE_KEYS.DEVICE_LIST, this.deviceList)
    },

    /**
     * 注册/刷新当前设备(登录成功 + 每次启动 onShow 时调用)
     * 让"我的设备列表"能展示当前登录的设备;服务端按 (user_id, device_id) upsert
     * @param {object} info 可选覆盖字段(uni.getSystemInfoSync 之外的扩展)
     */
    async upsertCurrentDevice(info = {}) {
      try {
        const sys = uni.getSystemInfoSync ? uni.getSystemInfoSync() : {}
        // deviceId 只与设备相关(刷新 token / 切换账号不视作新设备)
        const fingerprint = info.deviceId || getDeviceFingerprint()
        const body = {
          deviceId: fingerprint,
          platform: info.platform || sys.platform || 'WEB',
          model: info.model || sys.model || sys.deviceModel || '',
          osVersion: info.osVersion || sys.system || sys.osVersion || '',
          appVersion: info.appVersion || sys.appVersion || '',
          pushToken: info.pushToken || '',
        }
        await deviceApi.upsertDevice(body)
      } catch (e) {
        // 启动路径(onLaunch)允许静默失败;登录路径虽然 await 但内部失败不抛
        console.warn('[user] upsertCurrentDevice failed', e)
      }
    },
  },
})
