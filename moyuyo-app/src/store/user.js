/**
 * 用户状态管理（WP OAuth Server 适配版）
 * - OAuth access_token 持久化
 * - 通过 /api/v1/userinfo 获取用户信息
 * - Device 列表（2FA 场景）
 * - 登录 / 登出 / Token 刷新
 */
import { defineStore } from 'pinia'
import { userApi } from '@/api'
import { setStorage, getStorage, removeStorage, STORAGE_KEYS } from '@/utils/storage'

export const useUserStore = defineStore('user', {
  state: () => ({
    // OAuth access_token（WP OAuth Server 返回的 access_token）
    token: getStorage(STORAGE_KEYS.TOKEN, ''),
    // 用户信息（来自 /api/v1/userinfo）
    userInfo: getStorage(STORAGE_KEYS.USER_INFO, null),
    deviceList: getStorage(STORAGE_KEYS.DEVICE_LIST, [])
  }),

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.userInfo,
    userId: (state) => state.userInfo?.id || state.userInfo?.sub || null
  },

  actions: {
    /**
     * OAuth Password Grant 登录
     * 调用 /api/v1/token → 存储 access_token → 拉取 userinfo
     */
    async login(credentials) {
      const result = await userApi.login(credentials.username, credentials.password)
      // WP OAuth Server 返回 { access_token, refresh_token, expires_in, id_token }
      const token = result.access_token
      this.token = token
      setStorage(STORAGE_KEYS.TOKEN, token)
      // 拉取用户信息（/api/v1/userinfo 端点）
      await this.fetchProfile()
      return true
    },

    /**
     * 注册
     */
    async register(userData) {
      const result = await userApi.register(userData)
      return result
    },

    /**
     * 拉取当前用户资料
     * 通过 /api/v1/userinfo 端点获取（WP OAuth Server 标准 OAuth 端点）
     * 返回数据格式：{ id, sub, email, first_name, last_name, picture, ... }
     */
    async fetchProfile() {
      try {
        const data = await userApi.getUserInfo()
        // 映射 OAuth userinfo 为标准用户格式
        this.userInfo = {
          id: data.id || data.sub,
          email: data.email,
          first_name: data.first_name || data.given_name || '',
          last_name: data.last_name || data.family_name || '',
          avatar: data.picture || data.avatar || '',
          // 保留原始数据供业务使用
          ...data
        }
        setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
        return this.userInfo
      } catch (e) {
        console.error('[user] fetchProfile error', e)
        if (this.token) {
          // userinfo 拉取失败，尝试降级使用本地缓存
          const cached = getStorage(STORAGE_KEYS.USER_INFO)
          if (cached) this.userInfo = cached
        }
        return this.userInfo
      }
    },

    /**
     * 更新用户资料（WooCommerce 顾客）
     */
    async updateProfile(data) {
      if (!this.userId) throw new Error('未登录')
      const updated = await userApi.updateUser(this.userId, data)
      this.userInfo = { ...this.userInfo, ...updated }
      setStorage(STORAGE_KEYS.USER_INFO, this.userInfo)
      return updated
    },

    /**
     * 退出登录
     */
    async logout() {
      await userApi.logout()
      this.token = ''
      this.userInfo = null
      this.deviceList = []
    },

    /**
     * 强制登出（Token 失效时）
     */
    forceLogout() {
      this.token = ''
      this.userInfo = null
      removeStorage(STORAGE_KEYS.TOKEN)
      removeStorage(STORAGE_KEYS.USER_INFO)
      removeStorage('moyuyo_refresh_token')
    }
  }
})
