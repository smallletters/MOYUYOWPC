/**
 * i18n 字典完整性 + 抽样渲染测试
 * 运行:node scripts/i18n-smoke.mjs
 */
const zhCN = (await import('../src/i18n/zh-CN.js')).default
const enUS = (await import('../src/i18n/en-US.js')).default
const MESSAGES = { 'zh-CN': zhCN, 'en-US': enUS }

function getByPath(obj, path) {
  if (!obj || !path) return path
  const keys = String(path).split('.')
  let cur = obj
  for (const k of keys) {
    if (cur && typeof cur === 'object' && k in cur) cur = cur[k]
    else return path
  }
  return cur
}

const namespaces = Object.keys(MESSAGES['zh-CN'])
console.log('=== 命名空间清单 (', namespaces.length, '个) ===')
console.log(namespaces.join(', '))

console.log('\n=== 双语 key 一致性 ===')
let mismatch = 0, totalKeys = 0
for (const ns of namespaces) {
  const zhKeys = Object.keys(MESSAGES['zh-CN'][ns] || {})
  const enKeys = Object.keys(MESSAGES['en-US'][ns] || {})
  totalKeys += zhKeys.length
  const onlyZh = zhKeys.filter((k) => !enKeys.includes(k))
  const onlyEn = enKeys.filter((k) => !zhKeys.includes(k))
  if (onlyZh.length || onlyEn.length) {
    mismatch++
    console.log(`❌ ${ns}: zh-only=[${onlyZh}], en-only=[${onlyEn}]`)
  } else {
    console.log(`✅ ${ns}: ${zhKeys.length} keys`)
  }
}
console.log(`\n汇总: ${totalKeys} 个 key, ${mismatch === 0 ? '全部一致 ✅' : mismatch + ' 个不一致 ❌'}`)

console.log('\n=== 抽样渲染验证 ===')
const samples = [
  'orderPay.title', 'orderReview.title', 'orderRefund.title', 'orderLogistics.title',
  'couponCenter.title', 'couponDetail.title', 'couponTransfer.title',
  'subscribe.title', 'giftCards.title', 'feedback.title', 'invite.title', 'notifications.title', 'help.title',
  'orderStatus.PENDING_PAY', 'orderLogistics.status.IN_TRANSIT',
]
function t(locale, key) {
  const v = getByPath(MESSAGES[locale], key)
  return v === key ? `❌ MISSING:${key}` : v
}
for (const k of samples) {
  console.log(`  ${k.padEnd(36)} → en: ${t('en-US', k)}`)
}

console.log('\n=== 插值测试 ===')
const params = { price: '$25.20', period: '月', count: 5 }
console.log(`  subscribe.confirmContent: ${t('zh-CN', 'subscribe.confirmContent').replace('{price}', params.price).replace('{period}', params.period)}`)
console.log(`  help.articleCount (5): ${t('zh-CN', 'help.articleCount').replace('{count}', params.count)}`)
console.log(`  invite.heroTitle ($20): ${t('en-US', 'invite.heroTitle').replace('{reward}', '$20')}`)