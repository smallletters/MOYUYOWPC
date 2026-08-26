<template>
  <view class="crowdfunding-page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-btn" @click="goBack">
        <text class="nav-back"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">众筹</text>
      <view class="nav-btn" />
    </view>

    <!-- 标签筛选 -->
    <scroll-view scroll-x class="tab-scroll">
      <view class="tab-row">
        <view
          v-for="tab in tabs"
          :key="tab"
          class="tab-item"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          <text class="tab-text">{{ tab }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 项目列表 -->
    <view class="project-list">
      <view
        v-for="project in projects"
        :key="project.id"
        class="project-card"
        @click="goDetail(project)"
      >
        <!-- 项目封面 -->
        <view class="project-cover" :style="{ background: project.coverBg }">
          <view class="cover-badge" :style="{ background: project.statusBg }">
            <text class="cover-badge-text">{{ project.status }}</text>
          </view>
          <!-- 倒计时 -->
          <view v-if="project.countdown" class="cover-countdown">
            <text class="cover-countdown-text">{{ project.countdown }}</text>
          </view>
        </view>

        <!-- 项目信息 -->
        <view class="project-info">
          <text class="project-name">{{ project.name }}</text>
          <text class="project-desc">{{ project.desc }}</text>

          <!-- 进度条 -->
          <view class="fund-progress">
            <view class="progress-track">
              <view class="progress-fill" :style="{ width: project.percent + '%' }" />
            </view>
            <view class="progress-stats">
              <text class="progress-percent">{{ project.percent }}%</text>
              <text class="progress-amount">已筹 ¥{{ project.raised }}</text>
            </view>
          </view>

          <!-- 底部信息 -->
          <view class="project-footer">
            <view class="supporter-count">
              <text class="supporter-icon"><text class="luc luc-users"></text></text>
              <text class="supporter-text">{{ project.supporters }}人支持</text>
            </view>
            <view class="min-pledge">
              <text class="pledge-text">¥{{ project.minPledge }} 起</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 加载更多 -->
    <view v-if="hasMore" class="load-more" @click="loadMore">
      <text class="load-more-text">查看更多项目</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

// 标签
const tabs = ref(['全部', '进行中', '即将结束', '即将开始', '已成功'])
const activeTab = ref('全部')

// 众筹项目列表
const projects = ref([
  {
    id: 1,
    name: 'MOYUYO 智能宠物饮水机 Pro',
    desc: 'APP远程控制 · 活水循环过滤 · 水位智能监测',
    coverBg: 'linear-gradient(135deg, #e8f2ff, #66abff)',
    status: '进行中',
    statusBg: 'rgba(0, 122, 255, 0.8)',
    countdown: '剩余 12 天',
    percent: 156,
    raised: '46,800',
    supporters: 326,
    minPledge: 199,
  },
  {
    id: 2,
    name: '极简猫爬架 · 模块化设计',
    desc: '自由组合 · 剑麻柱体 · 环保密度板 · 承重50kg',
    coverBg: 'linear-gradient(135deg, #e9f9ee, #34c759)',
    status: '即将结束',
    statusBg: 'rgba(255, 149, 0, 0.8)',
    countdown: '剩余 2 天',
    percent: 89,
    raised: '26,700',
    supporters: 198,
    minPledge: 299,
  },
  {
    id: 3,
    name: '宠物有机零食礼盒',
    desc: '6种口味 · 天然有机原料 · 无添加 · 烘焙工艺',
    coverBg: 'linear-gradient(135deg, #fff4e5, #ff9500)',
    status: '即将开始',
    statusBg: 'rgba(0, 122, 255, 0.8)',
    countdown: '7月20日开启',
    percent: 0,
    raised: '0',
    supporters: 0,
    minPledge: 68,
  },
  {
    id: 4,
    name: 'MOYUYO 宠物推车 · 城市出行版',
    desc: '一键折叠 · 360°旋转轮 · 透气网窗 · 防雨罩',
    coverBg: 'linear-gradient(135deg, #f2f2f7, #c7c7cc)',
    status: '已成功',
    statusBg: 'rgba(52, 199, 89, 0.8)',
    countdown: '',
    percent: 234,
    raised: '70,200',
    supporters: 512,
    minPledge: 399,
  },
])

// 是否还有更多
const hasMore = ref(true)

// 返回
const goBack = () => {
  uni.navigateBack()
}

// 跳转详情
const goDetail = (project) => {
  uni.showToast({ title: project.name, icon: 'none' })
}

// 加载更多
const loadMore = () => {
  uni.showToast({ title: '加载中...', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.crowdfunding-page {
  min-height: 100vh;
  background: var(--color-background);
}

// 导航栏
.nav-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--color-card);
  border-bottom: 1rpx solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 30;
}
.nav-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-back {
  font-size: 40rpx;
  color: var(--color-text);
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text);
}

// 标签筛选
.tab-scroll {
  white-space: nowrap;
  background: var(--color-card);
  border-bottom: 1rpx solid var(--color-border);
}
.tab-row {
  display: inline-flex;
  gap: 8rpx;
  padding: 16rpx 24rpx;
}
.tab-item {
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: var(--color-background);
}
.tab-item.active {
  background: var(--color-primary);
}
.tab-text {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--color-text-secondary);
}
.tab-item.active .tab-text {
  font-weight: 600;
  color: #fff;
}

// 项目列表
.project-list {
  padding: 24rpx;
}

// 项目卡片
.project-card {
  background: var(--color-card);
  border-radius: 24rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
}

// 项目封面
.project-cover {
  height: 340rpx;
  position: relative;
  display: flex;
  align-items: flex-end;
  padding: 20rpx;
}
.cover-badge {
  position: absolute;
  top: 20rpx;
  left: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}
.cover-badge-text {
  font-size: 22rpx;
  font-weight: 600;
  color: #fff;
}
.cover-countdown {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.4);
}
.cover-countdown-text {
  font-size: 22rpx;
  font-weight: 500;
  color: #fff;
}

// 项目信息
.project-info {
  padding: 24rpx;
}
.project-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
}
.project-desc {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  margin-top: 8rpx;
}

// 众筹进度
.fund-progress {
  margin-top: 20rpx;
}
.progress-track {
  height: 12rpx;
  border-radius: 999rpx;
  background: var(--color-background);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: var(--color-primary);
  transition: width 0.4s ease;
}
.progress-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8rpx;
}
.progress-percent {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--color-primary);
}
.progress-amount {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}

// 底部信息
.project-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--color-border);
}
.supporter-count {
  display: flex;
  align-items: center;
  gap: 6rpx;
}
.supporter-icon {
  font-size: 26rpx;
}
.supporter-text {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}
.min-pledge {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: #e8f2ff;
}
.pledge-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-primary);
}

// 加载更多
.load-more {
  padding: 32rpx;
  text-align: center;
}
.load-more-text {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--color-primary);
}
</style>
