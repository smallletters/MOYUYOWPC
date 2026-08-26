<template>
  <view class="mission-center">
    <view class="nav-header">
      <view class="nav-back" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">任务中心</text>
      <view class="nav-placeholder" />
    </view>

    <view class="content">
      <view class="progress-card">
        <view class="ring-wrapper">
          <view class="ring-progress">
            <svg width="240" height="240" viewBox="0 0 120 120">
              <circle
                class="ring-track"
                cx="60"
                cy="60"
                r="50" />
              <circle
                class="ring-bar"
                cx="60"
                cy="60"
                r="50"
                stroke-dasharray="314.16"
                :stroke-dashoffset="ringOffset"
              />
            </svg>
            <view class="ring-text">
              <text class="ring-count">{{ dailyDone }}/{{ dailyTotal }}</text>
              <text class="ring-label">今日任务</text>
            </view>
          </view>

          <view class="ring-side">
            <text class="ring-side-label">今日已获积分</text>
            <text class="ring-side-points">+{{ todayPoints }}</text>
            <view class="streak-row">
              <text class="flame-icon"><text class="luc luc-flame"></text></text>
              <text class="streak-text">连续签到 {{ streak }} 天</text>
            </view>
          </view>
        </view>
      </view>

      <view class="tab-section">
        <view class="tab-bar">
          <view
            v-for="tab in tabs"
            :key="tab.key"
            class="tab-btn"
            :class="{ active: activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            <text>{{ tab.label }}</text>
          </view>
        </view>

        <view v-show="activeTab === 'daily'" class="tab-panel">
          <view v-for="(mission, idx) in dailyMissions" :key="mission.id || idx" class="mission-item">
            <view class="mission-icon" :style="{ background: mission.bgColor }">
              <text class="mission-emoji luc" :class="$luc(mission.icon)"></text>
            </view>
            <view class="mission-info">
              <view class="mission-title-row">
                <text class="mission-name">{{ mission.name }}</text>
                <text class="mission-points">+{{ mission.points }}</text>
              </view>
              <view v-if="mission.total > 1" class="mission-progress-row">
                <view class="progress-track">
                  <view
                    class="progress-fill"
                    :class="{ complete: mission.done >= mission.total }"
                    :style="{ width: Math.min(100, (mission.done / mission.total) * 100) + '%' }"
                  />
                </view>
                <text class="progress-text">{{ mission.done }}/{{ mission.total }}</text>
              </view>
              <text v-else class="mission-status" :class="{ done: mission.completed || mission.claimed }">
                {{ mission.claimed ? '已领取' : (mission.completed ? '可领取' : (mission.done > 0 ? '进行中' : '未完成')) }}
              </text>
            </view>
            <view
              class="mission-btn"
              :class="mission.claimed ? 'btn-done' : (mission.completed ? 'btn-claim' : 'btn-go')"
              @click="onMissionAction(mission)"
            >
              {{ mission.claimed ? '已领取' : (mission.completed ? '领取' : '去完成') }}
            </view>
          </view>
        </view>

        <view v-show="activeTab === 'weekly'" class="tab-panel">
          <view v-for="(mission, idx) in weeklyMissions" :key="mission.id || idx" class="mission-item">
            <view class="mission-icon" :style="{ background: mission.bgColor }">
              <text class="mission-emoji luc" :class="$luc(mission.icon)"></text>
            </view>
            <view class="mission-info">
              <view class="mission-title-row">
                <text class="mission-name">{{ mission.name }}</text>
                <text class="mission-points">+{{ mission.points }}</text>
              </view>
              <view v-if="mission.total > 1" class="mission-progress-row">
                <view class="progress-track">
                  <view
                    class="progress-fill"
                    :class="{ complete: mission.done >= mission.total }"
                    :style="{ width: Math.min(100, (mission.done / mission.total) * 100) + '%' }"
                  />
                </view>
                <text class="progress-text">{{ mission.done }}/{{ mission.total }}</text>
              </view>
              <text v-else class="mission-status" :class="{ done: mission.completed || mission.claimed }">
                {{ mission.claimed ? '已领取' : (mission.completed ? '可领取' : (mission.done > 0 ? '进行中' : '未完成')) }}
              </text>
            </view>
            <view
              class="mission-btn"
              :class="mission.claimed ? 'btn-done' : (mission.completed ? 'btn-claim' : 'btn-go')"
              @click="onMissionAction(mission)"
            >
              {{ mission.claimed ? '已领取' : (mission.completed ? '领取' : '去完成') }}
            </view>
          </view>
        </view>

        <view v-show="activeTab === 'achievement'" class="tab-panel">
          <view v-for="(ach, idx) in achievements" :key="ach.id || idx" class="mission-item">
            <view class="badge-icon" :class="{ earned: ach.earned }">
              <text class="badge-emoji luc" :class="$luc(ach.earned  ?  'trophy'  :  'lock')"></text>
            </view>
            <view class="mission-info">
              <view class="mission-title-row">
                <text class="mission-name">{{ ach.name }}</text>
                <text class="mission-points" v-if="ach.points">+{{ ach.points }}</text>
              </view>
              <view v-if="ach.total > 1" class="mission-progress-row">
                <view class="progress-track">
                  <view
                    class="progress-fill"
                    :class="{ complete: ach.done >= ach.total }"
                    :style="{ width: Math.min(100, (ach.done / ach.total) * 100) + '%' }"
                  />
                </view>
                <text class="progress-text">{{ ach.done }}/{{ ach.total }}</text>
              </view>
              <view v-else class="mission-status">
                <text v-if="ach.claimed" class="earned-text">已获得</text>
                <text v-else-if="ach.completed" class="earned-text">可领取</text>
                <text v-else class="locked-text">未达成</text>
              </view>
            </view>
            <view
              class="mission-btn"
              :class="ach.claimed ? 'btn-done' : (ach.completed ? 'btn-claim' : 'btn-go')"
              @click="onMissionAction(ach)"
            >
              {{ ach.claimed ? '已获得' : (ach.completed ? '领取' : '去完成') }}
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { missionApi } from '@/api'

const CIRCUMFERENCE = 314.16

export default {
  data() {
    return {
      activeTab: 'daily',
      tabs: [
        { key: 'daily', label: '每日' },
        { key: 'weekly', label: '每周' },
        { key: 'achievement', label: '成就' },
      ],
      dailyDone: 0,
      dailyTotal: 0,
      todayPoints: 0,
      streak: 0,
      dailyMissions: [],
      weeklyMissions: [],
      achievements: [],
    }
  },

  computed: {
    ringOffset() {
      if (this.dailyTotal === 0) return CIRCUMFERENCE
      const ratio = this.dailyDone / this.dailyTotal
      return CIRCUMFERENCE * (1 - ratio)
    },
  },

  onLoad() {
    this.loadMissions()
    this.loadStats()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    switchTab(key) {
      this.activeTab = key
    },

    async loadMissions() {
      try {
        // 后端返回 { code, data: { daily, weekly, achievements }, success }
        const res = await missionApi.getGroupedMissions()
        const data = res?.data || res || {}
        this.dailyMissions = Array.isArray(data.daily) ? data.daily : []
        this.weeklyMissions = Array.isArray(data.weekly) ? data.weekly : []
        this.achievements = Array.isArray(data.achievements) ? data.achievements : []
      } catch (e) {
        console.warn('[mission-center] load failed', e)
        this.dailyMissions = []
        this.weeklyMissions = []
        this.achievements = []
      }
    },

    async loadStats() {
      try {
        const res = await missionApi.getMissionStats()
        const stats = res.data || {}
        this.dailyDone = stats.dailyDone || 0
        this.dailyTotal = stats.dailyTotal || 0
        this.todayPoints = stats.todayPoints || 0
        this.streak = stats.streak || 0
      } catch {
        // 静默失败
      }
    },

    async onMissionAction(mission) {
      // 已完成但未领取 → 调领取接口
      if (mission.completed && !mission.claimed && mission.id) {
        try {
          await missionApi.claimMission(mission.id)
          uni.showToast({ title: `已领取 +${mission.points} 积分`, icon: 'success' })
          this.loadMissions()
          this.loadStats()
        } catch (e) {
          uni.showToast({ title: (e && e.message) || '领取失败', icon: 'none' })
        }
        return
      }
      // 已领取 → 提示
      if (mission.claimed) {
        uni.showToast({ title: '已领取', icon: 'none' })
        return
      }
      // 未完成 → 提示用户前往对应页面
      uni.showToast({ title: `前往: ${mission.name}`, icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.mission-center {
  min-height: 100vh;
  background: #f2f2f7;
  display: flex;
  flex-direction: column;
}

.nav-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--color-background, #ffffff);
  border-bottom: 1rpx solid #e5e5ea;
  position: sticky;
  top: 0;
  z-index: 20;
}

.nav-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.back-icon {
  font-size: 40rpx;
  color: var(--color-primary, #007aff);
  font-weight: 300;
}

.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text, #1d1d1f);
}

.nav-placeholder {
  width: 64rpx;
}

.content {
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.progress-card {
  background: linear-gradient(135deg, var(--color-primary, #007aff), #004fad);
  color: #ffffff;
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 122, 255, 0.3);
}

.ring-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ring-progress {
  position: relative;
  width: 240rpx;
  height: 240rpx;
  flex-shrink: 0;
}

.ring-progress svg {
  transform: rotate(-90deg);
}

.ring-track {
  fill: none;
  stroke: rgba(255, 255, 255, 0.2);
  stroke-width: 8;
}

.ring-bar {
  fill: none;
  stroke: #ffffff;
  stroke-width: 8;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.6s ease;
}

.ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-count {
  font-size: 48rpx;
  font-weight: 700;
  line-height: 1;
}

.ring-label {
  font-size: 22rpx;
  margin-top: 8rpx;
  opacity: 0.8;
}

.ring-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
}

.ring-side-label {
  font-size: 24rpx;
  opacity: 0.7;
}

.ring-side-points {
  font-size: 60rpx;
  font-weight: 700;
  line-height: 1;
}

.streak-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 8rpx;
}

.flame-icon {
  font-size: 28rpx;
  opacity: 0.8;
}

.streak-text {
  font-size: 24rpx;
  opacity: 0.8;
}

.tab-section {
  background: var(--color-background, #ffffff);
  border-radius: 24rpx;
  overflow: hidden;
  border: 1rpx solid #e5e5ea;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.08);
}

.tab-bar {
  display: flex;
  border-bottom: 1rpx solid #e5e5ea;
}

.tab-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 500;
  color: #8e8e93;
  background: transparent;
  position: relative;
  cursor: pointer;
  transition: color 0.2s ease;
}

.tab-btn.active {
  font-weight: 600;
  color: var(--color-primary, #007aff);
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 64rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-primary, #007aff);
}

.tab-panel {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.mission-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 32rpx;
  background: var(--color-background, #ffffff);
  border: 1rpx solid #e5e5ea;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.18s ease;
}

.mission-item:active {
  transform: scale(0.985);
}

.mission-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mission-emoji {
  font-size: 36rpx;
}

.mission-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.mission-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.mission-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--color-text, #1d1d1f);
}

.mission-points {
  font-size: 24rpx;
  font-weight: 600;
  color: #ff9500;
}

.mission-progress-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.progress-track {
  flex: 1;
  height: 12rpx;
  border-radius: 6rpx;
  background: #f2f2f7;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 6rpx;
  background: var(--color-primary, #007aff);
  transition: width 0.4s ease;
}

.progress-fill.complete {
  background: var(--color-success, #34c759);
}

.progress-text {
  font-size: 24rpx;
  color: #6e6e73;
  flex-shrink: 0;
}

.mission-status {
  font-size: 24rpx;
  color: #8e8e93;
}

.mission-status.done {
  color: var(--color-success, #34c759);
}

.mission-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12rpx 32rpx;
  border-radius: 999px;
  font-size: 24rpx;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;
  border: none;
  transition: opacity 0.18s ease;
}

.mission-btn:active {
  filter: brightness(0.92);
}

.btn-go {
  background: var(--color-primary, #007aff);
  color: #ffffff;
}

.btn-claim {
  background: var(--color-success, #34c759);
  color: #ffffff;
}

.btn-done {
  background: #f2f2f7;
  color: #8e8e93;
  cursor: default;
}

.badge-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.badge-icon.earned {
  background: linear-gradient(135deg, var(--color-primary, #007aff), #0064d6);
}

.badge-icon:not(.earned) {
  background: #f2f2f7;
}

.badge-emoji {
  font-size: 36rpx;
}

.earned-text {
  color: var(--color-success, #34c759);
  font-size: 24rpx;
}

.locked-text {
  color: #8e8e93;
  font-size: 24rpx;
}
</style>
