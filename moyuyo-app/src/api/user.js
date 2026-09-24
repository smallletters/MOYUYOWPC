import { post, get, put, del } from '@/utils/request'

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

/**
 * 按用户 ID 查询公开 profile(头像/昵称/简介/关注数/粉丝数/积分等)。
 * 用于"他人 profile 页",与 /me 不同,无需登录态也可匿名调用(后端放行)。
 */
export function getUserProfile(id) {
  return get(`/api/v1/users/${id}/profile`)
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

/**
 * 更换/绑定当前登录用户的手机号。
 * 后端 PUT /api/v1/users/me/phone,body: { phone, code }
 * 返回结构与 GET /me 一致,前端可覆盖本地 userInfo.phone。
 * <p>
 * 用 showError:false：让调用方(phone.vue.onSubmit)统一 toast 后端 message,
 * 避免 request.js 默认 toast 与本地 catch toast 重复弹窗。
 */
export function changePhone(phone, code) {
  return put('/api/v1/users/me/phone', { phone, code }, { showError: false })
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

/**
 * 发送手机短信验证码。
 * 默认 showError:false:调用方(register.vue / phone.vue.onSendCode 等)自己
 * 处理错误 toast,避免 request.js 默认 toast 与调用方 catch toast 重复弹窗。
 * 需要自动 toast 的场景可显式传 options:{ showError: true } 覆盖。
 */
export function sendPhoneCode(phone, purpose = 'LOGIN', options = { showError: false }) {
  return post('/api/v1/auth/phone/send-code', { phone, purpose }, options)
}

/** 手机号 + 验证码登录（后端会在未注册时自动创建账号） */
export function loginByPhone(phone, code) {
  // 与 login() 一致：自己处理错误 toast,避免 request.js 默认 showError 重复弹窗
  return post('/api/v1/auth/phone/login', { phone, code }, { showError: false })
}

/**
 * 查询账号注销状态（冻结期倒计时用）。
 * 返回 { pending, scheduledAtMillis, remainingSeconds, status }
 * <p>
 * 用 showError:false 调用：401 时让 request.js 的 handleUnauthorized 走全局弹窗，
 * 这里不重复 toast；其它业务错误（500 等）由调用方决定是否提示。
 */
export function getDeletionStatus() {
  return get('/api/v1/auth/account/deletion', {}, { showError: false })
}

/**
 * 申请注销账户。
 * 后端写入 delete_scheduled_at = now + 15d 并吊销全部 token,
 * 冻结期内登录或调用 cancelDeleteAccount 可撤销。
 */
export function requestDeleteAccount() {
  return post('/api/v1/auth/account/deletion')
}

/**
 * 撤销注销申请（15 天冻结期内可"悔棋"）。
 */
export function cancelDeleteAccount() {
  return del('/api/v1/auth/account/deletion')
}

/**
 * 请求账户数据导出（USER 端）。
 * <p>
 * 后端 1 天内同账号最多 1 次，超出抛 HTTP 429 + 错误码前缀 DATA_EXPORT_RATE_LIMITED:{nextAllowedAtMillis}，
 * 前端通过 i18n 模板 {date} 展示下次可发起时间。
 * <p>
 * @param {object} [options] 透传 request.js 选项,常用：
 *   - showError:false 让 request.js 不自动 toast,由调用方自己用 showModal 展示
 */
export function requestDataExport(options = {}) {
  return post('/api/v1/users/me/export', {}, options)
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
  changePhone,
  loginByPhone,
  getDeletionStatus,
  getUserProfile,
  requestDeleteAccount,
  cancelDeleteAccount,
  requestDataExport,
}
