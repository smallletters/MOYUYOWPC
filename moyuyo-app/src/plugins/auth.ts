/**
 * 社交登录原生插件封装
 *
 * 调用端通过 uni.requireNativePlugin('MOYUYO-Auth') 调用原生社交登录。
 * 降级策略：H5 端使用 WebView OAuth 跳转。
 */
import type { AuthPluginProtocol, AuthProvider, PluginResult, AuthUserInfo } from './types'

// 与原生 dcloud_uniplugins.json 中声明的插件名保持一致（"MOYUYO-Auth"）
const PLUGIN_NAME = 'MOYUYO-Auth'

let plugin: any = null

function getPlugin(): any {
  if (!plugin) {
    try {
      plugin = uni.requireNativePlugin(PLUGIN_NAME)
    } catch {
      console.warn('[Auth] 原生插件不可用')
      return null
    }
  }
  return plugin
}

function isNativeAvailable(): boolean {
  // #ifdef APP-PLUS
  return !!getPlugin()
  // #endif
  // #ifndef APP-PLUS
  return false
  // #endif
}

/**
 * 社交登录插件调用封装
 * - login: 把协议 provider 映射到原生 loginWithApple / loginWithGoogle / loginWithFacebook
 * - logout: 直接调原生 logout，原生侧按 provider 自行清理凭据
 * - isAuthorized: 走原生 isAppInstalled；后续可叠加本地 token 缓存判断（当前先返回是否安装）
 */
export function useAuthPlugin(): AuthPluginProtocol {
  // 通用调用：T 默认 void，可按调用点显式标注返回 data 的类型
  const call = <T = void>(method: string, args?: any): Promise<PluginResult<T>> => {
    return new Promise((resolve) => {
      if (!isNativeAvailable()) {
        resolve({ success: false, error: '原生登录插件不可用' } as PluginResult<T>)
        return
      }
      getPlugin()[method](args, (res: any) => {
        resolve(res as PluginResult<T>)
      })
    })
  }

  // 协议 provider 到原生方法名的映射
  const loginMethodMap: Record<AuthProvider, string> = {
    apple: 'loginWithApple',
    google: 'loginWithGoogle',
    facebook: 'loginWithFacebook',
  }

  return {
    login: (provider: AuthProvider) => call<AuthUserInfo>(loginMethodMap[provider], {}),
    logout: (provider: AuthProvider) => call('logout', { provider }),
    isAuthorized: (provider: AuthProvider) => call<boolean>('isAppInstalled', { provider }),
  }
}
