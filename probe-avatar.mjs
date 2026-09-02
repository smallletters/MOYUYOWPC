import { chromium } from 'playwright'

// 1. 登录拿真 token
const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext()
const apiCtx = await browser.newContext()
const apiPage = await apiCtx.newPage()

const loginRes = await apiPage.request.post('http://localhost:8080/api/v1/auth/login', {
  data: { email: 'bloom@bloomwitness.com', password: 'BloomWitness123' },
  headers: { 'Content-Type': 'application/json' },
})
const loginJson = await loginRes.json()
const token = loginJson.data?.accessToken
console.log('[login] code=' + loginJson.code + ' tokenLen=' + (token?.length || 0))
if (!token) {
  console.log('[login] body=' + JSON.stringify(loginJson).slice(0, 300))
  process.exit(0)
}

const page = await ctx.newPage()
page.on('console', (m) => console.log(`[${m.type()}] ${m.text()}`))
page.on('pageerror', (e) => console.log(`[pageerror] ${e.message}`))

await page.goto('http://localhost:5174/pages/user/profile', { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)

// 2. 在浏览器内 fetch 直接调后端(走 vite proxy,等同于前端逻辑)
const result1 = await page.evaluate(async (t) => {
  const PNG = [0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0,0,0,0x0D,0x49,0x48,0x44,0x52,0,0,0,1,0,0,0,1,8,6,0,0,0,0x1F,0x15,0xC4,0x89,0,0,0,0x0D,0x49,0x44,0x41,0x54,0x78,0x9C,0x63,0,1,0,0,5,0,1,0x0D,0x0A,0x2D,0xB4,0,0,0,0,0x49,0x45,0x4E,0x44,0xAE,0x42,0x60,0x82]
  const buf = new Uint8Array(PNG)
  const blob = new Blob([buf], { type: 'image/png' })
  const fd = new FormData()
  fd.append('file', blob, '1788227091629') // 无扩展名文件名
  const r = await fetch('/api/v1/file/upload/image', {
    method: 'POST',
    body: fd,
    headers: { Authorization: 'Bearer ' + t },
  })
  return { status: r.status, body: await r.text() }
}, token)
console.log('[upload no-ext] status=' + result1.status + ' body=' + result1.body)

const result2 = await page.evaluate(async (t) => {
  const PNG = [0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0,0,0,0x0D,0x49,0x48,0x44,0x52,0,0,0,1,0,0,0,1,8,6,0,0,0,0x1F,0x15,0xC4,0x89,0,0,0,0x0D,0x49,0x44,0x41,0x54,0x78,0x9C,0x63,0,1,0,0,5,0,1,0x0D,0x0A,0x2D,0xB4,0,0,0,0,0x49,0x45,0x4E,0x44,0xAE,0x42,0x60,0x82]
  const buf = new Uint8Array(PNG)
  const blob = new Blob([buf], { type: 'image/png' })
  const fd = new FormData()
  fd.append('file', blob, 'avatar.png')
  const r = await fetch('/api/v1/file/upload/image', {
    method: 'POST',
    body: fd,
    headers: { Authorization: 'Bearer ' + t },
  })
  return { status: r.status, body: await r.text() }
}, token)
console.log('[upload with-ext] status=' + result2.status + ' body=' + result2.body)

process.stdout.write('', () => process.exit(0))