import { i18n } from '@/i18n'

/**
 * 物流轨迹时间格式化：数据源返回 "2026-10-25 21:43"（承运商当地时间）。
 * 不用 new Date() 解析，避免 iOS 对非 ISO 格式返回 Invalid Date。
 */
export function formatTraceTime(value) {
  if (!value) return ''
  const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(String(value))
  if (!m) return value
  const [, , month, day, hour, minute] = m
  return i18n.locale === 'zh-CN'
    ? `${Number(month)}月${Number(day)}日 ${hour}:${minute}`
    : `${Number(month)}/${Number(day)} ${hour}:${minute}`
}
