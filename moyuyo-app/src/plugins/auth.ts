/**
 * 社交登录封装（uni 官方 API）
 *
 * 原 MOYUYO-Auth 原生插件已移除（桩实现），改为：
 * - Apple：uni.login({ provider: 'apple' })
 * - Google / Facebook：uni 无 App 端官方能力，走 Web OAuth 降级
 */
/* eslint-disable no-unreachable */
import type { AuthPluginProtocol, AuthProvider, PluginResult, AuthUserInfo } from './types'

/**
 * Apple 登录（仅 App 端可用）
 */
function loginWithApple(): Promise<AuthUserInfo> {
  // #ifdef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'apple',
      success: (res) => {
        // Apple 的完整登录信息在 appleInfo（App 3.4.3+），authResult 只有通用认证数据
        const info: any = (res as any).appleInfo || {}
        const auth: any = res.authResult || {}
        const fullName = info.fullName
        resolve({
          provider: 'apple',
          id: info.user || auth.openid || '',
          email: info.email || auth.email || '',
          // fullName 是对象（givenName/familyName 等），统一转成展示用字符串
          name:
            typeof fullName === 'string'
              ? fullName
              : [fullName?.givenName, fullName?.familyName].filter(Boolean).join(' '),
          accessToken: info.identityToken || auth.identityToken || '',
        })
      },
      fail: (err) => reject(new Error(err.errMsg || 'Apple 登录失败')),
    })
  })
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('Apple 登录仅支持 App 端'))
  // #endif
}

/**
 * 社交登录插件调用封装
 * 仅 Apple 走官方 uni.login，其余 provider 明确降级
 */
export function useAuthPlugin(): AuthPluginProtocol {
  const login = async (provider: AuthProvider): Promise<PluginResult<AuthUserInfo>> => {
    if (provider !== 'apple') {
      return { success: false, error: `${provider} 登录请走 Web OAuth` }
    }
    try {
      return { success: true, data: await loginWithApple() }
    } catch (error: any) {
      return { success: false, error: error?.message || '登录失败' }
    }
  }

  return {
    login,
    logout: async () => ({ success: true }),
    isAuthorized: async () => ({ success: true, data: false }),
  }
}
