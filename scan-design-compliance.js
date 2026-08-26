#!/usr/bin/env node
// 扫描设计稿 vs 实现的差异，生成合规报告
const fs = require('fs')
const path = require('path')

const DESIGN_DIR = 'D:/MOYUYOWPC/APPdocs/user'
const APP_PAGES = 'D:/MOYUYOWPC/moyuyo-app/src/pages'

function listDesignFiles() {
  return fs.readdirSync(DESIGN_DIR).filter((f) => f.endsWith('.html'))
}

function listAppPages() {
  const out = []
  function walk(dir) {
    fs.readdirSync(dir).forEach((f) => {
      const full = path.join(dir, f)
      if (fs.statSync(full).isDirectory()) walk(full)
      else if (f.endsWith('.vue')) out.push(full)
    })
  }
  walk(APP_PAGES)
  return out
}

/** 从设计稿 HTML 提取关键文本（按钮、标签、提示） */
function extractDesignKeywords(html) {
  const body = html.match(/<body[\s\S]*<\/body>/)?.[0] || html
  const texts = new Set()
  // 按钮文字
  const buttons = body.match(/<button[^>]*>([\s\S]*?)<\/button>/g) || []
  buttons.forEach((b) => {
    const t = b.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
    if (t && t.length < 30 && t.length > 1) texts.add(t)
  })
  // 标题
  const h1 = body.match(/<title>([^<]+)<\/title>/)?.[1]
  if (h1) texts.add(h1.replace(/^MOYUYO\s*/, '').trim())
  // aria-label
  const arias = body.match(/aria-label="([^"]+)"/g) || []
  arias.forEach((a) => {
    const t = a.replace(/aria-label="|"/g, '').trim()
    if (t.length < 30) texts.add(t)
  })
  return Array.from(texts).slice(0, 15)
}

/** 提取设计稿的关键 CSS class / 布局关键词 */
function extractDesignClasses(html) {
  const found = new Set()
  const patterns = [
    /class="([^"]*\b(kingkong|navbar|banner|product-card|category-item|sidebar)\b[^"]*)"/g,
    /class="([^"]*\b(btn-primary|btn-secondary|grid-2|grid-4)\b[^"]*)"/g,
  ]
  patterns.forEach((p) => {
    const m = html.match(p) || []
    m.forEach((x) => {
      const cl = x.match(/class="([^"]+)"/)?.[1]
      if (cl) cl.split(/\s+/).forEach((c) => found.add(c))
    })
  })
  return Array.from(found)
}

/** 简单从 html 提取关键 class 名列表 */
function extractClassList(html) {
  const cl = new Set()
  const matches = html.match(/class="([^"]+)"/g) || []
  matches.forEach((m) => {
    const cls = m.slice(7, -1).split(/\s+/)
    cls.forEach((c) => {
      // 只保留有意思的组件类
      if (/^[a-z][\w-]+$/.test(c) && !c.startsWith('flex') && !c.startsWith('text-')) {
        cl.add(c)
      }
    })
  })
  return Array.from(cl).slice(0, 30)
}

const designs = listDesignFiles()
const apps = listAppPages()

// 建立设计稿 → vue 的映射索引（路径末尾的目录名或文件名匹配）
function findVue(designName) {
  const baseName = designName.replace(/\.html$/, '')
  // 精确匹配：<baseName>.vue 或 <baseName>/index.vue（兼容 / 和 \ 分隔符）
  for (const p of apps) {
    if (/[/\\]/.test(p) && new RegExp(`[/\\\\]${baseName}\\.vue$`).test(p)) return p
    if (new RegExp(`[/\\\\]${baseName}[/\\\\]index\\.vue$`).test(p)) return p
  }
  // 语义匹配：<X>-detail → <X>/detail.vue（优先父段名匹配，避免跨领域冲突）
  const parts = baseName.split('-')
  if (parts.length >= 2) {
    const tail = parts[parts.length - 1]
    // 优先用第一段父目录名
    const head = parts[0]
    for (const p of apps) {
      if (new RegExp(`[/\\\\]${head}[/\\\\]${tail}\\.vue$`).test(p)) return p
    }
    // tail=list 时跳过 parentHints 模糊（避免与 goods/list 冲突），先尝试「去掉 -list 后缀直接匹配」
    if (tail === 'list') {
      const bare = baseName.replace(/-list$/, '')
      for (const p of apps) {
        if (new RegExp(`[/\\\\]${bare}\\.vue$`).test(p)) return p
        if (new RegExp(`[/\\\\]${bare}[/\\\\]index\\.vue$`).test(p)) return p
        if (new RegExp(`[/\\\\](user|pet|order)[/\\\\]${bare}\\.vue$`).test(p)) return p
      }
    }
    // 常见业务目录
    const parentHints = ['order', 'user', 'pet', 'goods', 'address', 'coupon', 'subscribe', 'wallet', 'balance', 'post', 'review', 'cs', 'tariff', 'topic', 'message', 'shipping', 'logistics', 'ar', 'flash', 'bundle', 'group', 'crowd', 'charity', 'frequent', 'fit', 'try', 'product']
    for (const p of apps) {
      for (const dir of parentHints) {
        if (new RegExp(`[/\\\\]${dir}[/\\\\]${tail}\\.vue$`).test(p)) return p
      }
    }
    // 多段复合名（如 order-recycle-bin → order/recycle-bin.vue）：去掉首段再用整体做文件名
    if (parts.length >= 3) {
      const stripped = parts.slice(1).join('-')
      for (const p of apps) {
        if (new RegExp(`[/\\\\](order|user|pet|goods)[/\\\\]${stripped}\\.vue$`).test(p)) return p
      }
    }
  }
  // 模糊匹配：去除 - _ 后的纯字符串匹配
  const norm = baseName.replace(/[-_]/g, '')
  for (const p of apps) {
    const bn = p.split(/[\\/]/).pop().replace(/\.vue$/, '')
    if (bn.replace(/[-_]/g, '') === norm) return p
  }
  return null
}

const report = []
for (const design of designs) {
  const designPath = path.join(DESIGN_DIR, design)
  const designContent = fs.readFileSync(designPath, 'utf-8')
  const keywords = extractDesignKeywords(designContent)
  const classes = extractClassList(designContent)
  const matchedVue = findVue(design)
  let vueContent = ''
  let matchedKeywords = []
  let missingKeywords = []
  let vueExists = false
  if (matchedVue && fs.existsSync(matchedVue)) {
    vueExists = true
    vueContent = fs.readFileSync(matchedVue, 'utf-8')
    for (const kw of keywords) {
      if (vueContent.includes(kw)) matchedKeywords.push(kw)
      else missingKeywords.push(kw)
    }
  }
  report.push({
    design: design,
    vue: matchedVue,
    vueExists,
    keywords,
    matchedKeywords,
    missingKeywords,
    classes,
  })
}

// 输出 Markdown 报告
const lines = ['# 设计稿 vs 实现 合规报告', '', `扫描 ${designs.length} 个设计稿与 ${apps.length} 个 Vue 页面`, '', '| 设计稿 | Vue 文件 | 状态 | 缺失关键文案 |',
  '|---|---|---|---|---|']
for (const r of report) {
  const status = r.vueExists ? (r.missingKeywords.length === 0 ? '✅ 完全' : `⚠️ ${r.missingKeywords.length} 项缺失`) : '❌ 未实现'
  const missing = r.missingKeywords.slice(0, 5).join(' / ') || '—'
  lines.push(`| ${r.design} | ${r.vue ? r.vue.replace(/.*\/src\//, '') : '—'} | ${status} | ${missing} |`)
}

fs.writeFileSync('D:/MOYUYOWPC/design-compliance-report.md', lines.join('\n'))
console.log('报告已生成: D:/MOYUYOWPC/design-compliance-report.md')
console.log('统计:')
console.log(`  ✅ 完全匹配: ${report.filter(r => r.vueExists && r.missingKeywords.length === 0).length}`)
console.log(`  ⚠️ 部分缺失: ${report.filter(r => r.vueExists && r.missingKeywords.length > 0).length}`)
console.log(`  ❌ 未实现: ${report.filter(r => !r.vueExists).length}`)
