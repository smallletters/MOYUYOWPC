// 扫描 moyuyo-app/src 下所有弹窗调用,统计是否走了 i18n
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve('d:/MOYUYOWPC/moyuyo-app/src')

function walk(dir) {
  const out = []
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) out.push(...walk(p))
    else if (/\.(vue|js|ts)$/.test(e.name)) out.push(p)
  }
  return out
}

const files = walk(root)
const results = []
for (const f of files) {
  const txt = fs.readFileSync(f, 'utf8')
  const lines = txt.split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (/uni\.(showModal|showToast|showActionSheet|showLoading)\s*\(/.test(line)) {
      const block = lines.slice(i, Math.min(i + 20, lines.length)).join('\n')
      const get = (key) => {
        const re = new RegExp(key + String.raw`\s*:\s*['"\x60]([^'"\x60]*)['"\x60]`)
        const m = block.match(re)
        return m ? m[1] : null
      }
      const title = get('title')
      const content = get('content')
      const confirmText = get('confirmText')
      const cancelText = get('cancelText')
      // 是否走 i18n:出现 $t( 或 tCategoryName( 或 i18n.t( 或 单词边界 t(
      const hasI18n =
        /\$t\s*\(/.test(block) ||
        /\btCategoryName\s*\(/.test(block) ||
        /\bi18n\.t\s*\(/.test(block) ||
        /\bt\s*\(/.test(block)
      results.push({
        file: path.relative('d:/MOYUYOWPC', f).replace(/\\/g, '/'),
        line: i + 1,
        api: line.match(/uni\.(showModal|showToast|showActionSheet|showLoading)/)[1],
        hasI18n,
        title,
        content,
        confirmText,
        cancelText,
      })
    }
  }
}

const total = results.length
const i18ned = results.filter((r) => r.hasI18n).length
const hardcoded = results.filter((r) => !r.hasI18n)

console.log('总弹窗调用:', total)
console.log('已 i18n:', i18ned)
console.log('硬编码文案(需补 i18n):', hardcoded.length)
console.log('')
console.log('=== 硬编码明细 ===')
for (const r of hardcoded) {
  const fields = []
  if (r.title) fields.push(`title=${JSON.stringify(r.title)}`)
  if (r.content) fields.push(`content=${JSON.stringify(r.content)}`)
  if (r.confirmText) fields.push(`confirmText=${JSON.stringify(r.confirmText)}`)
  if (r.cancelText) fields.push(`cancelText=${JSON.stringify(r.cancelText)}`)
  console.log(`${r.file}:${r.line}  [${r.api}]  ${fields.join(' | ') || '(无可识别文案)'}`)
}
