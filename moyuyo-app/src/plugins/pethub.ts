/**
 * PetHub 3D 原生插件封装
 *
 * 调用端通过 uni.requireNativePlugin('MOYUYO-PetHub3D') 与原生通信。
 * 降级策略：App 端调用原生插件，H5/小程序端使用静态图片占位。
 */
import type {
  PetHubPluginProtocol,
  PetHubOpenSceneParams,
  PetHubApplyCosmeticParams,
  PetHubPlayAnimationParams,
  PluginResult,
} from './types'

// 与原生 dcloud_uniplugins.json 中声明的插件名保持一致（"MOYUYO-PetHub3D"）
const PLUGIN_NAME = 'MOYUYO-PetHub3D'

/** 单例插件引用 */
let plugin: any = null

function getPlugin(): any {
  if (!plugin) {
    try {
      plugin = uni.requireNativePlugin(PLUGIN_NAME)
    } catch {
      console.warn('[PetHub] 原生插件不可用，使用降级模式')
      return null
    }
  }
  return plugin
}

/** 判断当前环境是否支持原生插件 */
function isNativeAvailable(): boolean {
  // #ifdef APP-PLUS
  return !!getPlugin()
  // #endif
  // #ifndef APP-PLUS
  return false
  // #endif
}

/**
 * PetHub 插件调用封装
 * 方法名与原生侧 UniJSMethod 注解保持一致：
 * - openScene / applyCosmetic / playAnimation / captureSnapshot / closeScene
 */
export function usePetHubPlugin(): PetHubPluginProtocol {
  const call = <T = void>(method: string, args?: any): Promise<PluginResult<T>> => {
    return new Promise((resolve) => {
      if (!isNativeAvailable()) {
        resolve({ success: false, error: '原生插件不可用' } as PluginResult<T>)
        return
      }
      getPlugin()[method](args, (res: any) => {
        resolve(res as PluginResult<T>)
      })
    })
  }

  // playAnimation 在原生侧为 fire-and-forget，JS 端保留 Promise 形式但不依赖回调结果
  const fire = (method: string, args: any): Promise<PluginResult> => {
    return new Promise((resolve) => {
      if (!isNativeAvailable()) {
        resolve({ success: false, error: '原生插件不可用' } as PluginResult)
        return
      }
      getPlugin()[method](args, () => resolve({ success: true }))
    })
  }

  return {
    openScene: (params: PetHubOpenSceneParams) => call('openScene', params),
    applyCosmetic: (params: PetHubApplyCosmeticParams) => call('applyCosmetic', params),
    playAnimation: (params: PetHubPlayAnimationParams) => fire('playAnimation', params),
    captureSnapshot: () => call<{ imagePath: string }>('captureSnapshot', {}),
    closeScene: () => fire('closeScene', {}),
  }
}
