/**
 * MOYUYO UTSDK 桥接协议定义
 *
 * 所有 uni-app 与原生插件的通信接口统一在此声明。
 * 调用端（TypeScript）与原生端（Kotlin / Swift）双方遵循此协议。
 *
 * 命名规范：方法名 camelCase，参数名 camelCase，回调统一 Promise
 */

// ============================================================
// 通用类型
// ============================================================

/** 插件调用结果 */
export interface PluginResult<T = void> {
  success: boolean
  data?: T
  error?: string
}

/** 3D 场景类型 */
export type PetScene = 'grassland' | 'livingroom' | 'training' | 'studio'

/** 支付渠道 */
export type PaymentChannel = 'apple_pay' | 'google_pay'

/** 社交登录提供商 */
export type AuthProvider = 'apple' | 'google' | 'facebook'

/** 支付结果 */
export interface PaymentResult {
  transactionId: string
  channel: PaymentChannel
  amount: number
  currency: string
  timestamp: number
}

/** 社交登录用户信息 */
export interface AuthUserInfo {
  provider: AuthProvider
  id: string
  email?: string
  name?: string
  avatar?: string
  accessToken: string
}

// ============================================================
// PetHub 3D 插件协议
// ============================================================

/**
 * 宠物装扮部位
 * BODY 主体服饰 / HEAD 头部配饰 / ACCESSORY 附加挂件
 */
export type CosmeticSlot = 'BODY' | 'HEAD' | 'ACCESSORY'

/**
 * 原生侧 openScene 入参
 */
export interface PetHubOpenSceneParams {
  petId: string
  petType: string
  breed?: string
  customization?: Record<string, unknown>
}

/**
 * 原生侧 applyCosmetic 入参
 */
export interface PetHubApplyCosmeticParams {
  petId: string
  slot: CosmeticSlot
  itemId: string
}

/**
 * 原生侧 playAnimation 入参
 */
export interface PetHubPlayAnimationParams {
  petId: string
  animationId: string
}

export interface PetHubPluginProtocol {
  /** 打开 3D 宠物场景（原生侧会启动 PetHubActivity，返回 success 表示已拉起） */
  openScene(params: PetHubOpenSceneParams): Promise<PluginResult>
  /** 为宠物更换服饰/配饰 */
  applyCosmetic(params: PetHubApplyCosmeticParams): Promise<PluginResult>
  /** 播放宠物动画（原生侧 fire-and-forget） */
  playAnimation(params: PetHubPlayAnimationParams): Promise<PluginResult>
  /** 截取 3D 场景截图 */
  captureSnapshot(): Promise<PluginResult<{ imagePath: string }>>
  /** 关闭 3D 场景释放资源 */
  closeScene(): Promise<PluginResult>
}

// ============================================================
// 支付插件协议
// ============================================================

export interface PaymentPluginProtocol {
  /** 发起支付 */
  pay(channel: PaymentChannel, orderInfo: Record<string, any>): Promise<PluginResult<PaymentResult>>
  /** 查询是否支持该支付渠道 */
  isAvailable(channel: PaymentChannel): Promise<PluginResult<boolean>>
}

// ============================================================
// 社交登录插件协议
// ============================================================

export interface AuthPluginProtocol {
  /** 发起社交登录 */
  login(provider: AuthProvider): Promise<PluginResult<AuthUserInfo>>
  /** 退出登录 */
  logout(provider: AuthProvider): Promise<PluginResult>
  /** 检查是否已授权 */
  isAuthorized(provider: AuthProvider): Promise<PluginResult<boolean>>
}
