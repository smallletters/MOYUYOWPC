<template>
  <view class="group-page">
    <!-- 自定义导航：返回 + 标题 + 创建 -->
    <view class="group-nav">
      <view class="group-nav-back" @tap="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="group-nav-title">{{ $t('dmGroup.createTitle') }}</text>
      <view
        class="group-nav-confirm"
        :class="{ 'group-nav-confirm--disabled': !canCreate }"
        @tap="submit"
      >
        <text class="group-nav-confirm-text">{{ $t('dmGroup.create') }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="group-body">
      <!-- 群名称 -->
      <view class="group-name-card">
        <text class="group-name-label">{{ $t('dmGroup.nameLabel') }}</text>
        <input
          v-model="groupName"
          class="group-name-input"
          maxlength="50"
          :placeholder="$t('dmGroup.namePlaceholder')"
          placeholder-class="group-name-placeholder"
        >
      </view>

      <!-- 从已有私信联系人中选择成员 -->
      <view class="member-head">
        <text class="member-title">{{ $t('dmGroup.selectMembers') }}</text>
        <text class="member-count">
          {{ $t('dmGroup.selected', { count: selectedIds.length }) }}
        </text>
      </view>

      <view
        v-for="user in candidates"
        :key="user.userId"
        class="member-item"
        @tap="toggle(user.userId)"
      >
        <image
          v-if="user.avatarUrl"
          :src="user.avatarUrl"
          class="member-avatar"
          mode="aspectFill"
        />
        <view v-else class="member-avatar member-avatar--letter">{{ user.letter }}</view>
        <text class="member-name">{{ user.nickname }}</text>
        <view class="member-check" :class="{ 'member-check--on': isSelected(user.userId) }">
          <text v-if="isSelected(user.userId)" class="member-check-icon">✓</text>
        </view>
      </view>

      <view v-if="loading" class="member-empty">{{ $t('dmGroup.loading') }}</view>
      <view v-else-if="!candidates.length" class="member-empty">{{ $t('dmGroup.empty') }}</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { t } from '@/i18n'
import { messageApi } from '@/api'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { useUserStore } from '@/store'

const groupName = ref('')
const candidates = ref([])
const selectedIds = ref([])
const loading = ref(false)
const submitting = ref(false)

/** 群名称 + 至少 1 位成员才能创建 */
const canCreate = computed(
  () => groupName.value.trim().length > 0 && selectedIds.value.length > 0 && !submitting.value,
)

/** 勾选/取消勾选成员 */
function isSelected(userId) {
  return selectedIds.value.includes(userId)
}

function toggle(userId) {
  const index = selectedIds.value.indexOf(userId)
  if (index >= 0) selectedIds.value.splice(index, 1)
  else selectedIds.value.push(userId)
}

/** 从已有单聊会话中提取可邀请联系人（排除自己与群聊会话） */
async function loadCandidates() {
  loading.value = true
  try {
    const me = String(useUserStore().userId || '')
    const page = await messageApi.listConversations({ page: 1, size: 50 })
    const seen = new Set()
    candidates.value = ((page && page.records) || [])
      .filter((conv) => (conv.type || 'SINGLE') === 'SINGLE')
      .map((conv) => ({
        userId: String(conv.targetUserId || ''),
        nickname: conv.nickname || '',
        avatarUrl: conv.avatar ? toAbsoluteImageUrl(conv.avatar) : '',
        letter: String(conv.nickname || 'U')
          .charAt(0)
          .toUpperCase(),
      }))
      .filter((user) => {
        if (!user.userId || user.userId === me || seen.has(user.userId)) return false
        seen.add(user.userId)
        return true
      })
  } catch (e) {
    candidates.value = []
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canCreate.value) {
    if (!groupName.value.trim()) {
      uni.showToast({ title: t('dmGroup.nameRequired'), icon: 'none' })
    } else if (!selectedIds.value.length) {
      uni.showToast({ title: t('dmGroup.memberRequired'), icon: 'none' })
    }
    return
  }
  submitting.value = true
  uni.showLoading({ title: t('dmGroup.creating'), mask: true })
  try {
    const group = await messageApi.createGroup(groupName.value.trim(), selectedIds.value)
    uni.hideLoading()
    const name = group.nickname || group.name || groupName.value.trim()
    uni.redirectTo({
      url: `/pages/community/dm-chat?groupId=${group.id}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(name.charAt(0).toUpperCase())}`,
    })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || t('dmGroup.createFailed'), icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function goBack() {
  uni.navigateBack()
}

onMounted(loadCandidates)
</script>

<style lang="scss" scoped>
.group-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
  overflow: hidden;
}

.group-nav {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  min-height: calc(44px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  border-bottom: 1rpx solid var(--color-border);
  background: var(--color-background);
  box-sizing: border-box;
}

.group-nav-back {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.group-nav-back .luc {
  font-size: 36rpx;
  color: var(--color-text);
}

.group-nav-title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.group-nav-confirm {
  min-width: 96rpx;
  padding: 10rpx 20rpx;
  border-radius: 28rpx;
  background: var(--color-primary);
  text-align: center;
}

.group-nav-confirm--disabled {
  opacity: 0.5;
}

.group-nav-confirm-text {
  font-size: 24rpx;
  color: #ffffff;
}

.group-body {
  flex: 1;
  min-height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}

.group-name-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.group-name-label {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.group-name-input {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: var(--color-text);
}

.group-name-placeholder {
  color: var(--color-text-tertiary);
}

.member-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 8rpx 16rpx;
}

.member-title {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text);
}

.member-count {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

.member-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 12rpx;
  border-radius: 20rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.member-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.member-avatar--letter {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.member-name {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-check {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.member-check--on {
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.member-check-icon {
  font-size: 24rpx;
  color: #ffffff;
}

.member-empty {
  padding: 48rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
</style>
