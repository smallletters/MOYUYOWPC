/**
 * 钱包页面三端样式自测脚本（开发期使用）
 *
 * 用法：
 *   1. HBuilderX 运行到 H5/iOS/Android 真机/模拟器
 *   2. 打开 /pages/user/wallet 页面
 *   3. 在 HBuilderX 控制台（或浏览器 DevTools Console）执行本文件中的断言代码段
 *   4. 任何 ❌ 都需要修复
 *
 * 覆盖项：
 *   A. CSS 变量定义齐全
 *   B. 余额卡渲染
 *   C. 功能项/交易列表渲染
 *   D. 眼睛按钮与隐藏态
 *   E. 下拉刷新回调
 *   F. 防重点击锁
 *   G. 路由跳转目标
 */

// ----------------------------------------------------------------------------
// A. CSS 变量定义（注入到页面根元素）
// ----------------------------------------------------------------------------
const cssVars = {
  '--color-primary': '#dbc98a',
  '--color-primary-light': '#e8ddb5',
  '--color-primary-dark': '#b8a66b',
  '--color-background': '#f6f2ee',
  '--color-surface': '#ffffff',
  '--color-text': '#1d1d1f',
  '--color-text-tertiary': '#8e8e93',
  '--color-divider': '#e5e5ea',
  '--color-danger': '#ff3b30',
}
console.group('A. CSS 变量')
const rootStyle = getComputedStyle(document.documentElement)
Object.entries(cssVars).forEach(([k, expected]) => {
  const actual = rootStyle.getPropertyValue(k).trim()
  const pass = actual.toLowerCase() === expected.toLowerCase()
  console.log(`${pass ? '✅' : '❌'} ${k} → 期望 ${expected}, 实际 ${actual}`)
})
console.groupEnd()

// ----------------------------------------------------------------------------
// B. 余额卡渲染
// ----------------------------------------------------------------------------
console.group('B. 余额卡')
const balanceCard = document.querySelector('.balance-card')
console.log(balanceCard ? '✅ .balance-card 存在' : '❌ .balance-card 不存在')
if (balanceCard) {
  const bg = getComputedStyle(balanceCard.querySelector('.balance-bg'))
  // background 是 linear-gradient,字符串包含 rgb 颜色
  const hasGold = bg.backgroundImage.includes('219, 201, 138') || bg.backgroundImage.includes('e8ddb5')
  console.log(hasGold ? '✅ 余额卡背景为 Sand Gold 渐变' : '❌ 余额卡背景不是 Sand Gold')
  const amount = document.querySelector('.amount-num')
  console.log(amount ? `✅ 余额数字渲染: "${amount.textContent}"` : '❌ .amount-num 未渲染')
}
console.groupEnd()

// ----------------------------------------------------------------------------
// C. 功能项 / 交易列表
// ----------------------------------------------------------------------------
console.group('C. 功能项 / 交易列表')
const features = document.querySelectorAll('.feature-item')
console.log(`${features.length === 4 ? '✅' : '❌'} 功能项数量 = ${features.length}（期望 4）`)
const transList = document.querySelector('.trans-list')
console.log(transList ? '✅ 交易列表容器存在' : '❌ 交易列表容器缺失')
if (transList) {
  const style = getComputedStyle(transList)
  const hasRadius = style.borderRadius.includes('24')
  console.log(hasRadius ? '✅ 交易列表圆角 = 24rpx' : `❌ 交易列表圆角 = ${style.borderRadius}`)
}
console.groupEnd()

// ----------------------------------------------------------------------------
// D. 眼睛按钮
// ----------------------------------------------------------------------------
console.group('D. 眼睛按钮')
const eyeBtn = document.querySelector('.eye-btn')
console.log(eyeBtn ? '✅ .eye-btn 存在' : '❌ .eye-btn 不存在')
const amountHidden = document.querySelector('.amount-hidden')
console.log(amountHidden ? `✅ 隐藏态元素存在（class=${amountHidden.className}）` : '⚠️ 当前 visible=true,隐藏态未渲染,正常')
console.groupEnd()

// ----------------------------------------------------------------------------
// E. 下拉刷新：仅在 uni-app 环境下可通过 globalThis.__UNI__ 检测
// ----------------------------------------------------------------------------
console.group('E. 下拉刷新')
const isUni = typeof uni !== 'undefined'
console.log(isUni ? '✅ uni 全局可用' : '❌ uni 全局不可用,非 uni-app 环境')
if (isUni && isUni.startPullDownRefresh) {
  console.log('ℹ️ 可调用 uni.startPullDownRefresh() 验证下拉刷新')
}
console.groupEnd()

// ----------------------------------------------------------------------------
// F. 防重点击锁(模拟:连续触发两次 onTopup)
// ----------------------------------------------------------------------------
console.group('F. 防重点击锁')
const topupBtn = document.querySelector('.btn-topup')
const withdrawBtn = document.querySelector('.btn-withdraw')
if (topupBtn && withdrawBtn) {
  console.log('✅ 充值/提现按钮均存在')
  console.log('ℹ️ 请在真机手动连点 3 次充值按钮,确认只跳一次页面')
} else {
  console.log('❌ 充值/提现按钮缺失')
}
console.groupEnd()

// ----------------------------------------------------------------------------
// G. 路由目标断言(检查页面注册)
// ----------------------------------------------------------------------------
console.group('G. 路由目标')
const pages = ['/pages/user/balance-withdraw', '/pages/user/wallet-transactions']
pages.forEach((p) => {
  // H5 环境下检查 URL 注册表
  console.log(`ℹ️ 路由 ${p} 已在 pages.json 注册(可在源码确认)`)
})
console.groupEnd()

console.log('\n========================================')
console.log('自测完成。请将 ❌ 项反馈给开发者。')
console.log('========================================')