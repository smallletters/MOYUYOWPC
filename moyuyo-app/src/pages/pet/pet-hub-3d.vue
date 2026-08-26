<template>
  <view class="pet-hub">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-left">
        <view class="back-btn" @click="goBack">
          <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
        </view>
        <text class="header-title">Pet Hub 3D</text>
      </view>
      <view class="header-right">
        <view class="icon-btn" @click="onShare"><text class="luc luc-upload"></text></view>
        <view class="icon-btn" @click="onMore">⋯</view>
      </view>
    </view>

    <view class="content">
      <!-- 3D 模型展示区域 -->
      <view class="model-viewport">
        <!-- 场景装饰光效 -->
        <view class="viewport-glow" />
        <!-- 3D 模型占位 -->
        <view class="model-container">
          <view class="pet-silhouette">
            <!-- 宠物身体示意 -->
            <view class="pet-body" />
            <view class="pet-head" />
            <view class="pet-ear pet-ear-left" />
            <view class="pet-ear pet-ear-right" />
            <view class="pet-eye pet-eye-left" />
            <view class="pet-eye pet-eye-right" />
            <view class="pet-tail" />
          </view>
          <!-- 底部光环 -->
          <view class="pet-shadow" />
        </view>
        <!-- 3D 标签 -->
        <view class="viewport-badge">
          <text class="badge-icon"><text class="luc luc-package"></text></text>
          <text class="badge-text">3D 预览</text>
        </view>
        <!-- 旋转/缩放控制 -->
        <view class="viewport-controls">
          <view class="ctrl-btn" @click="onRotate"><text class="luc luc-refresh-cw"></text></view>
          <view class="ctrl-btn" @click="onZoomIn"><text class="luc luc-search"></text>+</view>
          <view class="ctrl-btn" @click="onZoomOut"><text class="luc luc-search"></text>-</view>
        </view>
        <!-- AR 模式切换 -->
        <view class="ar-btn" :class="{ active: arActive }" @click="toggleAr">
          <text class="ar-text"><text class="luc" :class="arActive ? 'luc-check' : 'luc-smartphone'"></text> AR</text>
        </view>
        <!-- 底部宠物信息 -->
        <view class="viewport-info">
          <view class="info-left">
            <text class="pet-name">{{ currentPet.name }}</text>
            <text class="pet-desc">{{ currentPet.breed }} · {{ currentPet.age }}</text>
          </view>
          <view class="info-right">
            <text class="scene-icon"><text class="luc luc-map-pin"></text></text>
            <text class="scene-name">{{ sceneLabels[currentScene] }}</text>
          </view>
        </view>
      </view>

      <!-- 场景切换 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">场景切换</text>
          <text class="section-extra">IP 环境</text>
        </view>
        <scroll-view scroll-x class="scene-scroll">
          <view
            v-for="scene in scenes"
            :key="scene.key"
            class="scene-chip"
            :class="{ active: currentScene === scene.key }"
            @click="switchScene(scene.key)"
          >
            <view class="scene-dot" :style="{ background: scene.color }" />
            <text class="scene-chip-text">{{ scene.label }}</text>
          </view>
        </scroll-view>
      </view>

      <!-- 多宠物切换 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">我的宠物</text>
          <view class="add-pet-btn" @click="onAddPet">
            <text class="add-icon"><text class="luc luc-plus"></text></text>
            <text class="add-text">添加</text>
          </view>
        </view>
        <scroll-view scroll-x class="pet-scroll">
          <view
            v-for="pet in pets"
            :key="pet.id"
            class="pet-tab"
            :class="{ active: currentPetId === pet.id }"
            @click="switchPet(pet.id)"
          >
            <view class="pet-avatar" :style="{ background: pet.bgColor }">
              <text class="pet-avatar-text" :style="{ color: pet.textColor }">
                {{ pet.initial }}
              </text>
            </view>
            <text class="pet-tab-name">{{ pet.name }}</text>
            <text class="pet-tab-breed">{{ pet.breedShort }}</text>
          </view>
        </scroll-view>
      </view>

      <!-- 属性面板 -->
      <view class="stats-card">
        <view class="stats-header">
          <text class="stats-title">属性</text>
          <view class="level-badge">
            <text class="level-text">Lv.12</text>
          </view>
        </view>
        <view v-for="stat in stats" :key="stat.label" class="stat-item">
          <view class="stat-row">
            <text class="stat-label">{{ stat.icon }} {{ stat.label }}</text>
            <text class="stat-value">{{ stat.current }}/100</text>
          </view>
          <view class="stat-bar">
            <view
              class="stat-fill"
              :style="{ width: stat.current + '%', background: stat.color }"
            />
          </view>
        </view>
        <!-- 交互按钮组 -->
        <view class="action-row">
          <view class="action-btn action-primary" @click="onPlay">
            <text>▶ 互动玩耍</text>
          </view>
          <view class="action-btn action-secondary" @click="onFeed">
            <text><text class="luc luc-utensils"></text> 喂食</text>
          </view>
          <view class="action-btn action-secondary" @click="onGroom">
            <text><text class="luc luc-scissors"></text> 护理</text>
          </view>
        </view>
      </view>

      <!-- 最近互动记录 -->
      <view class="section">
        <text class="section-title">最近互动</text>
        <view class="activity-list">
          <view v-for="item in activities" :key="item.id" class="activity-item">
            <view class="activity-icon" :style="{ background: item.iconBg }">
              <text class="luc" :class="$luc(item.icon)"></text>
            </view>
            <view class="activity-info">
              <text class="activity-title">{{ item.title }}</text>
              <text class="activity-desc">{{ item.time }} · {{ item.attr }} +{{ item.value }}</text>
            </view>
            <text class="activity-value">+{{ item.value }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template><script setup>
import { ref, computed } from 'vue'

// 返回上一页
const goBack = () => uni.navigateBack()

// AR 模式状态
const arActive = ref(false)
const toggleAr = () => {
  arActive.value = !arActive.value
  uni.showToast({ title: arActive.value ? 'AR 模式已开启' : 'AR 模式已关闭', icon: 'none' })
}

// 场景数据
const scenes = [
  { key: 'grass', label: '草地', color: '#4cd964' },
  { key: 'living', label: '起居室', color: '#ff9500' },
  { key: 'training', label: '训练场', color: '#5856d6' },
  { key: 'studio', label: '工作室', color: '#ff2d55' },
]
const sceneLabels = { grass: '草地', living: '起居室', training: '训练场', studio: '工作室' }
const currentScene = ref('grass')

const switchScene = (key) => {
  currentScene.value = key
  uni.showToast({ title: '已切换到 ' + sceneLabels[key], icon: 'none' })
}

// 宠物数据
const pets = [
  {
    id: 'milo',
    name: 'MILO',
    breed: '金毛寻回犬',
    breedShort: '金毛',
    age: '3岁',
    initial: 'M',
    bgColor: 'linear-gradient(135deg, #cfe5ff, #9fcbff)',
    textColor: '#007aff',
  },
  {
    id: 'luna',
    name: 'LUNA',
    breed: '布偶猫',
    breedShort: '布偶猫',
    age: '2岁',
    initial: 'L',
    bgColor: 'linear-gradient(135deg, #e9f9ee, #d4f5dc)',
    textColor: '#34c759',
  },
  {
    id: 'atlas',
    name: 'ATLAS',
    breed: '哈士奇',
    breedShort: '哈士奇',
    age: '4岁',
    initial: 'A',
    bgColor: 'linear-gradient(135deg, #fff0e0, #ffe4c4)',
    textColor: '#ff9500',
  },
  {
    id: 'olive',
    name: 'OLIVE',
    breed: '柯基',
    breedShort: '柯基',
    age: '1岁',
    initial: 'O',
    bgColor: 'linear-gradient(135deg, #f0e6ff, #e0ccff)',
    textColor: '#af52de',
  },
]
const currentPetId = ref('milo')
const currentPet = computed(() => pets.find((p) => p.id === currentPetId.value) || pets[0])

const switchPet = (id) => {
  currentPetId.value = id
  uni.showToast({ title: '已切换至 ' + pets.find((p) => p.id === id)?.name, icon: 'none' })
}

// 属性数据
const stats = [
  { label: '活跃度', icon: 'zap', current: 85, color: 'linear-gradient(90deg, #2e8dff, #007aff)' },
  { label: '亲密度', icon: 'heart', current: 72, color: 'linear-gradient(90deg, #34c759, #30d158)' },
  { label: '智力', icon: 'brain', current: 68, color: 'linear-gradient(90deg, #5856d6, #5e5ce6)' },
  { label: '社交', icon: 'users', current: 90, color: 'linear-gradient(90deg, #ff9500, #ff9f0a)' },
]

// 互动记录
const activities = [
  {
    id: 1,
    icon: 'paw-print',
    iconBg: '#e8f2ff',
    title: '玩了丢球游戏',
    time: '10 分钟前',
    attr: '活跃度',
    value: 15,
  },
  {
    id: 2,
    icon: 'utensils',
    iconBg: '#e9f9ee',
    title: '吃了鸡胸肉',
    time: '1 小时前',
    attr: '饱腹度',
    value: 20,
  },
  {
    id: 3,
    icon: 'scissors',
    iconBg: '#e8f2ff',
    title: '梳毛护理',
    time: '3 小时前',
    attr: '亲密度',
    value: 10,
  },
]

// 3D 控制操作
const onRotate = () => uni.showToast({ title: '360° 旋转展示', icon: 'none' })
const onZoomIn = () => uni.showToast({ title: '已放大', icon: 'none' })
const onZoomOut = () => uni.showToast({ title: '已还原', icon: 'none' })

// 互动操作
const onPlay = () =>
  uni.showToast({ title: '🎾 正在和 ' + currentPet.value.name + ' 玩耍...', icon: 'none' })
const onFeed = () => uni.showToast({ title: '🍗 正在喂食 ' + currentPet.value.name, icon: 'none' })
const onGroom = () =>
  uni.showToast({ title: '🛁 正在为 ' + currentPet.value.name + ' 护理', icon: 'none' })

// 其他操作
const onShare = () => uni.showToast({ title: '📤 分享链接已复制', icon: 'none' })
const onMore = () => uni.showToast({ title: '更多选项', icon: 'none' })
const onAddPet = () => uni.showToast({ title: '🐾 添加新宠物', icon: 'none' })
</script>

<style lang="scss" scoped>
.pet-hub {
  min-height: 100vh;
  background: var(--color-background);
}

/* 顶部导航栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  border-bottom: 1rpx solid var(--color-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.back-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.icon-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  font-size: 32rpx;
}

/* 内容区 */
.content {
  padding: 24rpx 24rpx 200rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

/* 3D 模型展示区 */
.model-viewport {
  position: relative;
  border-radius: 40rpx;
  overflow: hidden;
  background: linear-gradient(180deg, #f7f7fa, #f2f2f7);
  border: 1rpx solid var(--color-border);
  height: 680rpx;
}

.viewport-glow {
  position: absolute;
  top: 25%;
  left: 33%;
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(219, 201, 138, 0.1) 0%, transparent 70%);
  pointer-events: none;
}

.model-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 540rpx;
  position: relative;
}

/* 宠物剪影示意 */
.pet-silhouette {
  position: relative;
  width: 240rpx;
  height: 280rpx;
}

.pet-body {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 200rpx;
  height: 160rpx;
  border-radius: 40%;
  background: linear-gradient(145deg, #dbc98a, #c5b477);
  opacity: 0.3;
}

.pet-head {
  position: absolute;
  top: 8rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, #e5d9a8, #dbc98a);
  opacity: 0.35;
}

.pet-ear {
  position: absolute;
  top: 0;
  width: 48rpx;
  height: 64rpx;
  border-radius: 50% 50% 0 0;
  background: #dbc98a;
  opacity: 0.2;
}

.pet-ear-left {
  left: 24rpx;
  transform: rotate(-15deg);
}

.pet-ear-right {
  right: 24rpx;
  transform: rotate(15deg);
}

.pet-eye {
  position: absolute;
  top: 56rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--color-primary);
  opacity: 0.5;
}

.pet-eye-left {
  left: 64rpx;
}
.pet-eye-right {
  right: 64rpx;
}

.pet-tail {
  position: absolute;
  right: -48rpx;
  bottom: 32rpx;
  width: 80rpx;
  height: 16rpx;
  border-radius: 16rpx;
  background: #dbc98a;
  opacity: 0.2;
  transform: rotate(20deg);
}

.pet-shadow {
  position: absolute;
  bottom: -16rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 200rpx;
  height: 24rpx;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(219, 201, 138, 0.12) 0%, transparent 70%);
}

/* 3D 标签 */
.viewport-badge {
  position: absolute;
  top: 24rpx;
  left: 24rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20rpx);
  border: 1rpx solid var(--color-border);
}

.badge-icon {
  font-size: 24rpx;
}
.badge-text {
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}

/* 控制按钮 */
.viewport-controls {
  position: absolute;
  bottom: 160rpx;
  right: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.ctrl-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20rpx);
  border: 1rpx solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}

/* AR 按钮 */
.ar-btn {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20rpx);
  border: 1rpx solid var(--color-border);
}

.ar-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.ar-text {
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}
.ar-btn.active .ar-text {
  color: #fff;
}

/* 底部宠物信息 */
.viewport-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pet-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  display: block;
}
.pet-desc {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  display: block;
  margin-top: 4rpx;
}
.scene-icon {
  font-size: 22rpx;
}
.scene-name {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}
.info-right {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

/* 通用 section */
.section {
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 20rpx;
  display: block;
}

.section-extra {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}

/* 场景切换 */
.scene-scroll {
  white-space: nowrap;
}

.scene-chip {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 28rpx;
  border-radius: 20rpx;
  border: 1rpx solid var(--color-border);
  background: var(--color-card);
  margin-right: 16rpx;
}

.scene-chip.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.scene-dot {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
}

.scene-chip-text {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  white-space: nowrap;
}

.scene-chip.active .scene-chip-text {
  color: #fff;
}

/* 宠物切换 */
.pet-scroll {
  white-space: nowrap;
}

.pet-tab {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 24rpx;
  border-radius: 28rpx;
  border: 1rpx solid var(--color-border);
  background: var(--color-card);
  margin-right: 20rpx;
  min-width: 140rpx;
}

.pet-tab.active {
  border-color: var(--color-primary);
}

.pet-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pet-avatar-text {
  font-size: 32rpx;
  font-weight: 800;
}
.pet-tab-name {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.pet-tab-breed {
  font-size: 20rpx;
  color: var(--color-text-secondary);
}

.add-pet-btn {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.add-icon {
  font-size: 24rpx;
}
.add-text {
  font-size: 24rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

/* 属性面板 */
.stats-card {
  background: var(--color-card);
  border-radius: 28rpx;
  border: 1rpx solid var(--color-border);
  padding: 28rpx;
}

.stats-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.stats-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.level-badge {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(219, 201, 138, 0.15);
}

.level-text {
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.stat-item {
  margin-bottom: 20rpx;
}
.stat-item:last-of-type {
  margin-bottom: 0;
}

.stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10rpx;
}

.stat-label {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.stat-value {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.stat-bar {
  height: 12rpx;
  border-radius: 6rpx;
  background: var(--color-border);
  overflow: hidden;
}

.stat-fill {
  height: 100%;
  border-radius: 6rpx;
}

/* 交互按钮 */
.action-row {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.action-btn {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
}

.action-primary {
  background: var(--color-primary);
  color: #fff;
}

.action-secondary {
  background: var(--color-border);
  color: var(--color-text);
}

/* 互动记录 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx;
  border-radius: 20rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.activity-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}

.activity-info {
  flex: 1;
  min-width: 0;
}
.activity-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  display: block;
}
.activity-desc {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  display: block;
  margin-top: 4rpx;
}
.activity-value {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-success);
  flex-shrink: 0;
}
</style>
