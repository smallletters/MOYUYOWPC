import { post, get, put } from '@/utils/request'

export function register(data) {
  return post('/api/v1/auth/register', data)
}

export function login(username, password) {
  // showError:false 让 onLogin 自己处理 toast,避免重复弹窗且能看到后端真实 message
  return post('/api/v1/auth/login', { email: username, password }, { showError: false })
}

export function refreshToken(refreshToken) {
  return post('/api/v1/auth/refresh', { refreshToken })
}

export function logout() {
  return post('/api/v1/auth/logout')
}

export function getUserInfo() {
  return get('/api/v1/users/me')
}

export function updateUser(data) {
  return put('/api/v1/users/me', data)
}

export function sendEmailVerification(email) {
  return post('/api/v1/auth/email/verify', { email })
}

export function confirmEmailVerification(email, code) {
  return post('/api/v1/auth/email/verify-confirm', { email, code })
}

export function forgotPassword(email) {
  return post('/api/v1/auth/password/forgot', { email })
}

export function resetPassword(token, newPassword) {
  return post('/api/v1/auth/password/reset', { token, newPassword })
}

export function changePassword(oldPassword, newPassword) {
  return post('/api/v1/auth/password/change', { oldPassword, newPassword })
}

export function sendMagicLink(email) {
  return post('/api/v1/auth/magic-link/send', { email })
}

export function verifyMagicLink(token) {
  return post('/api/v1/auth/magic-link/verify', { token })
}

export function sendTwoFactorCode() {
  return post('/api/v1/auth/2fa/send')
}

export function verifyTwoFactorCode(code) {
  return post('/api/v1/auth/2fa/verify', { code })
}

/**
 * 开启/关闭两步验证。
 * 后端 PUT /api/v1/auth/2fa,body: { enabled: boolean }
 * 返回精简 profile VO(id/email/nickname/avatar/twoFactorEnabled),
 * 调用方负责把 twoFactorEnabled 回写到 store.userInfo。
 */
export function setTwoFactorEnabled(enabled) {
  return put('/api/v1/auth/2fa', { enabled })
}

/** 发送手机短信验证码 */
export function sendPhoneCode(phone, purpose = 'LOGIN') {
  return post('/api/v1/auth/phone/send-code', { phone, purpose })
}

/** 手机号 + 验证码登录（后端会在未注册时自动创建账号） */
export function loginByPhone(phone, code) {
  // 与 login() 一致：自己处理错误 toast,避免 request.js 默认 showError 重复弹窗
  return post('/api/v1/auth/phone/login', { phone, code }, { showError: false })
}

export default {
  register,
  login,
  refreshToken,
  logout,
  getUserInfo,
  updateUser,
  sendEmailVerification,
  confirmEmailVerification,
  forgotPassword,
  resetPassword,
  changePassword,
  sendMagicLink,
  verifyMagicLink,
  sendTwoFactorCode,
  verifyTwoFactorCode,
  setTwoFactorEnabled,
  sendPhoneCode,
  loginByPhone,
}
