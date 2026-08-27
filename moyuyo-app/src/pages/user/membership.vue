<template>
  <view class="membership-page">
    <!-- Hero 会员卡区域 -->
    <view class="hero-section">
      <view class="hero-inner">
        <!-- 会员卡（视觉锚点：全屏宽度的金属质感卡片） -->
        <view class="member-card">
          <!-- 卡片装饰：丝带纹理 + 顶部光晕 -->
          <view class="card-shine" />
          <view class="card-grain" />
          <view class="card-corner-glow" />

          <!-- 卡片顶部：品牌 + 等级 -->
          <view class="card-top">
            <view class="card-brand">
              <text class="brand-mark">M</text>
              <text class="brand-text">MOYUYO+</text>
            </view>
            <view class="card-tier">
              <text class="tier-en">MEMBER</text>
              <text class="tier-cn">{{ levelLabel }}</text>
            </view>
          </view>

          <!-- 卡片中部：核心信息（昵称 + 成长进度） -->
          <view class="card-mid">
            <view class="card-user">
              <view class="card-avatar">
                <text class="luc luc-paw-print card-avatar-icon" />
              </view>
              <view class="card-user-text">
                <text class="card-greeting">{{ greeting }}</text>
                <text class="card-name">{{ userInfo.nickname || '会员' }}</text>
              </view>
            </view>

            <view class="card-progress">
              <view class="card-progress-row">
                <text class="card-progress-label">{{ progressHint }}</text>
                <text class="card-progress-val">{{ progressPercent }}%</text>
              </view>
              <view class="card-progress-track">
                <view class="card-progress-fill" :style="{ width: progressPercent + '%' }" />
              </view>
            </view>
          </view>

          <!-- 卡片底部：卡号样式装饰 + CTA -->
          <view class="card-bottom">
            <text class="card-no">No. {{ cardNo }}</text>
            <view class="card-cta" @click="onUpgrade">
              <text class="card-cta-text">立即升级</text>
              <text class="luc luc-arrow-right card-cta-arrow" />
            </view>
          </view>
        </view>

        <!-- 价格提示（独立于卡片之外，呼吸感更强） -->
        <view class="price-hint">
          <text class="price-hint-text">年付 ¥99 · 首月仅需 ¥9</text>
        </view>
      </view>
    </view>

    <!-- 等级阶梯 -->
    <view class="ladder-section">
      <view class="section-head">
        <view class="section-head-left">
          <text class="section-eyebrow">LEVELS</text>
          <text class="section-title">五级阶梯 · 向上生长</text>
        </view>
        <text class="section-meta">共 {{ levels.length }} 级</text>
      </view>

      <view class="ladder">
        <view
          v-for="(lv, idx) in levels"
          :key="lv.code"
          class="ladder-row"
          :class="{ current: lv.code === currentLevelCode }"
        >
          <view class="ladder-rail">
            <view class="ladder-dot" :class="`dot-${lv.code}`">
              <text class="ladder-dot-num">{{ idx + 1 }}</text>
            </view>
            <view v-if="idx < levels.length - 1" class="ladder-line" />
          </view>
          <view class="ladder-body">
            <view class="ladder-head">
              <text class="ladder-name">{{ lv.name }}</text>
              <text v-if="lv.code === currentLevelCode" class="ladder-tag">当前</text>
            </view>
            <text class="ladder-desc">{{ lv.description }}</text>
            <view class="ladder-meta">
              <view class="meta-item">
                <text class="meta-label">积分倍率</text>
                <text class="meta-val strong">{{ lv.pointsRate }}x</text>
              </view>
              <view class="meta-divider" />
              <view class="meta-item">
                <text class="meta-label">成长值门槛</text>
                <text class="meta-val">{{ lv.growthThreshold }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 会员专属特权 -->
    <view class="privilege-section">
      <view class="section-head">
        <view class="section-head-left">
          <text class="section-eyebrow">PERKS</text>
          <text class="section-title">会员专属特权</text>
        </view>
      </view>

      <view class="privilege-grid">
        <view
          v-for="(item, idx) in privileges"
          :key="item.title"
          class="privilege-card"
          :class="`pg-${idx % 4}`"
        >
          <view class="privilege-icon-wrap">
            <text class="privilege-emoji luc" :class="$luc(item.icon)" />
          </view>
          <text class="privilege-title">{{ item.title }}</text>
          <text class="privilege-desc">{{ item.desc }}</text>
        </view>
      </view>
    </view>

    <!-- 底部链接 -->
    <view class="bottom-links">
      <view class="link-item" @click="goRules">
        <text class="link-text">了解会员规则</text>
        <text class="link-arrow"><text class="luc luc-chevron-right" /></text>
      </view>
      <view class="link-divider" />
      <view class="link-item" @click="goPoints">
        <text class="link-text">查看积分明细</text>
        <text class="link-arrow"><text class="luc luc-chevron-right" /></text>
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { memberApi } from '@/api'

// 用户会员信息（来自后端 /api/v1/member）
const userInfo = ref({ nickname: '', level: 'NORMAL', growthValue: 0, points: 0, memberNo: '' })

// 等级档位（来自后端 /api/v1/member/levels）
const levels = ref([
  { code: 'L1', name: 'Member', description: '注册即获得', growthThreshold: 0, pointsRate: 1.0 },
  {
    code: 'L2',
    name: 'Silver',
    description: '完成首单+签到',
    growthThreshold: 500,
    pointsRate: 1.1,
  },
  { code: 'L3', name: 'Gold', description: '活跃用户', growthThreshold: 2000, pointsRate: 1.2 },
  {
    code: 'L4',
    name: 'Platinum',
    description: '高频消费用户',
    growthThreshold: 8000,
    pointsRate: 1.5,
  },
  { code: 'L5', name: 'Black', description: '顶级 VIP', growthThreshold: 25000, pointsRate: 2.0 },
])

const currentLevelCode = computed(() => mapLevelCode(userInfo.value.level))

const levelLabel = computed(() => {
  const lv = levels.value.find((l) => l.code === currentLevelCode.value)
  return lv ? lv.name : 'Member'
})

// 问候语：根据等级切换，赋予身份感
const greeting = computed(() => {
  const map = { L1: '欢迎加入', L2: '感谢同行', L3: '尊享之旅', L4: '荣耀之选', L5: '王者归来' }
  return map[currentLevelCode.value] || '欢迎加入'
})

// 会员卡号：直接使用后端返回的 memberNo，未返回时兜底显示
const cardNo = computed(() => userInfo.value.memberNo || 'MY·00000000·0000')

// 进度条使用用户总积分（与 tabbar/user 中显示的"积分"为同一数值），保持两端一致
const progressPercent = computed(() => {
  const idx = levels.value.findIndex((l) => l.code === currentLevelCode.value)
  if (idx < 0) return 0
  const cur = levels.value[idx]
  const next = levels.value[idx + 1]
  if (!next) return 100
  const curPoints = userInfo.value.points || 0
  const span = next.growthThreshold - cur.growthThreshold
  if (span <= 0) return 100
  return Math.min(100, Math.max(0, Math.round(((curPoints - cur.growthThreshold) / span) * 100)))
})

const progressHint = computed(() => {
  const idx = levels.value.findIndex((l) => l.code === currentLevelCode.value)
  const next = levels.value[idx + 1]
  if (!next) return `已是顶级会员 · 当前积分 ${userInfo.value.points || 0}`
  const need = Math.max(0, next.growthThreshold - (userInfo.value.points || 0))
  return `距 ${next.name} 还需 ${need} 积分`
})

// 专属特权（来自后端 /api/v1/member/privileges，未返回时使用兜底）
const privileges = ref([
  { icon: 'tag', title: '新品优先购', desc: '新品提前48小时购买权' },
  { icon: 'star', title: '会员日特惠', desc: '每月8号会员专属折扣' },
  { icon: 'package', title: '专属IP周边', desc: 'MOYUYO IP限定周边' },
])

function mapLevelCode(apiLevel) {
  // 后端 mo_member.level 枚举：NORMAL/SILVER/GOLD/PLATINUM/DIAMOND
  // 前端映射为 L1~L5
  switch ((apiLevel || 'NORMAL').toUpperCase()) {
    case 'SILVER':
      return 'L2'
    case 'GOLD':
      return 'L3'
    case 'PLATINUM':
      return 'L4'
    case 'DIAMOND':
      return 'L5'
    case 'NORMAL':
    default:
      return 'L1'
  }
}

async function loadMemberInfo() {
  try {
    // request.get 已经解包 Result 包装，res 直接是业务 payload
    const data = (await memberApi.getMemberInfo()) || {}
    userInfo.value = {
      nickname: data.nickname || '会员',
      level: data.level || 'NORMAL',
      growthValue: data.growthValue || 0,
      points: data.points || 0,
      memberNo: data.memberNo || '',
    }
  } catch (e) {
    console.warn('[membership] load failed', e)
  }
}

async function loadLevels() {
  try {
    const arr = (await memberApi.getMemberLevels()) || []
    if (Array.isArray(arr) && arr.length) levels.value = arr
  } catch (e) {
    console.warn('[membership] load levels failed', e)
  }
}

async function loadPrivileges() {
  try {
    const arr = (await memberApi.getMemberPrivileges()) || []
    if (Array.isArray(arr) && arr.length) privileges.value = arr
  } catch (e) {
    console.warn('[membership] load privileges failed', e)
  }
}

function onUpgrade() {
  uni.navigateTo({ url: '/pages/user/prime-page' })
}

function goRules() {
  // 了解会员规则
  uni.navigateTo({ url: '/pages/user/membership-rule' })
}

function goPoints() {
  // 查看积分明细
  uni.navigateTo({ url: '/pages/user/points-detail' })
}

// 页面加载时拉取真实数据
onMounted(() => {
  loadMemberInfo()
  loadLevels()
  loadPrivileges()
})
onActivated(() => {
  loadMemberInfo()
  loadPrivileges()
})
</script>

<style lang="scss" scoped>
/* ============================================================
   MOYUYO Membership Center
   风格语言：克制奢华 · Sand Gold × Charcoal · 金属卡 + 米色留白
   单一视觉锚点（会员卡）+ 一个主色（Sand Gold） · 不用卡片堆砌
   ============================================================ */

.membership-page {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

// ===== Hero 区（深色背景，承载金属会员卡） =====
.hero-section {
  position: relative;
  padding: 8rpx 32rpx 56rpx;
  overflow: hidden;
  background:
    radial-gradient(120% 80% at 80% 0%, rgba(219, 201, 138, 0.18) 0%, transparent 55%),
    radial-gradient(80% 60% at 10% 100%, rgba(179, 138, 90, 0.35) 0%, transparent 60%),
    linear-gradient(160deg, #2e2b29 0%, #1f1d1b 60%, #141312 100%);
}
.hero-inner {
  position: relative;
  z-index: 2;
}

/* ===== 会员卡：全屏宽度的金属质感视觉锚点 ===== */
.member-card {
  position: relative;
  margin-top: 8rpx;
  border-radius: 32rpx;
  padding: 40rpx 36rpx 32rpx;
  overflow: hidden;
  /* Sand Gold 金属渐变 */
  background: linear-gradient(135deg, #dbc98a 0%, #c9b47a 35%, #b8a66b 70%, #8c7a4a 100%);
  box-shadow:
    0 24rpx 64rpx rgba(46, 43, 41, 0.35),
    0 4rpx 12rpx rgba(46, 43, 41, 0.18),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.4),
    inset 0 -2rpx 0 rgba(0, 0, 0, 0.18);
  color: #2e2b29;
}

/* 卡片高光：右上角斜射光 */
.card-shine {
  position: absolute;
  top: 0;
  right: 0;
  width: 70%;
  height: 100%;
  background: linear-gradient(
    135deg,
    transparent 30%,
    rgba(255, 255, 255, 0.35) 50%,
    transparent 70%
  );
  pointer-events: none;
  z-index: 1;
}
/* 丝带纹理：极细的横向金属拉丝 */
.card-grain {
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.04) 0px,
    rgba(255, 255, 255, 0.04) 1rpx,
    transparent 1rpx,
    transparent 4rpx
  );
  opacity: 0.5;
  pointer-events: none;
  z-index: 1;
  mix-blend-mode: overlay;
}
/* 左下角柔和光晕，呼应主色 */
.card-corner-glow {
  position: absolute;
  bottom: -120rpx;
  left: -120rpx;
  width: 320rpx;
  height: 320rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(46, 43, 41, 0.25), transparent 70%);
  pointer-events: none;
  z-index: 1;
}

/* 卡片顶部：品牌 + 等级标识 */
.card-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 56rpx;
}
.card-brand {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.brand-mark {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  border-radius: 14rpx;
  background: #2e2b29;
  color: #dbc98a;
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: 0;
  font-family: Georgia, serif;
}
.brand-text {
  font-size: 26rpx;
  font-weight: 700;
  color: #2e2b29;
  letter-spacing: 4rpx;
}
.card-tier {
  text-align: right;
}
.tier-en {
  display: block;
  font-size: 18rpx;
  font-weight: 700;
  color: rgba(46, 43, 41, 0.6);
  letter-spacing: 4rpx;
}
.tier-cn {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: #2e2b29;
  letter-spacing: 2rpx;
  margin-top: 2rpx;
  font-family: Georgia, serif;
}

/* 卡片中部：用户 + 进度 */
.card-mid {
  position: relative;
  z-index: 2;
  margin-bottom: 40rpx;
}
.card-user {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 32rpx;
}
.card-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #2e2b29;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 6rpx 16rpx rgba(46, 43, 41, 0.3),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.15);
}
.card-avatar-icon {
  font-size: 44rpx;
  color: #dbc98a;
}
.card-user-text {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.card-greeting {
  font-size: 22rpx;
  color: rgba(46, 43, 41, 0.7);
  letter-spacing: 2rpx;
  margin-bottom: 4rpx;
}
.card-name {
  font-size: 36rpx;
  font-weight: 800;
  color: #2e2b29;
  letter-spacing: 1rpx;
  font-family: Georgia, serif;
}

/* 进度条（卡片内，金属拉丝轨道 + 深色填充） */
.card-progress {
  width: 100%;
}
.card-progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
}
.card-progress-label {
  font-size: 22rpx;
  color: rgba(46, 43, 41, 0.78);
  letter-spacing: 1rpx;
}
.card-progress-val {
  font-size: 26rpx;
  font-weight: 800;
  color: #2e2b29;
  font-variant-numeric: tabular-nums;
}
.card-progress-track {
  position: relative;
  height: 10rpx;
  border-radius: 999rpx;
  background: rgba(46, 43, 41, 0.2);
  overflow: hidden;
  box-shadow: inset 0 1rpx 2rpx rgba(46, 43, 41, 0.2);
}
.card-progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #2e2b29 0%, #4a4541 100%);
  box-shadow: 0 0 8rpx rgba(46, 43, 41, 0.4);
  transition: width 0.8s cubic-bezier(0.22, 0.61, 0.36, 1);
}

/* 卡片底部：卡号 + CTA */
.card-bottom {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 24rpx;
  border-top: 1rpx dashed rgba(46, 43, 41, 0.25);
}
.card-no {
  font-size: 22rpx;
  color: rgba(46, 43, 41, 0.7);
  letter-spacing: 4rpx;
  font-variant-numeric: tabular-nums;
  font-family: Georgia, serif;
}
.card-cta {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #2e2b29;
  color: #dbc98a;
  transition: transform 0.2s ease;
}
.card-cta:active {
  transform: scale(0.96);
}
.card-cta-text {
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}
.card-cta-arrow {
  font-size: 24rpx;
}

/* 价格提示（卡片下方，呼吸感） */
.price-hint {
  margin-top: 28rpx;
  text-align: center;
}
.price-hint-text {
  display: inline-block;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(246, 242, 238, 0.85);
  font-size: 22rpx;
  letter-spacing: 2rpx;
  backdrop-filter: blur(10rpx);
  -webkit-backdrop-filter: blur(10rpx);
}

// ===== 区域标题（eyebrow + title 模式） =====
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 56rpx 32rpx 28rpx;
}
.section-head-left {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.section-eyebrow {
  font-size: 20rpx;
  font-weight: 700;
  color: var(--color-primary-dark);
  letter-spacing: 6rpx;
}
.section-title {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--color-text);
  letter-spacing: 1rpx;
  font-family: Georgia, serif;
}
.section-meta {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  letter-spacing: 1rpx;
  padding-bottom: 6rpx;
}

// ===== 等级阶梯（时间线式） =====
.ladder-section {
  padding-bottom: 8rpx;
}
.ladder {
  padding: 0 32rpx;
  display: flex;
  flex-direction: column;
}
.ladder-row {
  display: flex;
  gap: 24rpx;
  position: relative;
}
/* 阶梯轨道 */
.ladder-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 8rpx;
}
.ladder-dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
  border: 2rpx solid var(--color-divider);
  color: var(--color-text-secondary);
  font-size: 22rpx;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  transition: all 0.3s ease;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}
.ladder-dot.dot-L1 {
  background: linear-gradient(135deg, #e8ddb5, #dbc98a);
  border-color: #c9b47a;
  color: #2e2b29;
}
.ladder-dot.dot-L2 {
  background: linear-gradient(135deg, #d9dee0, #b8c0c4);
  border-color: #98a0a4;
  color: #2e2b29;
}
.ladder-dot.dot-L3 {
  background: linear-gradient(135deg, #f0d68a, #d4af37);
  border-color: #b8941f;
  color: #2e2b29;
}
.ladder-dot.dot-L4 {
  background: linear-gradient(135deg, #d8dde2, #9aa1a8);
  border-color: #6f7479;
  color: #2e2b29;
}
.ladder-dot.dot-L5 {
  background: linear-gradient(135deg, #2e2b29, #1a1816);
  border-color: #141312;
  color: #dbc98a;
}

.ladder-row.current .ladder-dot {
  width: 56rpx;
  height: 56rpx;
  font-size: 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(46, 43, 41, 0.25);
  border-color: #2e2b29;
}
.ladder-line {
  width: 2rpx;
  flex: 1;
  background: var(--color-divider);
  margin: 4rpx 0 -4rpx;
  min-height: 32rpx;
}
.ladder-row.current .ladder-line {
  background: linear-gradient(180deg, #2e2b29, var(--color-divider));
}

.ladder-body {
  flex: 1;
  padding: 4rpx 0 36rpx;
}
.ladder-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 6rpx;
}
.ladder-name {
  font-size: 30rpx;
  font-weight: 800;
  color: var(--color-text);
  letter-spacing: 1rpx;
  font-family: Georgia, serif;
}
.ladder-tag {
  display: inline-block;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background: #2e2b29;
  color: #dbc98a;
  font-size: 18rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
.ladder-desc {
  display: block;
  font-size: 22rpx;
  color: var(--color-text-secondary);
  margin-bottom: 16rpx;
  letter-spacing: 1rpx;
}
.ladder-meta {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border-radius: 16rpx;
  padding: 18rpx 24rpx;
  box-shadow: var(--shadow-sm);
}
.meta-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.meta-divider {
  width: 1rpx;
  height: 32rpx;
  background: var(--color-divider);
  margin: 0 20rpx;
}
.meta-label {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
  letter-spacing: 1rpx;
}
.meta-val {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}
.meta-val.strong {
  color: var(--color-primary-dark);
  font-family: Georgia, serif;
}

// ===== 会员专属特权（2×2 网格，不用横向滚动） =====
.privilege-section {
  padding-bottom: 8rpx;
}
.privilege-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  padding: 0 32rpx;
}
.privilege-card {
  position: relative;
  padding: 32rpx 28rpx;
  border-radius: 24rpx;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  transition: transform 0.2s ease;
}
.privilege-card:active {
  transform: scale(0.98);
}
/* 每个卡片一个微妙色调，避免网格单调 */
.privilege-card.pg-0::after,
.privilege-card.pg-1::after,
.privilege-card.pg-2::after,
.privilege-card.pg-3::after {
  content: '';
  position: absolute;
  top: -40rpx;
  right: -40rpx;
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  pointer-events: none;
}
.privilege-card.pg-0::after {
  background: radial-gradient(circle, rgba(219, 201, 138, 0.25), transparent 70%);
}
.privilege-card.pg-1::after {
  background: radial-gradient(circle, rgba(179, 138, 90, 0.2), transparent 70%);
}
.privilege-card.pg-2::after {
  background: radial-gradient(circle, rgba(171, 185, 173, 0.25), transparent 70%);
}
.privilege-card.pg-3::after {
  background: radial-gradient(circle, rgba(217, 180, 176, 0.25), transparent 70%);
}

.privilege-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #2e2b29 0%, #4a4541 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20rpx;
  position: relative;
  z-index: 2;
  box-shadow: 0 6rpx 16rpx rgba(46, 43, 41, 0.2);
}
.privilege-emoji {
  font-size: 32rpx;
  color: #dbc98a;
}
.privilege-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 8rpx;
  letter-spacing: 1rpx;
  position: relative;
  z-index: 2;
}
.privilege-desc {
  display: block;
  font-size: 22rpx;
  color: var(--color-text-secondary);
  line-height: 1.5;
  position: relative;
  z-index: 2;
}

// ===== 底部链接 =====
.bottom-links {
  margin: 32rpx 32rpx 0;
  background: var(--color-surface);
  border-radius: 24rpx;
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
.link-item {
  display: flex;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1rpx solid var(--color-divider);
  transition: background 0.2s ease;
}
.link-item:last-child {
  border-bottom: none;
}
.link-item:active {
  background: var(--color-background);
}
.link-text {
  flex: 1;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--color-text);
  letter-spacing: 1rpx;
}
.link-arrow {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
</style>
