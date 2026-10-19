/**
 * 表单验证工具集。
 * 统一返回 { ok: boolean, msg: string, value?: any } 三元组,
 * 让调用方在 v-model 失焦校验 / 提交前置校验 / 接口响应校验 复用同一逻辑。
 */
import { t } from '@/i18n'

const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const PHONE_RE = /^\+\d{8,15}$/ // E.164 格式：+ 加国家码 + 8~15 位数字
const SMS_CODE_RE = /^\d{6}$/
const PASSWORD_MIN = 8

/** 邮箱格式校验 */
export function validateEmail(value) {
  const v = (value || '').trim()
  if (!v) return { ok: false, msg: t('validate.emailRequired') }
  if (!EMAIL_RE.test(v)) return { ok: false, msg: t('validate.emailInvalid') }
  return { ok: true, msg: '', value: v }
}

/** 手机号校验(E.164:必带国家区号) */
export function validatePhone(value) {
  const v = (value || '').trim()
  if (!v) return { ok: false, msg: t('validate.phoneRequired') }
  if (!v.startsWith('+')) {
    return { ok: false, msg: t('validate.phoneNeedCountryCode') }
  }
  if (!PHONE_RE.test(v)) return { ok: false, msg: t('validate.phoneInvalid') }
  return { ok: true, msg: '', value: v }
}

/** 仅校验手机号部分(不含区号,用于 UI 输入框) */
export function validatePhoneNumber(value) {
  const v = (value || '').trim()
  if (!v) return { ok: false, msg: t('validate.phoneRequired') }
  if (!/^\d{6,15}$/.test(v)) return { ok: false, msg: t('validate.phoneNumberInvalid') }
  return { ok: true, msg: '', value: v }
}

/** 6 位短信验证码 */
export function validateSmsCode(value) {
  const v = (value || '').trim()
  if (!v) return { ok: false, msg: t('validate.smsRequired') }
  if (!SMS_CODE_RE.test(v)) return { ok: false, msg: t('validate.smsInvalid') }
  return { ok: true, msg: '', value: v }
}

/** 密码强度(注册/重置场景) */
export function validatePassword(value) {
  const v = value || ''
  if (!v) return { ok: false, msg: t('validate.passwordRequired') }
  if (v.length < PASSWORD_MIN)
    return { ok: false, msg: t('validate.passwordMin', { min: PASSWORD_MIN }) }
  // 至少含字母 + 数字(防纯数字 / 纯字母弱密码)
  if (!/[A-Za-z]/.test(v) || !/\d/.test(v)) {
    return { ok: false, msg: t('validate.passwordWeak') }
  }
  return { ok: true, msg: '', value: v }
}

/** 收货人姓名 */
export function validateReceiver(value) {
  const v = (value || '').trim()
  if (!v) return { ok: false, msg: t('validate.receiverRequired') }
  if (v.length < 2 || v.length > 30) return { ok: false, msg: t('validate.receiverLength') }
  return { ok: true, msg: '', value: v }
}

/** 宠物名(可选) */
export function validatePetName(value) {
  const v = (value || '').trim()
  if (v && v.length > 20) return { ok: false, msg: t('validate.petNameTooLong') }
  return { ok: true, msg: '', value: v }
}

/**
 * 通用校验失败提示:返回空 promise 时调用。
 * 与 uni.showToast 配合使用,避免每个表单重复 try/catch toast。
 */
export function showValidateError(result) {
  if (!result || result.ok) return false
  uni.showToast({ title: result.msg, icon: 'none', duration: 2500 })
  return true
}

export const PATTERNS = { EMAIL_RE, PHONE_RE, SMS_CODE_RE }
