#!/usr/bin/env node
// 批量清理18个文件的双标题栏问题 - 支持 nav-bar / header-bar / nav-header
const fs = require('fs');

const files = [
  'd:/MOYUYOWPC/moyuyo-app/src/pages/cart/checkout.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/common/empty-state.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/community/chat-history.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/community/dm-chat.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/ar-try-on.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/crowdfunding-list.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/goods/flash-sale-detail.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/order/after-sales-policy.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/order/cs-rating.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/order/tariff-detail.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/address-edit.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/address.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/community-topic.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/coupon-center.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/coupon-detail.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/coupon-transfer.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/devices.vue',
  'd:/MOYUYOWPC/moyuyo-app/src/pages/user/post-collection.vue',
];

// 1) 删模板中 class 为 nav-bar / header-bar / nav-header 的整块
const NAV_HEADER_RE = /\s*<view\s+class="(?:nav-bar|header-bar|nav-header|topbar|app-bar)">[\s\S]*?<\/view>(?=\s*(?:<!--|<view|<scroll-view|<view\s+class="(?:content|main|page|container|body|chat-content|chat-container|chat-detail|main-content|detail-content|detail|profile-content|profile|chat-history|address-content|address|coupon-content|coupon|crowdfunding|flash-sale|flash-content|policy|cs-rating|tariff|post|empty|content-area)))/g;

// 2) 删 goBack() { uni.navigateBack() } 整段
const GO_BACK_RE = /\s*goBack\(\)\s*\{\s*uni\.navigateBack\(\)\s*\}\s*,\s*/g;

// 3) 删相关 CSS - 支持多种 class 命名
const CSS_CLASSES = [
  // nav-bar 系列
  '.nav-bar', '.nav-title', '.nav-back', '.back-icon', '.nav-placeholder',
  '.nav-action', '.nav-action-btn',
  // header-bar 系列
  '.header-bar', '.header-title', '.header-back',
  // nav-header 系列（之前已删，但保守起见再列）
  '.nav-header',
  // 通用
  '.topbar', '.topbar-brand', '.topbar-back', '.topbar-icon', '.topbar-placeholder',
  // app-bar 系列
  '.app-bar', '.app-bar-title',
];
const CSS_RE = new RegExp(
  '\\s*\\n\\s*(?:' + CSS_CLASSES.map(c => '\\' + c).join('|') +
  ')\\s*\\{[^}]*\\}\\s*\\n',
  'g'
);

let totalFixed = 0, totalNoChange = 0;

for (const f of files) {
  if (!fs.existsSync(f)) {
    console.log(`SKIP(not found): ${f}`);
    continue;
  }
  let content = fs.readFileSync(f, 'utf8');
  const before = content;

  content = content.replace(NAV_HEADER_RE, '\n');
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

console.log(`\nSUMMARY: fixed=${totalFixed}, noChange=${totalNoChange}`);