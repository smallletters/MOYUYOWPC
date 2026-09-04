<template>
  <view class="pet-hub">
    <!-- 可滚动内容区（场景直达页面顶部） -->
    <scroll-view scroll-y class="page-scroll">
      <!-- 3D 宠物互动场景 -->
      <view class="scene" @click="onSceneTap">
        <!-- 设计稿固定场景图 -->
        <image class="scene-bg" src="/static/pet/pet-hub-scene.jpg" mode="aspectFill" />
        <!-- 顶部轻微压暗、底部加深，保证浮层文字可读 -->
        <view class="scene-mask" />

        <!-- 场景粒子光点（装饰动画） -->
        <view class="scene-particles">
          <view class="particle p1" />
          <view class="particle p2" />
          <view class="particle p3" />
          <view class="particle p4" />
        </view>

        <!-- 场景内悬浮宠物切换条（位于信息浮层下方） -->
        <view class="scene-pets" @click.stop>
          <scroll-view scroll-x class="pets-scroll" :show-scrollbar="false">
            <view class="pets-inner">
              <!-- 宠物头像列表 -->
              <view
                v-for="pet in petStore.pets"
                :key="pet.id"
                class="scene-pet"
                :class="{ active: pet.id === activePet?.id }"
                @click.stop="switchPet(pet)"
              >
                <view class="avatar-ring" :class="{ active: pet.id === activePet?.id }">
                  <image
                    v-if="pet.avatar"
                    class="pet-avatar"
                    :src="pet.avatar"
                    mode="aspectFill" />
                  <view v-else class="pet-avatar pet-avatar-fallback">
                    <text class="luc" :class="$luc(petIcon(pet))" />
                  </view>
                </view>
              </view>
              <!-- 暂无宠物：我的宠物占位（点击添加档案） -->
              <view v-if="petStore.pets.length === 0" class="scene-pet" @click.stop="goAddPet">
                <view class="avatar-ring">
                  <view class="pet-avatar pet-avatar-fallback">
                    <text class="luc luc-paw-print" />
                  </view>
                </view>
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 左上：宠物信息浮层 -->
        <view v-if="activePet" class="glass scene-info">
          <text class="info-kicker">{{ sceneKicker }}</text>
          <text class="info-title">{{ sceneTitle }}</text>
        </view>

        <!-- 右上：装扮入口 + 3D 空间入口（始终显示，点击守卫见 methods） -->
        <view class="scene-top">
          <view class="glass scene-pill" @click.stop="goDresser">
            <text class="luc luc-shirt scene-pill-icon" />
            <text class="scene-pill-text">装扮</text>
          </view>
          <view class="glass scene-pill" @click.stop="goSpace">
            <text class="luc luc-sparkles scene-pill-icon" />
            <text class="scene-pill-text">3D空间</text>
          </view>
        </view>

        <!-- 底部互动提示 -->
        <view v-if="activePet" class="scene-hint">
          <text>拖拽旋转 · 点击互动</text>
        </view>
        <view v-else class="scene-hint" @click.stop="goAddPet">
          <text>添加宠物档案</text>
        </view>

        <!-- 场景选择器 -->
        <view class="scene-selector">
          <view
            v-for="s in scenes"
            :key="s.id"
            class="scene-chip"
            :class="{ active: selectedScene === s.id }"
            @click="selectedScene = s.id"
          >
            <text>{{ s.label }}</text>
          </view>
        </view>
      </view>

      <!-- 护理状态卡片（上叠 16px 到场景图底部） -->
      <view class="care-wrap">
        <view class="care-grid">
          <view
            v-for="c in careItems"
            :key="c.type"
            class="care-card"
            @click="onCareClick">
            <view class="care-icon" :class="`tone-${c.tone}`">
              <!-- 洗护：设计稿浴缸图标 -->
              <svg
                v-if="c.type === 'BATH'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M10 10v.2A3 3 0 0 1 8.9 16H5a3 3 0 0 1-1-5.8V10a3 3 0 0 1 6 0Z" />
                <path d="M7 16v6" />
                <path d="M13 19v3" />
                <path
                  d="M12 19h8.3a1 1 0 0 0 .7-1.7L18 14h.3a1 1 0 0 0 .7-1.7L16 9h.2a1 1 0 0 0 .8-1.7L13 3l-1.4 1.5"
                />
              </svg>
              <!-- 疫苗：设计稿三角警示图标 -->
              <svg
                v-else-if="c.type === 'VACCINE'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"
                />
                <path d="M12 9v4" />
                <path d="M12 17h.01" />
              </svg>
              <!-- 驱虫：设计稿胶囊图标 -->
              <svg
                v-else
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="m8 2 1.88 1.88" />
                <path d="M14.12 3.88 16 2" />
                <path d="M9 7.13v-1a3.003 3.003 0 1 1 6 0v1" />
                <path
                  d="M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6"
                />
                <path d="M12 20v-9" />
                <path d="M6.53 9C4.6 8.8 3 7.1 3 5" />
                <path d="M6 13H2" />
                <path d="M3 21c0-2.1 1.7-3.9 3.8-4" />
                <path d="M20.97 5c0 2.1-1.6 3.9-3.5 4" />
                <path d="M22 13h-4" />
                <path d="M17.2 17c2.1.1 3.8 1.9 3.8 4" />
              </svg>
            </view>
            <text class="care-label">{{ c.label }}</text>
            <text class="care-value" :class="{ muted: !c.enabled }">{{ c.value }}</text>
            <text class="care-status" :class="c.statusClass">{{ c.status }}</text>
          </view>
        </view>

        <!-- 下次护理提醒条 -->
        <view v-if="nextReminder" class="reminder-banner" @click="goHealth">
          <view class="reminder-main">
            <text class="rm-text">{{ nextReminder.text }}</text>
            <text v-if="nextReminder.num" class="rm-num">{{ nextReminder.num }}</text>
            <text v-if="nextReminder.suffix" class="rm-text">{{ nextReminder.suffix }}</text>
          </view>
          <view class="rm-btn" @click.stop="goHealth">
            <text class="luc luc-more-horizontal" />
          </view>
        </view>
      </view>

      <!-- 快捷操作 -->
      <view class="quick-block">
        <view class="quick-head">
          <text class="quick-title">快捷操作</text>
        </view>
        <view class="quick-grid">
          <view
            v-for="a in actions"
            :key="a.id"
            class="quick-item"
            @click="onActionClick(a)">
            <view class="quick-icon">
              <text class="luc" :class="$luc(a.icon)" />
            </view>
            <text class="quick-label">{{ a.label }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { usePetStore, useUserStore } from '@/store'

// 提醒类型 → 中文名（护理卡图标为内联 SVG，不再依赖图标字体）
const REMINDER_META = {
  BATH: { label: '洗护' },
  VACCINE: { label: '疫苗' },
  DEWORM: { label: '驱虫' },
  EXAM: { label: '体检' },
}

// 护理卡类型与图标状态色：洗护/疫苗 → 绿；驱虫 → 红（与设计稿一致）
const CARE_TYPES = ['BATH', 'VACCINE', 'DEWORM']
const CARE_TONES = {
  BATH: { tone: 'ok' },
  VACCINE: { tone: 'ok' },
  DEWORM: { tone: 'err' },
}

export default {
  pageTitleKey: 'pageTitle.tabbarPet',

  data() {
    return {
      // 3D 可选场景（当前仅切换选中态，无真实换景）
      scenes: [
        { id: 'grass', label: '草地' },
        { id: 'living', label: '客厅' },
        { id: 'training', label: '训练场' },
        { id: 'studio', label: '工作室' },
      ],
      selectedScene: 'grass',
      actions: [
        { id: 'shop', label: '购买', icon: 'shopping-bag' },
        { id: 'share', label: '晒宠', icon: 'camera' },
        { id: 'calendar', label: '日历', icon: 'calendar' },
        { id: 'achievement', label: '成就', icon: 'trophy' },
        { id: 'weight', label: '体重', icon: 'scale' },
      ],
    }
  },

  computed: {
    petStore() {
      return usePetStore()
    },
    userStore() {
      return useUserStore()
    },
    activePet() {
      return this.petStore.activePet
    },

    // 场景卡信息浮层：kicker 为种类/品种，title 为「名字, 年龄」
    sceneKicker() {
      const p = this.activePet
      if (!p) return '宠物空间 3D'
      const label = [p.species, p.breed].filter(Boolean).join(' · ')
      return label || p.type || '我的小伙伴'
    },
    sceneTitle() {
      const p = this.activePet
      if (!p) return '开启宠物互动新体验'
      const age = this.ageText(p.birthday)
      return age ? `${p.name}, ${age}` : p.name
    },

    // 护理卡：有宠物取该宠物的护理提醒；无宠物时显示占位卡（保证结构始终可见）
    careItems() {
      const p = this.activePet
      const list = Array.isArray(this.petStore.reminders) ? this.petStore.reminders : []
      return CARE_TYPES.map((type) => {
        const meta = REMINDER_META[type]
        const tone = CARE_TONES[type]
        const rem = p ? list.find((r) => r.reminderType === type) : null
        return this.buildCareCard(type, meta, tone, rem)
      })
    },

    // 距离最近的下一条护理提醒（用于提醒条单行文案）
    nextReminder() {
      const list = Array.isArray(this.petStore.reminders) ? this.petStore.reminders : []
      const active = list.filter((r) => r.enabled !== false && r.nextDate)
      if (active.length === 0) return null
      const today = new Date()
      const soon = active.reduce((a, b) => {
        const da = new Date(a.nextDate)
        const db = new Date(b.nextDate)
        return Math.abs(da - today) <= Math.abs(db - today) ? a : b
      })
      const diff = Math.ceil((new Date(soon.nextDate) - today) / 86400000)
      const meta = REMINDER_META[soon.reminderType] || { label: soon.reminderType }
      if (diff < 0) {
        return { text: `${meta.label}提醒已到期，请尽快安排`, num: '', suffix: '' }
      }
      if (diff === 0) {
        return { text: `${meta.label}提醒就在今天`, num: '', suffix: '' }
      }
      return { text: `距下次${meta.label}提醒还有 `, num: String(diff), suffix: ' 天' }
    },
  },

  onShow() {
    // 未登录不调接口，避免 Pet Tab 每次切回都打 401 噪音日志
    if (!this.userStore?.isLoggedIn) return
    this.petStore.loadPets().then(() => {
      this.refreshReminders()
    })
  },

  methods: {
    // 按宠物类型返回头像占位图标
    petIcon(pet) {
      if (!pet) return 'paw-print'
      const map = { DOG: 'dog', CAT: 'cat' }
      return map[pet.type] || 'paw-print'
    },

    // 刷新当前宠物提醒数据
    refreshReminders() {
      const pet = this.activePet
      if (pet?.id) this.petStore.loadReminders(pet.id)
    },

    switchPet(pet) {
      this.petStore.currentPet = pet
      this.refreshReminders()
    },

    // 组装单张护理卡展示数据：以「上次护理时间」为主，距下次护理判断紧迫度
    buildCareCard(type, meta, tone, rem) {
      const base = { type, label: meta.label, ...tone }
      const set = (value, status, statusClass) => ({
        ...base,
        enabled: true,
        value,
        status,
        statusClass,
      })
      if (!rem) {
        return {
          ...base,
          enabled: false,
          value: '未设置',
          status: '点击去设置',
          statusClass: 'todo',
        }
      }
      if (rem.enabled === false) {
        return { ...base, enabled: false, value: '已关闭', status: '点击开启', statusClass: 'todo' }
      }

      // 上次护理时间 → 「X天前」
      const last = rem.lastNotifiedDate ? new Date(rem.lastNotifiedDate) : null
      const lastDiff =
        last && !Number.isNaN(last.getTime())
          ? Math.max(0, Math.floor((new Date() - last) / 86400000))
          : null

      // 距下次护理天数 → 判断是否临近到期
      const next = rem.nextDate ? new Date(rem.nextDate) : null
      const nextDiff =
        next && !Number.isNaN(next.getTime()) ? Math.ceil((next - new Date()) / 86400000) : null
      const warnDays = rem.advanceDays || 7 // 提前提醒天数

      if (lastDiff === null) {
        // 尚未记录上次护理时间
        const soon = nextDiff !== null && nextDiff <= warnDays
        return set('未记录', soon ? '尽快安排' : '待设置', soon ? 'warn' : 'todo')
      }

      const value = lastDiff === 0 ? '今天' : `${lastDiff} 天前`
      // 临近下次护理 → 红色提醒
      if (nextDiff !== null && nextDiff <= warnDays) {
        return set(value, '即将到期', 'warn')
      }
      const status = lastDiff <= 7 ? '状态良好' : '无需担心'
      return set(value, status, 'ok')
    },

    // 生日 → 年龄文案
    ageText(birthday) {
      if (!birthday) return ''
      const b = new Date(birthday)
      if (Number.isNaN(b.getTime())) return ''
      const now = new Date()
      let months = (now.getFullYear() - b.getFullYear()) * 12 + now.getMonth() - b.getMonth()
      if (now.getDate() < b.getDate()) months -= 1
      if (months <= 0) return '幼年'
      if (months < 12) return `${months} 个月`
      const years = Math.floor(months / 12)
      return `${years} 岁`
    },

    // 场景卡点击：有宠物进 3D 空间，无宠物去添加
    onSceneTap() {
      if (this.activePet) {
        this.goSpace()
      } else {
        this.goAddPet()
      }
    },

    // 进入装扮页
    goDresser() {
      const pet = this.activePet
      if (!pet) {
        this.goAddPet()
        return
      }
      uni.navigateTo({ url: `/pages/pet/dresser?petId=${pet.id}` })
    },

    // 护理卡点击 → 健康记录页
    onCareClick() {
      this.goHealth()
    },

    // 进入健康日历（护理提醒管理）
    goHealth() {
      const pet = this.activePet
      if (!pet?.id) {
        this.goAddPet()
        return
      }
      uni.navigateTo({ url: `/pages/pet/health?petId=${pet.id}` })
    },

    onActionClick(action) {
      const pet = this.activePet
      const petUrl = (path) => (pet?.id ? `${path}?petId=${pet.id}` : path)
      switch (action.id) {
        case 'shop':
          uni.switchTab({ url: '/pages/tabbar/category' })
          break
        case 'share':
          uni.switchTab({ url: '/pages/tabbar/community' })
          break
        case 'calendar':
          uni.navigateTo({ url: petUrl('/pages/pet/health-calendar') })
          break
        case 'achievement':
          uni.navigateTo({ url: petUrl('/pages/pet/achievement') })
          break
        case 'weight':
          uni.navigateTo({ url: petUrl('/pages/pet/weight-chart') })
          break
        default:
          uni.showToast({ title: '敬请期待', icon: 'none' })
      }
    },

    goAddPet() {
      uni.navigateTo({ url: '/pages/pet/profile' })
    },

    // 进入 3D 房间（页面独立运行，不依赖宠物参数，任何状态都可进入）
    goSpace() {
      uni.navigateTo({ url: '/pages/pet/space-3d' })
    },
  },
}
</script>

<style lang="scss" scoped>
/* 页面内局部 token：把设计稿的 iOS 风格色映射到本页，不改动全局主题 */
.pet-hub {
  --hub-bg: #f7f7fa; /* 页面浅灰背景 */
  --hub-card: #ffffff; /* 卡片 / 顶部栏表面 */
  --hub-line: #e5e5ea; /* 分隔 / 卡片描边 */
  --hub-chip: #f2f2f7; /* 图标底圆 / 灰按钮 */
  --hub-muted: #8e8e93; /* 次要文字 */
  --hub-ok: #34c759; /* 状态良好 */
  --hub-ok-bg: #e9f9ee;
  --hub-err: #ff3b30; /* 即将到期 */
  --hub-err-bg: #ffecea;
  /* 设计稿 text-xs = 12px，本页统一覆盖为 24rpx（1rpx≈0.5px） */
  --font-size-xs: 24rpx;
  /* 场景内容顶部距（状态栏高度 + H5 安全区），供场景内绝对定位浮层使用 */
  --sb: calc(var(--status-bar-height, 0px) + env(safe-area-inset-top, 0px));

  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--hub-bg);
}

/* 深色模式适配（跟随系统） */
@media (prefers-color-scheme: dark) {
  .pet-hub {
    --hub-bg: #1a1816;
    --hub-card: #2a2724;
    --hub-line: #3a3530;
    --hub-chip: #232120;
    --hub-muted: #807a72;
    --hub-ok: #30d158;
    --hub-ok-bg: rgba(48, 209, 88, 0.12);
    --hub-err: #ff453a;
    --hub-err-bg: rgba(255, 69, 58, 0.14);
  }
}

/* ===== 通用头像圆（40px） ===== */
.avatar-ring {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  padding: 2rpx;
  border: 2rpx solid var(--hub-line); /* 未选中：灰环 */
  box-sizing: border-box;
}
.avatar-ring.active {
  border-color: var(--color-primary); /* 选中：品牌主色环 */
}
.pet-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2rpx solid var(--hub-card); /* 内容白边，iOS 头像效果 */
  box-sizing: border-box;
}
.pet-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hub-chip);
  color: var(--hub-muted);
  font-size: 32rpx;
}

/* ===== 场景内悬浮宠物切换条（场景左上角） ===== */
.scene-pets {
  position: absolute;
  top: calc(var(--sb) + 24rpx);
  left: 32rpx;
  right: 32rpx;
  z-index: 6;
}
.pets-scroll {
  width: 100%;
  white-space: nowrap;
}
.pets-inner {
  display: inline-flex;
  align-items: center;
  gap: 20rpx;
}
.scene-pet {
  flex-shrink: 0;
}
/* 悬浮于场景图上：未选中用白色描边，选中用品牌金环 */
.scene-pets .avatar-ring {
  border-color: rgba(255, 255, 255, 0.85);
}
.scene-pets .avatar-ring.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 6rpx rgba(219, 201, 138, 0.22);
}
.scene-pets .pet-avatar {
  border-color: rgba(255, 255, 255, 0.9);
}
.scene-pets .pet-avatar-fallback {
  background: rgba(255, 255, 255, 0.35);
  color: #ffffff;
}

/* ===== 滚动内容 ===== */
.page-scroll {
  flex: 1;
}

/* ===== 3D 互动场景 ===== */
.scene {
  position: relative;
  width: 100%;
  height: 50vh;
  min-height: 720rpx;
  max-height: 920rpx;
  overflow: hidden;
}
.scene-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.scene-mask {
  position: absolute;
  inset: 0;
  /* 顶部轻微压暗 + 底部加深（from-black/10 via-transparent to-black/25） */
  background: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.1) 0%,
    rgba(0, 0, 0, 0) 40%,
    rgba(0, 0, 0, 0.25) 100%
  );
}
.glass {
  /* 玻璃拟态：半透明 + 背景模糊 */
  backdrop-filter: blur(20rpx);
  border: 1rpx solid rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

/* 左上宠物信息浮层（位于宠物头像排下方） */
.scene-info {
  position: absolute;
  top: calc(var(--sb) + 132rpx);
  left: 32rpx;
  padding: 16rpx 24rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  max-width: 480rpx;
}
.info-kicker {
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.85);
}
.info-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
}

/* 右上按钮组（装扮 + 3D 空间），纵向排列 */
.scene-top {
  position: absolute;
  top: calc(var(--sb) + 32rpx);
  right: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 16rpx;
}
.scene-pill {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.2);
}
.scene-pill-icon {
  font-size: 28rpx;
}
.scene-pill-text {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

/* 底部互动提示 */
.scene-hint {
  position: absolute;
  bottom: 160rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 16rpx 32rpx;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.3);
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}

/* 场景粒子光点（装饰动画） */
.scene-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}
.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  animation: particle-pulse 2.4s ease-in-out infinite;
}
.particle.p1 {
  width: 6rpx;
  height: 6rpx;
  top: 25%;
  left: 25%;
}
.particle.p2 {
  width: 4rpx;
  height: 4rpx;
  top: 33%;
  right: 33%;
  background: rgba(255, 255, 255, 0.5);
  animation-delay: 0.6s;
}
.particle.p3 {
  width: 8rpx;
  height: 8rpx;
  bottom: 33%;
  left: 50%;
  background: rgba(255, 255, 255, 0.4);
  animation-delay: 1.2s;
}
.particle.p4 {
  width: 4rpx;
  height: 4rpx;
  top: 40%;
  right: 25%;
  background: rgba(255, 255, 255, 0.5);
  animation-delay: 1.8s;
}
@keyframes particle-pulse {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

/* 场景选择器 */
.scene-selector {
  position: absolute;
  bottom: 84rpx; /* 上移至场景图内悬浮，不贴合下方护理卡 */
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 16rpx;
}
.scene-chip {
  padding: 12rpx 24rpx;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.3);
  border: 1rpx solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(20rpx);
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.12); /* 悬浮感阴影 */
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: rgba(255, 255, 255, 0.8);
}
.scene-chip.active {
  background: #ffffff;
  border-color: #ffffff;
  color: var(--color-text);
  box-shadow: var(--shadow-sm);
}

/* ===== 护理状态卡片（上叠 32rpx 到场景图） ===== */
.care-wrap {
  position: relative;
  z-index: 10;
  margin-top: -32rpx;
  padding: 0 32rpx;
}
.care-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24rpx;
}
.care-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 24rpx;
  background: var(--hub-card);
  border: 1rpx solid var(--hub-line);
  border-radius: 32rpx;
  box-shadow: var(--shadow-sm);
  box-sizing: border-box;
}
.care-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}
.care-icon svg {
  width: 32rpx;
  height: 32rpx;
}
.care-icon.tone-ok {
  background: var(--hub-ok-bg);
  color: var(--hub-ok);
}
.care-icon.tone-err {
  background: var(--hub-err-bg);
  color: var(--hub-err);
}
.care-label {
  font-size: var(--font-size-xs);
  color: var(--hub-muted);
}
.care-value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  margin-top: 4rpx;
  word-break: keep-all;
}
.care-value.muted {
  color: var(--hub-muted);
  font-weight: var(--font-weight-medium);
}
.care-status {
  font-size: 20rpx;
  margin-top: 6rpx;
  font-weight: var(--font-weight-medium);
}
.care-status.ok {
  color: var(--hub-ok);
}
.care-status.todo {
  color: var(--hub-muted);
}
.care-status.warn {
  color: var(--hub-err);
}

/* ===== 下次护理提醒条 ===== */
.reminder-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 24rpx 32rpx;
  background: var(--hub-chip);
  border-radius: 32rpx;
}
.reminder-main {
  flex: 1;
  display: flex;
  align-items: baseline;
}
.rm-text {
  font-size: var(--font-size-xs);
  color: var(--hub-muted);
}
.rm-num {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  margin: 0 4rpx;
}
.rm-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  color: var(--hub-muted);
}

/* ===== 快捷操作 ===== */
.quick-block {
  margin-top: 32rpx;
  padding: 0 32rpx 48rpx;
}
.quick-head {
  margin-bottom: 24rpx;
}
.quick-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16rpx;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 4rpx;
  background: var(--hub-card);
  border: 1rpx solid var(--hub-line);
  border-radius: 32rpx;
  box-sizing: border-box;
}
.quick-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--hub-chip);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  color: var(--color-text);
}
.quick-label {
  font-size: 20rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}
</style>
