#!/usr/bin/env node
// 批量清理12个文件的双标题栏问题
const fs = require('fs');
const path = require('path');

const files = [
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/bargain.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/bundle-deal.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/charity-donation.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/crowdfunding.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/fit-finder.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/flash-sale.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/frequent-purchase.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/group-buy.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/product-compare.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/shipping-calculator.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/try-before-buy.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/invoice.vue',
];

// 1) 删模板中 <view class="nav-header">...</view> 整块
//    用 [\s\S]*? 非贪婪，配合 </view> + 后面跟着 < 开头标签(注释/view/scroll-view)
const NAV_HEADER_RE = /\s*<view\s+class="nav-header">[\s\S]*?<\/view>(?=\s*(?:<!--|<view|<scroll-view))/g;

// 2) 删 goBack() { uni.navigateBack() },  整段
const GO_BACK_RE = /\s*goBack\(\)\s*\{\s*uni\.navigateBack\(\)\s*\}\s*,\s*/g;

// 3) 删 nav-* 和 back-icon CSS 整段
//    匹配 \n[whitespace].cls { ... } 后面到行尾
const CSS_CLASSES = ['.nav-header', '.nav-back', '.back-icon', '.nav-title', '.nav-placeholder'];
const CSS_RE = new RegExp(
  '\\s*\\n\\s*(?:' + CSS_CLASSES.map(c => c.replace('.', '\\).')).join('|') +
  ')\\s*\\{[^}]*\\}\\s*\\n',
  'g'
);

let totalFixed = 0;
let totalNoChange = 0;
let totalNotFound = 0;

for (const f of files) {
  if (!fs.existsSync(f)) {
    console.log(`SKIP(not found): ${f}`);
    totalNotFound++;
    continue;
  }
  let content = fs.readFileSync(f, 'utf8');
  const before = content;

  content = content.replace(NAV_HEADER_RE, '\n\n');
  content = content.replace(GO_BACK_RE, '\n    ');
  content = content.replace(CSS_RE, '\n');

  if (content !== before) {
    fs.writeFileSync(f, content, 'utf8');
    const rel = f.replace('d:/MOYUYOWPC/moyuyo-app/src/', '');
    console.log(`FIXED: ${rel}`);
    totalFixed++;
  } else {
    const rel = f.replace('d:/MOYUYOWPC/moyuyo-app/src/', '');
    console.log(`NO CHANGE: ${rel}`);
    totalNoChange++;
  }
}

console.log(`\nSUMMARY: fixed=${totalFixed}, noChange=${totalNoChange}, notFound=${totalNotFound}`);