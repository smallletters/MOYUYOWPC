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
                <text class="card-name">
                  {{ userInfo.nickname || t('membership.memberFallback') }}
                </text>
              </view>
            </view>

            <!-- 当前等级对应的会员价折扣 -->
            <view class="card-discount">
              <text class="card-discount-label">{{ t('membership.myDiscountLabel') }}</text>
              <text class="card-discount-val">{{ currentDiscountText }}</text>
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

          <!-- 卡片底部：卡号样式装饰 -->
          <view class="card-bottom">
            <text class="card-no">No. {{ cardNo }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 等级阶梯 -->
    <view class="ladder-section">
      <view class="section-head">
        <view class="section-head-left">
          <text class="section-eyebrow">LEVELS</text>
          <text class="section-title">{{ t('membership.ladderTitle') }}</text>
        </view>
        <text class="section-meta">
          {{ t('membership.totalLevels', { count: levels.length }) }}
        </text>
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
              <text v-if="lv.code === currentLevelCode" class="ladder-tag">
                {{ $t('membership.currentTag') }}
              </text>
            </view>
            <text class="ladder-desc">{{ lv.descKey ? t(lv.descKey) : lv.description }}</text>
            <view class="ladder-meta">
              <view class="meta-item">
                <text class="meta-label">{{ $t('membership.pointsRateLabel') }}</text>
                <text class="meta-val strong">{{ lv.pointsRate }}x</text>
              </view>
              <view class="meta-divider" />
              <view class="meta-item">
                <text class="meta-label">{{ $t('membership.growthLabel') }}</text>
                <text class="meta-val">{{ lv.growthThreshold }}</text>
              </view>
              <view class="meta-divider" />
              <view class="meta-item">
                <text class="meta-label">{{ $t('membership.priceDiscountLabel') }}</text>
                <text class="meta-val strong">{{ formatDiscount(lv.priceDiscountRate) }}</text>
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
          <text class="section-title">{{ t('membership.perksTitle') }}</text>
        </view>
      </view>

      <view class="privilege-grid">
        <view
          v-for="(item, idx) in privileges"
          :key="item.key"
          class="privilege-card"
          :class="[`pg-${idx % 4}`, { 'is-locked': !item.unlocked }]"
        >
          <view class="privilege-icon-wrap">
            <text class="privilege-emoji luc" :class="$luc(item.icon)" />
          </view>
          <text class="privilege-title">{{ item.title }}</text>
          <text class="privilege-desc">{{ item.desc }}</text>
          <text class="privilege-badge" :class="{ unlocked: item.unlocked }">
            {{ item.badge }}
          </text>
        </view>
      </view>
    </view>

    <!-- 底部链接 -->
    <view class="bottom-links">
      <view class="link-item" @click="goRules">
        <text class="link-text">{{ t('membership.rulesLink') }}</text>
        <text class="link-arrow luc-chevron-right" />
      </view>
      <view class="link-divider" />
      <view class="link-item" @click="goPoints">
        <text class="link-text">{{ $t('membership.pointsLink') }}</text>
        <text class="link-arrow luc-chevron-right" />
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed, onMounted, onActivated, onBeforeUnmount } from 'vue'
import { memberApi } from '@/api'
import { i18n } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userMembership')

// 轻量翻译函数（响应 localeVersion 变化，刷新依赖本地化的模板）
const localeVersion = ref(0)
let _unsubLocale = null
function t(key, params) {
  void localeVersion.value // 触发依赖追踪
  return i18n.t(key, params)
}

// 用户会员信息（来自后端 /api/v1/member）

const userInfo = ref({ nickname: '', level: 'NORMAL', growthValue: 0, points: 0, memberNo: '' })

// 等级档位（来自后端 /api/v1/member/levels）
const levels = ref([
  {
    code: 'L1',
    name: 'Member',
    descKey: 'membership.levelDesc.L1',
    growthThreshold: 0,
    pointsRate: 1.0,
    priceDiscountRate: 1.0,
  },
  {
    code: 'L2',
    name: 'Silver',
    descKey: 'membership.levelDesc.L2',
    growthThreshold: 500,
    pointsRate: 1.1,
    priceDiscountRate: 0.95,
  },
  {
    code: 'L3',
    name: 'Gold',
    descKey: 'membership.levelDesc.L3',
    growthThreshold: 2000,
    pointsRate: 1.2,
    priceDiscountRate: 0.88,
  },
  {
    code: 'L4',
    name: 'Platinum',
    descKey: 'membership.levelDesc.L4',
    growthThreshold: 8000,
    pointsRate: 1.5,
    priceDiscountRate: 0.85,
  },
  {
    code: 'L5',
    name: 'Black',
    descKey: 'membership.levelDesc.L5',
    growthThreshold: 25000,
    pointsRate: 2.0,
    priceDiscountRate: 0.8,
  },
])

const currentLevelCode = computed(() => mapLevelCode(userInfo.value.level))

const levelLabel = computed(() => {
  const lv = levels.value.find((l) => l.code === currentLevelCode.value)
  return lv ? lv.name : t('membership.memberFallback')
})

/** 当前等级档位（含后端下发的会员价折扣系数） */
const currentLevel = computed(
  () => levels.value.find((l) => l.code === currentLevelCode.value) || null,
)

/** 当前用户的会员价折扣文案，如 8.8折 / 12% off */
const currentDiscountText = computed(() => formatDiscount(currentLevel.value?.priceDiscountRate))

/**
 * 会员价折扣文案：0.88 → 8.8折，1.00 → 无折扣
 * 英文展示为折扣百分比，避免中英文单位差异
 */
function formatDiscount(rate) {
  const r = Number(rate)
  if (!(r > 0 && r < 1)) return t('membership.noDiscount')
  if (i18n.locale === 'zh-CN') {
    return `${Number((r * 10).toFixed(2))}${t('membership.discountUnit')}`
  }
  return t('membership.discountOff', { percent: Math.round((1 - r) * 100) })
}

// 问候语：根据等级切换，赋予身份感
const greeting = computed(() => t('membership.greeting.' + currentLevelCode.value))

// 会员卡号：后端统一返回 12 位数字，这里拼前缀展示（No.MY.000000000001）
// 兼容旧格式“MY·xxxxxxxx·xxxx”：仅提取数字后拼接，避免重复出现 MY 前缀
const cardNo = computed(() => {
  const raw = userInfo.value.memberNo || ''
  const digits = String(raw).replace(/\D/g, '').slice(0, 12)
  return `MY.${digits ? digits.padStart(12, '0') : '000000000000'}`
})

// 进度条使用后端返回的 growthValue(成长值)与等级门槛(growthThreshold)对照计算
// 修复前用 userInfo.points(积分) 当成长值,导致进度数值无意义
const progressPercent = computed(() => {
  const idx = levels.value.findIndex((l) => l.code === currentLevelCode.value)
  if (idx < 0) return 0
  const cur = levels.value[idx]
  const next = levels.value[idx + 1]
  if (!next) return 100
  const curGrowth = userInfo.value.growthValue || 0
  const span = next.growthThreshold - cur.growthThreshold
  if (span <= 0) return 100
  return Math.min(100, Math.max(0, Math.round(((curGrowth - cur.growthThreshold) / span) * 100)))
})

const progressHint = computed(() => {
  const idx = levels.value.findIndex((l) => l.code === currentLevelCode.value)
  const next = levels.value[idx + 1]
  if (!next) return t('membership.topLevel', { value: userInfo.value.growthValue || 0 })
  const curGrowth = userInfo.value.growthValue || 0
  const need = Math.max(0, next.growthThreshold - curGrowth)
  return t('membership.distanceNext', { next: next.name, need })
})

// 会员专属特权：按后端下发的真实等级配置动态生成，不再使用写死的营销文案
// - 会员价折扣：取当前等级 priceDiscountRate（与商品详情/下单结算同一份配置）
// - 积分加速：取当前等级 pointsRate（与积分实际发放倍率一致）
// 当前等级尚未生效的权益展示“升级解锁”，并给出目标等级的倍率/折扣
const privileges = computed(() => {
  const cur = currentLevel.value || {}

  // 会员价折扣：0.88 → 8.8折；>= 1 表示原价（未解锁）
  const curDiscount = Number(cur.priceDiscountRate)
  const discountUnlocked = curDiscount > 0 && curDiscount < 1
  const discountTarget = discountUnlocked
    ? cur
    : levels.value.find((lv) => {
        const rate = Number(lv.priceDiscountRate)
        return rate > 0 && rate < 1
      })

  // 积分倍率：> 1 表示有加速
  const curPointsRate = Number(cur.pointsRate)
  const pointsUnlocked = curPointsRate > 1
  const pointsTarget = pointsUnlocked ? cur : levels.value.find((lv) => Number(lv.pointsRate) > 1)

  const list = []
  // 折扣权益：无任何等级配置折扣时不展示，避免出现与实际不符的权益
  if (discountTarget) {
    list.push({
      key: 'memberPrice',
      icon: 'tag',
      unlocked: discountUnlocked,
      title: t('membership.privileges.memberPrice.title'),
      desc: discountUnlocked
        ? t('membership.privileges.memberPrice.descUnlocked', {
            value: formatDiscount(curDiscount),
          })
        : t('membership.privileges.memberPrice.descLocked', {
            level: discountTarget.name,
            value: formatDiscount(discountTarget.priceDiscountRate),
          }),
      badge: discountUnlocked
        ? t('membership.privileges.unlocked')
        : t('membership.privileges.lockedAt', { level: discountTarget.code }),
    })
  }
  if (pointsTarget) {
    list.push({
      key: 'pointsBoost',
      icon: 'star',
      unlocked: pointsUnlocked,
      title: t('membership.privileges.pointsBoost.title'),
      desc: pointsUnlocked
        ? t('membership.privileges.pointsBoost.descUnlocked', { value: `${curPointsRate}x` })
        : t('membership.privileges.pointsBoost.descLocked', {
            level: pointsTarget.name,
            value: `${Number(pointsTarget.pointsRate)}x`,
          }),
      badge: pointsUnlocked
        ? t('membership.privileges.unlocked')
        : t('membership.privileges.lockedAt', { level: pointsTarget.code }),
    })
  }
  return list
})

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
      nickname: data.nickname || t('membership.memberFallback'),
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
    if (Array.isArray(arr) && arr.length) {
      // 兼容旧后端未下发 priceDiscountRate 的场景，按等级回退到默认折扣，避免展示成“无折扣”
      const fallbackRates = new Map(levels.value.map((lv) => [lv.code, lv.priceDiscountRate]))
      levels.value = arr.map((lv) => ({
        ...lv,
        priceDiscountRate:
          lv.priceDiscountRate != null ? lv.priceDiscountRate : fallbackRates.get(lv.code),
      }))
    }
  } catch (e) {
    console.warn('[membership] load levels failed', e)
  }
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
  // 订阅语言切换，触发模板与本页 t() 依赖重新求值
  _unsubLocale = i18n.subscribe(() => {
    localeVersion.value += 1
  })
})
onActivated(() => {
  loadMemberInfo()
})
onBeforeUnmount(() => {
  if (_unsubLocale) _unsubLocale()
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
  color: #2e2b29; /* 设计为金色底深字，两态一致：.member-card 为金色渐变，深色下保持不变 */
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
  color: #2e2b29; /* 设计为金色底深字，两态一致：.member-card 为金色渐变，深色下保持不变 */
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
  color: #2e2b29; /* 设计为金色底深字，两态一致：.member-card 为金色渐变，深色下保持不变 */
  letter-spacing: 1rpx;
  font-family: Georgia, serif;
}

/* 当前等级会员价折扣（金色卡面上的深色信息条） */
.card-discount {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -12rpx 0 28rpx;
  padding: 12rpx 20rpx;
  border-radius: 12rpx;
  background: rgba(46, 43, 41, 0.08);
}
.card-discount-label {
  font-size: 20rpx;
  color: rgba(46, 43, 41, 0.65);
  letter-spacing: 1rpx;
}
.card-discount-val {
  font-size: 26rpx;
  font-weight: 800;
  color: #2e2b29;
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
  color: #2e2b29; /* 设计为金色底深字，两态一致：.member-card 为金色渐变，深色下保持不变 */
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
  font-size: 24rpx;
  color: rgba(46, 43, 41, 0.72);
  letter-spacing: 3rpx;
  /* 卡号统一使用等宽数字字体，确保每个数字等高对齐、宽度一致 */
  font-variant-numeric: tabular-nums lining-nums;
  font-feature-settings:
    'tnum' 1,
    'lnum' 1;
  font-family: ui-monospace, 'SF Mono', 'Cascadia Mono', Menlo, Consolas, monospace;
  line-height: 1.4;
  font-weight: 500;
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
/* 权益状态徽标：已解锁 / Lx 解锁 */
.privilege-badge {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  z-index: 3;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-size: 18rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  color: var(--color-text-tertiary);
  background: var(--color-background);
  border: 1rpx solid var(--color-divider);
}
.privilege-badge.unlocked {
  color: #8c7a4a;
  background: rgba(219, 201, 138, 0.28);
  border-color: rgba(179, 138, 90, 0.35);
}
/* 未解锁权益整体弱化，与已解锁项形成层级 */
.privilege-card.is-locked {
  opacity: 0.72;
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
