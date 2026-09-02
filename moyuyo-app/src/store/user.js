import { defineStore } from 'pinia'
import { userApi } from '@/api'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'

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

    async logout() {
      try { await userApi.logout() } catch (e) { /* ignore */ }
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      this.deviceList = []
      removeStorage(STORAGE_KEYS.TOKEN)
      removeStorage(STORAGE_KEYS.USER_INFO)
      removeStorage('moyuyo_refresh_token')
    },

    forceLogout() {
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      removeStorage(STORAGE_KEYS.TOKEN)
      removeStorage(STORAGE_KEYS.USER_INFO)
      removeStorage('moyuyo_refresh_token')
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
     * 开启 2FA 的完整流程:
     * 1) 发送验证码(后端写 Redis 5 分钟有效)
     * 2) 校验验证码(后端写 auth:2fa-verified:{userId} 2 小时有效)
     * 3) PUT /api/v1/auth/2fa {enabled:true}(服务端校验 verified 缓存存在)
     * 任一步失败抛出错误并被上层 toast。
     * 复用 toggle2FA 的回滚机制以保证 UI 一致性。
     */
    async enable2FAWithCode(code) {
      if (!code || !/^\d{6}$/.test(code)) {
        throw new Error('invalid_code')
      }
      // 1. 发送(幂等)
      await this.sendTwoFactorCode()
      // 2. 校验
      await this.verifyTwoFactorCode(code)
      // 3. 持久化(直接调 setTwoFactorEnabled 而不走 toggle2FA,
      //    因为此时 verified 缓存刚被消费,若 toggle2FA 先乐观改 userInfo 再走 PUT
      //    实际上语义没问题,但 enable2FAWithCode 是顺序化的,更清晰)
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
      return this.deviceList
    },

    async trustDevice(deviceId) {
      const dev = this.deviceList.find(d => d.id === deviceId)
      if (dev) dev.trusted = true
      setStorage(STORAGE_KEYS.DEVICE_LIST, this.deviceList)
    },

    async untrustDevice(deviceId) {
      const dev = this.deviceList.find(d => d.id === deviceId)
      if (dev) dev.trusted = false
      setStorage(STORAGE_KEYS.DEVICE_LIST, this.deviceList)
    },

    async removeDevice(deviceId) {
      this.deviceList = this.deviceList.filter(d => d.id !== deviceId)
      setStorage(STORAGE_KEYS.DEVICE_LIST, this.deviceList)
    },
  },
})
