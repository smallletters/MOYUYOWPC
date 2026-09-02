<template>
  <view class="allergy-profile">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">过敏档案</text>
      <view class="edit-btn" @click="onEdit">
        <text class="edit-text">编辑</text>
      </view>
    </view>

    <view class="content">
      <!-- 宠物选择器 -->
      <view class="pet-selector">
        <view
          v-for="pet in pets"
          :key="pet.id"
          class="pet-option"
          :class="{ active: currentPetId === pet.id }"
          @click="selectPet(pet.id)"
        >
          <view
            class="pet-avatar"
            :style="{
              background: pet.bgColor,
              border:
                currentPetId === pet.id
                  ? '4rpx solid var(--color-primary)'
                  : '4rpx solid transparent',
            }"
          >
            <text class="pet-avatar-text" :style="{ color: pet.textColor }">{{ pet.initial }}</text>
          </view>
          <text class="pet-name" :class="{ active: currentPetId === pet.id }">{{ pet.name }}</text>
          <view v-if="currentPetId === pet.id" class="pet-indicator" />
        </view>
      </view>

      <!-- 过敏原标签区 -->
      <view class="allergen-section">
        <view class="section-header">
          <text class="section-title">过敏原</text>
          <text class="section-count">已标记 {{ markedAllergens.length }} 项</text>
        </view>

        <!-- 已标记的过敏原 -->
        <view class="marked-allergens">
          <view
            v-for="item in markedAllergens"
            :key="item"
            class="allergen-tag marked"
            @click="removeAllergen(item)"
          >
            <text class="tag-text">{{ item }}</text>
            <view class="tag-close"><text class="luc luc-x" /></view>
          </view>
        </view>

        <!-- 常见过敏原（可选） -->
        <view class="common-section">
          <text class="common-label">常见过敏原</text>
          <view class="common-allergens">
            <view
              v-for="item in commonAllergens"
              :key="item"
              class="allergen-tag common"
              @click="addAllergen(item)"
            >
              <text class="tag-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <!-- 自定义过敏原输入 -->
        <view class="custom-input-row">
          <input
            v-model="customAllergen"
            class="custom-input"
            placeholder="自定义过敏原..."
            @confirm="addCustomAllergen"
          >
          <view class="add-btn" @click="addCustomAllergen">
            <text class="add-btn-text">添加</text>
          </view>
        </view>
      </view>

      <!-- 过敏记录时间线 -->
      <view class="timeline-section">
        <view class="section-header">
          <text class="section-title">过敏记录</text>
          <text class="view-all" @click="onViewAll">查看全部</text>
        </view>
        <view class="timeline">
          <view class="timeline-line" />
          <view v-for="record in allergyRecords" :key="record.id" class="timeline-item">
            <view
              class="timeline-dot"
              :style="{ background: record.dotBg, borderColor: record.dotBorder }"
            />
            <view class="timeline-card">
              <view class="card-header">
                <text class="card-title" :style="{ color: record.titleColor }">
                  {{ record.title }}
                </text>
                <text class="card-date">{{ record.date }}</text>
              </view>
              <text class="card-desc">{{ record.description }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 安全商品推荐区 -->
      <view class="safe-products-section">
        <view class="section-header">
          <view class="title-row">
            <text class="section-title">安全商品清单</text>
            <view class="auto-badge">
              <text class="auto-text">自动生成</text>
            </view>
          </view>
          <text class="view-all" @click="onMoreProducts">更多</text>
        </view>
        <scroll-view scroll-x class="products-scroll">
          <view v-for="product in safeProducts" :key="product.id" class="product-card">
            <view class="product-img" :style="{ background: product.imgBg }" />
            <view class="product-info">
              <view class="safe-badge">
                <text class="safe-icon luc-shield" />
                <text class="safe-text">安全</text>
              </view>
              <text class="product-name">{{ product.name }}</text>
              <text class="product-desc">{{ product.desc }}</text>
              <text class="product-price">{{ product.price }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 成分预警提示卡片 -->
      <view class="warning-card">
        <view class="warning-icon-wrap">
          <text class="warning-icon luc-bell" />
        </view>
        <view class="warning-info">
          <text class="warning-title">成分预警已开启</text>
          <text class="warning-desc">
            浏览商品详情时，系统将自动比对商品成分表与 Milo 的过敏原列表，并弹出安全提醒。
          </text>
        </view>
      </view>
    </view>

    <!-- 底部浮动按钮 -->
    <view class="float-btn safe-area-bottom" @click="onAddRecord">
      <text class="float-btn-text">
        <text class="luc luc-plus" />
        添加过敏记录
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.petAllergyProfile')


const goBack = () => uni.navigateBack()



// 宠物选择
const pets = [
  {
    id: 'milo',
    name: 'Milo',
    initial: 'M',
    bgColor: 'linear-gradient(135deg, #9fcbff, #66abff)',
    textColor: '#fff',
  },
  {
    id: 'luna',
    name: 'Luna',
    initial: 'L',
    bgColor: 'linear-gradient(135deg, #e5e5ea, #d1d1d6)',
    textColor: '#6e6e73',
  },
]
const currentPetId = ref('milo')

const selectPet = (id) => {
  currentPetId.value = id
}

// 已标记过敏原
const markedAllergens = ref(['鸡肉', '谷物', '乳制品'])

// 常见过敏原
const commonAllergens = ['牛肉', '鱼类', '大豆', '香精', '防腐剂', '花粉', '跳蚤']

// 移除过敏原
const removeAllergen = (item) => {
  markedAllergens.value = markedAllergens.value.filter((a) => a !== item)
  uni.showToast({ title: '已移除 ' + item, icon: 'none' })
}

// 添加过敏原
const addAllergen = (item) => {
  if (!markedAllergens.value.includes(item)) {
    markedAllergens.value.push(item)
    uni.showToast({ title: '已添加 ' + item, icon: 'none' })
  }
}

// 自定义过敏原输入
const customAllergen = ref('')
const addCustomAllergen = () => {
  const val = customAllergen.value.trim()
  if (val && !markedAllergens.value.includes(val)) {
    markedAllergens.value.push(val)
    customAllergen.value = ''
    uni.showToast({ title: '已添加 ' + val, icon: 'none' })
  }
}

// 过敏记录时间线
const allergyRecords = [
  {
    id: 1,
    title: '皮肤红肿',
    date: '2026-06-20',
    description: '怀疑鸡肉过敏，食用鸡肉配方狗粮后出现腹部皮肤泛红、轻微瘙痒',
    dotBg: '#ffecea',
    dotBorder: 'var(--color-error)',
    titleColor: 'var(--color-error)',
  },
  {
    id: 2,
    title: '轻微腹泻',
    date: '2026-05-15',
    description: '食用含谷物零食后出现软便，持续约 24 小时后自行恢复',
    dotBg: '#e8f2ff',
    dotBorder: '#66abff',
    titleColor: '#0064d6',
  },
  {
    id: 3,
    title: '打喷嚏',
    date: '2026-04-02',
    description: '花粉季节开始，频繁打喷嚏及鼻部发痒，疑似环境过敏原',
    dotBg: '#e8f2ff',
    dotBorder: '#66abff',
    titleColor: '#0064d6',
  },
]

// 安全商品
const safeProducts = [
  {
    id: 1,
    name: '无谷三文鱼配方',
    desc: '天然狗粮 2kg',
    price: '$38.99',
    imgBg: 'linear-gradient(145deg, #e8f2ff, #cfe5ff)',
  },
  {
    id: 2,
    name: '低敏羊奶粉',
    desc: '宠物营养补充 400g',
    price: '$24.50',
    imgBg: 'linear-gradient(145deg, #e9f9ee, #e8f2ff)',
  },
  {
    id: 3,
    name: '纯肉冻干零食',
    desc: '单一蛋白 100g',
    price: '$15.99',
    imgBg: 'linear-gradient(145deg, #e8f2ff, #e9f9ee)',
  },
]

// 操作
const onEdit = () => uni.showToast({ title: '进入编辑模式', icon: 'none' })
const onViewAll = () => uni.showToast({ title: '查看全部记录', icon: 'none' })
const onMoreProducts = () => uni.showToast({ title: '查看更多商品', icon: 'none' })
const onAddRecord = () => uni.showToast({ title: '添加过敏记录', icon: 'none' })
</script>

<style lang="scss" scoped>
.allergy-profile {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 160rpx;
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
  background: var(--color-text);
}

.back-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.back-icon {
  font-size: 48rpx;
  color: #fff;
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: #fff;
}

.edit-btn {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.15);
}

.edit-text {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  color: rgba(255, 255, 255, 0.85);
}

/* 内容区 */
.content {
  padding: 24rpx 24rpx 0;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

/* 宠物选择器 */
.pet-selector {
  display: flex;
  gap: 32rpx;
  justify-content: center;
  padding: 24rpx 0;
}

.pet-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  position: relative;
}

.pet-avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pet-avatar-text {
  font-size: 36rpx;
  font-weight: 800;
}
.pet-name {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}
.pet-name.active {
  color: var(--color-primary);
}
.pet-indicator {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: var(--color-primary);
  position: absolute;
  bottom: -8rpx;
}

/* 通用 section-header */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.section-count {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

.view-all {
  font-size: 24rpx;
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

/* 过敏原标签区 */
.allergen-section {
  display: flex;
  flex-direction: column;
}

.marked-allergens {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.allergen-tag {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
}

.allergen-tag.marked {
  background: #ffecea;
  border: 1rpx solid var(--color-error);
}

.allergen-tag.marked .tag-text {
  color: var(--color-error);
  font-weight: var(--font-weight-medium);
  font-size: 24rpx;
}

.tag-close {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: var(--color-error);
  color: #fff;
  font-size: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.allergen-tag.common {
  background: var(--color-border);
  border: 1rpx solid var(--color-border);
}

.allergen-tag.common .tag-text {
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
  font-size: 24rpx;
}

.common-section {
  margin-bottom: 20rpx;
}
.common-label {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  display: block;
  margin-bottom: 16rpx;
}
.common-allergens {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

/* 自定义输入 */
.custom-input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.custom-input {
  flex: 1;
  height: 80rpx;
  padding: 0 24rpx;
  border-radius: 20rpx;
  background: var(--color-border);
  border: 1rpx solid var(--color-border);
  font-size: 28rpx;
  color: var(--color-text);
}

.add-btn {
  height: 80rpx;
  padding: 0 32rpx;
  border-radius: 20rpx;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.add-btn-text {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: #fff;
}

/* 时间线 */
.timeline-section {
  display: flex;
  flex-direction: column;
}
.timeline {
  position: relative;
  padding-left: 40rpx;
}
.timeline-line {
  position: absolute;
  left: 12rpx;
  top: 16rpx;
  bottom: 16rpx;
  width: 2rpx;
  background: var(--color-border);
}

.timeline-item {
  position: relative;
  padding-bottom: 40rpx;
}
.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: -32rpx;
  top: 8rpx;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  border: 4rpx solid;
}

.timeline-card {
  margin-left: 24rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.card-title {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
}
.card-date {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}
.card-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

/* 安全商品 */
.safe-products-section {
  display: flex;
  flex-direction: column;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.auto-badge {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(171, 185, 173, 0.2);
}
.auto-text {
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-success);
}

.products-scroll {
  white-space: nowrap;
}
.product-card {
  display: inline-flex;
  flex-direction: column;
  width: 280rpx;
  margin-right: 20rpx;
  border-radius: 20rpx;
  overflow: hidden;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.product-img {
  width: 100%;
  height: 280rpx;
}
.product-info {
  padding: 20rpx;
}

.safe-badge {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-bottom: 10rpx;
}
.safe-icon {
  font-size: 24rpx;
}
.safe-text {
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-success);
}

.product-name {
  font-size: 24rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-desc {
  font-size: 20rpx;
  color: var(--color-text-secondary);
  display: block;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-price {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  display: block;
  margin-top: 8rpx;
}

/* 预警卡片 */
.warning-card {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, rgba(219, 201, 138, 0.1), rgba(171, 185, 173, 0.1));
  border: 1rpx solid var(--color-primary);
}

.warning-icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.warning-icon {
  font-size: 32rpx;
}
.warning-title {
  font-size: 24rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  display: block;
  margin-bottom: 6rpx;
}
.warning-desc {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  line-height: 1.5;
  display: block;
}

/* 底部浮动按钮 */
.float-btn {
  position: fixed;
  bottom: 40rpx;
  left: 50%;
  transform: translateX(-50%);
  height: 96rpx;
  padding: 0 64rpx;
  border-radius: 999rpx;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16rpx 40rpx rgba(219, 201, 138, 0.3);
  z-index: 40;
}

.float-btn-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: #fff;
}
</style>
