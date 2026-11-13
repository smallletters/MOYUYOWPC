/**
 * 插件统一导出
 * - 官方 API 封装：支付 / 登录 / 相机 / 定位
 * - 自研原生插件：推送 / 生物识别 / 凭证存储
 */
export { createPay, getPaymentChannels } from './payment'
export { loginWithApple, loginWithGoogle, loginWithFacebook } from './auth'
export { registerPush, handlePushMessage, getPushToken } from './push'
export { takePhoto, pickImage, compressImage } from './camera'
export { getCurrentLocation, openMapSelector } from './location'
export { authenticateBiometric, isBiometricAvailable } from './biometric'
