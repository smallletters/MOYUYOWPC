<template>
  <view class="dm-page" :style="pageStyle">
    <!-- 导航栏 -->
    <view class="dm-header">
      <view class="nav-back" @tap="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="nav-title">
        {{ targetUser.name || $t('dmChat.title') }}
        <text v-if="isGroup" class="nav-group-count">({{ groupMembers.length }})</text>
      </text>
      <view class="nav-actions">
        <view class="nav-action" @tap="openSearch">
          <text class="luc luc-search" />
        </view>
        <view class="nav-action" @tap="showMoreOptions">
          <text class="luc luc-more-horizontal" />
        </view>
      </view>
    </view>

    <!-- 聊天主体区域 -->
    <scroll-view
      class="dm-body"
      scroll-y
      :scroll-into-view="scrollToId"
      :scroll-with-animation="true"
    >
      <!-- 对方信息卡片（可折叠） -->
      <view class="profile-card">
        <view class="profile-toggle" @tap="toggleProfile">
          <view class="profile-left">
            <view class="avatar-circle">
              <image
                v-if="targetUser.avatarUrl"
                :src="targetUser.avatarUrl"
                class="avatar-img"
                mode="aspectFill"
              />
              <text v-else class="avatar-letter">{{ targetUser.avatar }}</text>
            </view>
            <view class="profile-info">
              <text class="profile-name">{{ targetUser.name }}</text>
            </view>
          </view>
          <text class="chevron" :class="{ rotated: profileCollapsed }">
            <text class="luc luc-arrow-left" />
          </text>
        </view>
        <!-- 可折叠内容 -->
        <view class="profile-card-body" :class="{ collapsed: profileCollapsed }">
          <view class="view-profile-btn" @tap="viewProfile">
            <text class="view-profile-text">
              {{ isGroup ? $t('dmChat.viewGroupMembers') : $t('dm.viewProfile') }}
            </text>
          </view>
        </view>
      </view>

      <!-- 群公告 -->
      <view
        v-if="isGroup && groupAnnouncement"
        class="group-announcement"
        @tap="showGroupAnnouncement"
      >
        <text class="group-announcement-label">{{ $t('dmChat.groupAnnouncement') }}</text>
        <text class="group-announcement-text">{{ groupAnnouncement }}</text>
      </view>

      <!-- 消息列表 -->
      <view class="message-list">
        <!-- 历史消息加载：还有更早消息时展示 -->
        <view v-if="hasMore" class="load-older" @tap="loadOlderMessages">
          <text class="load-older-text">
            {{ loadingOlder ? $t('dmChat.loadingOlder') : $t('dmChat.loadOlder') }}
          </text>
        </view>

        <!-- 空会话提示：后端会话初始没有消息，不再插入假的欢迎语 -->
        <view v-if="!messages.length && !loading" class="message-empty">
          <text class="message-empty-text">{{ $t('dmChat.emptyHint') }}</text>
        </view>

        <!-- 消息项：外层 message-block 用于承载时间分隔线 + 气泡 -->
        <view
          v-for="(msg, index) in messages"
          :id="`msg-${index}`"
          :key="msg.id || index"
          class="message-block"
        >
          <!-- 时间分隔：首条或与上一条间隔超过 5 分钟时展示 -->
          <view v-if="showTimeDivider(index)" class="msg-time-divider">
            <text class="msg-time-text">{{ formatMessageTime(msg.time) }}</text>
          </view>

          <!-- 已撤回消息：居中系统提示，不再展示原内容 -->
          <view v-if="msg.recalled" class="msg-recalled-row">
            <text class="msg-recalled-text">
              {{ msg.isSelf ? $t('dmChat.recalledSelf') : $t('dmChat.recalledOther') }}
            </text>
          </view>

          <template v-else>
            <!-- 引用回复：展示被引用消息摘要，点击定位原消息 -->
            <view
              v-if="msg.quoteContent"
              class="msg-quote-row"
              :class="{ 'msg-quote-row--self': msg.isSelf }"
              @tap.stop="jumpToQuoted(msg)"
            >
              <text class="msg-quote-text">{{ msg.quoteContent }}</text>
            </view>

            <!-- 群聊：对方消息展示发送者昵称 -->
            <view v-if="isGroup && !msg.isSelf" class="msg-group-sender-row">
              <text class="msg-group-sender">
                {{ msg.senderNickname || $t('messagesCenter.defaultName') }}
              </text>
            </view>

            <view
              class="message-item"
              :class="{ 'message-self': msg.isSelf, 'message-remote': !msg.isSelf }"
              @tap="onMessageTap(msg)"
              @longpress="onLongPressMessage(msg)"
            >
              <!-- 多选模式：勾选圈 -->
              <view
                v-if="selectMode"
                class="msg-check"
                :class="{ 'msg-check--on': isSelected(msg) }"
              >
                <text v-if="isSelected(msg)" class="msg-check-icon">✓</text>
              </view>

              <!-- 对方头像（群聊按发送者展示） -->
              <view v-if="!msg.isSelf" class="msg-avatar">
                <image
                  v-if="isGroup ? msg.senderAvatarUrl : targetUser.avatarUrl"
                  :src="isGroup ? msg.senderAvatarUrl : targetUser.avatarUrl"
                  class="msg-avatar-img"
                  mode="aspectFill"
                />
                <text v-else class="avatar-letter-sm">
                  {{ isGroup ? msg.senderLetter : targetUser.avatar }}
                </text>
              </view>

              <!-- 自己头像（镜像抖音：自己消息右侧同样展示头像） -->
              <view v-else class="msg-avatar msg-avatar--self">
                <image
                  v-if="myAvatarUrl"
                  :src="myAvatarUrl"
                  class="msg-avatar-img"
                  mode="aspectFill"
                />
                <text v-else class="avatar-letter-sm">{{ myAvatarLetter }}</text>
              </view>

              <!-- 商品卡片消息 -->
              <view v-if="msg.type === 'product'" class="product-card">
                <view class="product-image">
                  <text class="product-emoji luc-spray-can" />
                </view>
                <view class="product-info">
                  <text class="product-name">{{ msg.product.name }}</text>
                  <text class="product-desc">{{ msg.product.desc }}</text>
                  <view class="product-bottom">
                    <text class="product-price">{{ msg.product.price }}</text>
                    <view class="product-cta">
                      <text class="product-cta-text">{{ $t('dmChat.viewDetail') }}</text>
                    </view>
                  </view>
                </view>
              </view>

              <!-- 图片消息 -->
              <view v-else-if="msg.type === 'image'" class="msg-image" @tap="previewImage(msg)">
                <image :src="displayMediaUrl(msg)" class="msg-image-content" mode="widthFix" />
              </view>

              <!-- 语音消息：宽度随时长变化，点击播放/停止 -->
              <view
                v-else-if="msg.type === 'voice'"
                class="msg-voice"
                :class="msg.isSelf ? 'msg-voice--self' : 'msg-voice--remote'"
                :style="{ width: voiceBubbleWidth(msg) }"
                @tap="playVoice(msg)"
              >
                <text
                  class="voice-icon"
                  :class="isPlayingVoice(msg) ? 'luc luc-pause' : 'luc luc-play'"
                />
                <view class="voice-wave">
                  <view v-for="n in 4" :key="n" class="voice-wave-bar" />
                </view>
                <text class="voice-duration">{{ msg.mediaDuration || 1 }}″</text>
              </view>

              <!-- 文件消息：点击下载/打开 -->
              <view v-else-if="msg.type === 'file'" class="msg-file" @tap="openFile(msg)">
                <text class="msg-file-icon luc luc-file-text" />
                <view class="msg-file-info">
                  <text class="msg-file-name">{{ msg.mediaName || $t('dmChat.fileMessage') }}</text>
                  <text class="msg-file-size">{{ formatFileSize(msg.mediaSize) }}</text>
                </view>
              </view>

              <!-- 普通文本消息（群聊支持 @成员 高亮） -->
              <view v-else class="bubble" :class="msg.isSelf ? 'bubble-self' : 'bubble-remote'">
                <text v-if="msg.mentionSegments && msg.mentionSegments.length" class="bubble-text">
                  <text
                    v-for="(seg, segIndex) in msg.mentionSegments"
                    :key="segIndex"
                    :class="{ 'bubble-mention': seg.mention }"
                  >
                    {{ seg.text }}
                  </text>
                </text>
                <text v-else class="bubble-text">{{ msg.content }}</text>
              </view>
            </view>
          </template>

          <!-- 已读回执：只在最后一条自己发送且未撤回的消息下方展示 -->
          <view
            v-if="msg.isSelf && msg.readFlag && index === lastSelfMessageIndex"
            class="msg-read-row"
          >
            <text class="msg-read-text">{{ $t('dmChat.read') }}</text>
          </view>

          <!-- 发送中 / 发送失败（失败可点击重试） -->
          <view v-if="msg.isSelf && msg.status === 'sending'" class="msg-status-row">
            <text class="msg-status-text">{{ $t('dmChat.sending') }}</text>
          </view>
          <view
            v-else-if="msg.isSelf && msg.status === 'failed'"
            class="msg-status-row msg-status-row--failed"
            @tap="retryMessage(msg)"
          >
            <text class="msg-status-text msg-status-text--failed">
              {{ $t('dmChat.sendFailedRetry') }}
            </text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 多选操作栏：进入多选模式后替换输入栏 -->
    <view v-if="selectMode" class="dm-select-bar">
      <text class="dm-select-cancel" @tap="exitSelectMode">{{ $t('common.cancel') }}</text>
      <view
        class="dm-select-forward"
        :class="{ 'dm-select-action--disabled': !selectedIds.length }"
        @tap="openForwardSelected"
      >
        <text class="dm-select-forward-text">
          {{ $t('dmChat.forwardSelected', { count: selectedIds.length }) }}
        </text>
      </view>
      <view
        class="dm-select-delete"
        :class="{ 'dm-select-action--disabled': !selectedIds.length }"
        @tap="confirmDeleteSelected"
      >
        <text class="dm-select-delete-text">
          {{ $t('dmChat.deleteSelected', { count: selectedIds.length }) }}
        </text>
      </view>
    </view>

    <!-- 底部输入栏 -->
    <view v-else class="dm-input-bar">
      <!-- 引用回复预览：可取消 -->
      <view v-if="quotedMessage" class="dm-quote-preview">
        <text class="dm-quote-preview-text">{{ quotedMessage.content }}</text>
        <text class="dm-quote-preview-close" @tap="quotedMessage = null">✕</text>
      </view>
      <view class="input-row">
        <view class="add-btn" @tap="addAttachment">
          <text class="add-icon">＋</text>
        </view>
        <!-- 语音：按住录音、松开发送（最长 60 秒） -->
        <view
          class="voice-btn"
          :class="{ 'voice-btn--active': recording }"
          @touchstart.prevent="startRecording"
          @touchend.prevent="stopRecording"
          @touchcancel.prevent="cancelRecording"
        >
          <text class="luc luc-mic" />
        </view>
        <!-- @成员：仅群聊展示 -->
        <view v-if="isGroup" class="mention-btn" @tap="showMentionPicker">
          <text class="mention-icon">@</text>
        </view>
        <view class="input-wrapper">
          <input
            v-model="inputText"
            class="msg-input"
            :placeholder="$t('dmChat.inputPlaceholder')"
            placeholder-class="msg-input-placeholder"
            confirm-type="send"
            :adjust-position="false"
            @focus="onInputFocus"
            @input="saveDraft"
            @confirm="sendMessage"
          >
        </view>
        <view class="send-btn" @tap="sendMessage">
          <text class="send-icon luc-send" />
        </view>
      </view>
      <!-- 录音中提示：浮在输入栏上方 -->
      <view v-if="recording" class="voice-recording-tip">
        <text class="voice-recording-dot" />
        <text class="voice-recording-text">{{ $t('dmChat.recording') }} {{ recordSeconds }}″</text>
      </view>
    </view>

    <!-- 会话内搜索面板 -->
    <view v-if="searchVisible" class="dm-search-mask" @tap="closeSearch">
      <view class="dm-search-panel" @tap.stop>
        <view class="dm-search-bar">
          <text class="luc luc-search dm-search-icon" />
          <input
            v-model="searchKeyword"
            class="dm-search-input"
            :placeholder="$t('dmChat.searchPlaceholder')"
            confirm-type="search"
            focus
            @input="onSearchInput"
          >
          <text class="dm-search-cancel" @tap="closeSearch">{{ $t('common.cancel') }}</text>
        </view>
        <scroll-view scroll-y class="dm-search-results">
          <view v-if="searching" class="dm-search-empty">{{ $t('dmChat.searching') }}</view>
          <view v-else-if="!searchResults.length" class="dm-search-empty">
            {{ searchKeyword ? $t('dmChat.searchEmpty') : $t('dmChat.searchHint') }}
          </view>
          <view
            v-for="item in searchResults"
            :key="item.id"
            class="dm-search-result"
            @tap="jumpToSearchResult(item)"
          >
            <text class="dm-search-result-text">{{ item.content }}</text>
            <text class="dm-search-result-time">{{ formatMessageTime(item.time) }}</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 转发面板：选择转发到的会话 -->
    <view v-if="forwardVisible" class="dm-forward-mask" @tap="closeForward">
      <view class="dm-forward-panel" @tap.stop>
        <text class="dm-forward-title">
          {{ $t('dmChat.forwardTitle') }} ({{ forwardMessages.length }})
        </text>
        <scroll-view scroll-y class="dm-forward-list">
          <view v-if="!forwardConversations.length" class="dm-forward-empty">
            {{ $t('messagesCenter.dmEmpty') }}
          </view>
          <view
            v-for="conv in forwardConversations"
            :key="conv.id"
            class="dm-forward-item"
            @tap="doForward(conv)"
          >
            <text class="dm-forward-name">
              {{ conv.nickname || $t('messagesCenter.defaultName') }}
            </text>
          </view>
        </scroll-view>
      </view>
    </view>
    <!-- 群成员面板 -->
    <view v-if="groupPanelVisible" class="dm-group-mask" @tap="closeGroupPanel">
      <view class="dm-group-panel" @tap.stop>
        <view class="dm-group-head">
          <text class="dm-group-title">
            {{ $t('dmChat.groupMembersTitle', { count: groupMembers.length }) }}
          </text>
          <text class="dm-group-close" @tap="closeGroupPanel">✕</text>
        </view>
        <scroll-view scroll-y class="dm-group-list">
          <view v-for="member in groupMembers" :key="member.userId" class="dm-group-item">
            <image
              v-if="member.avatarUrl"
              :src="member.avatarUrl"
              class="dm-group-avatar"
              mode="aspectFill"
            />
            <view v-else class="dm-group-avatar dm-group-avatar--letter">
              {{ member.letter }}
            </view>
            <view class="dm-group-info">
              <view class="dm-group-name-row">
                <text class="dm-group-name">{{ member.nickname }}</text>
                <text v-if="member.owner" class="dm-group-owner-tag">
                  {{ $t('dmChat.groupOwner') }}
                </text>
                <text v-if="member.isSelf" class="dm-group-self-tag">
                  {{ $t('dmChat.groupSelf') }}
                </text>
              </view>
            </view>
            <text
              v-if="isGroupOwner && !member.isSelf"
              class="dm-group-remove"
              @tap.stop="confirmRemoveMember(member)"
            >
              {{ $t('dmChat.removeMember') }}
            </text>
          </view>
        </scroll-view>
        <view class="dm-group-actions">
          <view v-if="isGroupOwner" class="dm-group-invite" @tap="openInvitePicker">
            <text class="dm-group-invite-text">{{ $t('dmChat.inviteMember') }}</text>
          </view>
          <view class="dm-group-leave" @tap="confirmLeaveGroup">
            <text class="dm-group-leave-text">{{ $t('dmChat.leaveGroup') }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 邀请成员面板：从已有私信联系人中选择 -->
    <view v-if="inviteVisible" class="dm-forward-mask" @tap="closeInvitePicker">
      <view class="dm-forward-panel" @tap.stop>
        <text class="dm-forward-title">{{ $t('dmChat.inviteTitle') }}</text>
        <scroll-view scroll-y class="dm-forward-list">
          <view v-if="!inviteCandidates.length" class="dm-forward-empty">
            {{ $t('dmChat.inviteEmpty') }}
          </view>
          <view
            v-for="candidate in inviteCandidates"
            :key="candidate.userId"
            class="dm-forward-item"
            @tap="inviteMember(candidate)"
          >
            <text class="dm-forward-name">{{ candidate.nickname }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { t } from '@/i18n'
import { usePageTitle } from '@/utils/i18nPageMixin'
import { getPageQuery } from '@/utils/pageQuery'
import { messageApi, blockApi } from '@/api'
import { uploadChatImage, uploadVoice, uploadFile } from '@/api/upload'
import { toAbsoluteImageUrl } from '@/utils/imageUrl'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { useUserStore } from '@/store'
import { connectDmSocket, onDmSocketEvent, syncMessageTabBadge } from '@/utils/dmSocket'
usePageTitle('pageTitle.communityDmChat')

/** 单页消息条数：与后端默认分页保持一致 */
const MESSAGE_PAGE_SIZE = 50
/** 时间分隔阈值：相邻消息超过 5 分钟才插入时间条 */
const TIME_DIVIDER_GAP_MS = 5 * 60 * 1000
/** 撤回时间窗口：2 分钟（与后端保持一致） */
const RECALL_WINDOW_MS = 2 * 60 * 1000
/** 录音上限：到点自动停止发送（后端允许更长，前端限制 60s 控制文件体积） */
const RECORD_LIMIT_SECONDS = 60
/** 文件大小上限：100MB（后端上传接口另有 multipart 限制） */
const MAX_FILE_SIZE = 100 * 1024 * 1024
/** 媒体消息类型 ↔ 后端 contentType */
const MEDIA_CONTENT_TYPE = { image: 'IMAGE', voice: 'VOICE', file: 'FILE' }

// 目标用户信息：从 URL query 取（name/avatar/userId）

const targetUser = ref({
  id: null,
  name: t('dmChat.defaultFriendName'),
  avatar: 'P',
  // 对方真实头像 URL（由 ensureConversation 返回，空时回落到首字母）
  avatarUrl: '',
})

const profileCollapsed = ref(false)
const inputText = ref('')
const scrollToId = ref('')
const messages = ref([])
// 后端会话ID（由 ensureConversation 返回），发送/拉取消息都依赖它
const conversationId = ref(null)
const loading = ref(false)
const sending = ref(false)
// 历史消息分页：当前已加载页 / 总条数 / 是否还有更早消息
const messagePage = ref(1)
const messageTotal = ref(0)
const hasMore = ref(false)
const loadingOlder = ref(false)
// WebSocket 取消订阅函数
let unsubDmSocket = null
// 键盘适配：H5 下页面高度需要跟随 visualViewport 收缩，否则输入栏会被软键盘遮挡
const pageStyle = ref({})
// H5 visualViewport 监听句柄（APP 端由 pages.json 的 softinputMode=adjustResize 处理）
let visualViewportHandler = null
// 本地待发送消息自增序号（发送失败的消息保留在列表中，可点击重试）
let localMessageSeq = 0
// 草稿防抖计时器
let draftTimer = null
// 会话内搜索面板状态
const searchVisible = ref(false)
const searchKeyword = ref('')
const searchResults = ref([])
const searching = ref(false)
let searchTimer = null
/** 草稿存储 Key：按目标用户维度，未创建会话时也能保存 */
const draftKey = computed(() => (targetUser.value.id ? `dm_draft_${targetUser.value.id}` : ''))
// 引用回复：当前待引用的消息（id + 展示摘要）
const quotedMessage = ref(null)
// 多选删除模式
const selectMode = ref(false)
const selectedIds = ref([])
// 转发：目标消息（可多选）+ 可选会话列表
const forwardVisible = ref(false)
const forwardConversations = ref([])
const forwardMessages = ref([])
const forwarding = ref(false)
// 录音状态：是否录音中 / 已录秒数
const recording = ref(false)
const recordSeconds = ref(0)
let recordTimer = null
// 录音实现：APP 用 RecorderManager，H5 用 MediaRecorder
let appRecorder = null
let h5Recorder = null
let h5Stream = null
let h5Chunks = []
let recordStartAt = 0
// 本次录音是否被取消（松开前上滑取消等）
let recordCancelled = false
// 语音播放：全局单例音频上下文 + 当前播放的消息ID
let audioCtx = null
const playingVoiceId = ref('')
// 群聊状态：群成员/群主/成员面板/邀请面板
const isGroup = ref(false)
const groupMembers = ref([])
const groupOwnerId = ref('')
const groupPanelVisible = ref(false)
const inviteVisible = ref(false)
const inviteCandidates = ref([])
const groupBusy = ref(false)
// 群公告
const groupAnnouncement = ref('')
// 当前用户是否群主（决定是否展示移除成员/邀请入口）
const isGroupOwner = computed(
  () => isGroup.value && String(groupOwnerId.value) === String(useUserStore().userId || ''),
)

/** 当前登录用户头像（自己消息右侧展示，抖音式） */
const myAvatarUrl = computed(() => {
  const info = useUserStore().userInfo || {}
  return info.avatar ? toAbsoluteImageUrl(info.avatar) : ''
})

/** 当前登录用户头像占位字母 */
const myAvatarLetter = computed(() => {
  const info = useUserStore().userInfo || {}
  return String(info.nickname || 'U')
    .charAt(0)
    .toUpperCase()
})

/** 最后一条自己发送且未撤回的消息索引：仅在该消息下方展示「已读」回执 */
const lastSelfMessageIndex = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    const msg = messages.value[i]
    if (msg.isSelf && !msg.recalled) return i
  }
  return -1
})

/** 后端 contentType → 页面展示的消息类型 */
function messageTypeOf(contentType) {
  const type = String(contentType || 'TEXT').toUpperCase()
  if (type === 'IMAGE') return 'image'
  if (type === 'VOICE') return 'voice'
  if (type === 'FILE') return 'file'
  return 'text'
}

/**
 * 群聊 @成员 高亮：优先按成员昵称完整匹配（支持昵称含空格），
 * 未匹配到成员时把 @ 后到空白为止的 token 也高亮，避免视觉割裂。
 */
function buildMentionSegments(content) {
  if (!isGroup.value) return null
  const text = String(content || '')
  if (!text || !text.includes('@')) return null

  const names = groupMembers.value
    .map((member) => member.nickname)
    .filter((name) => !!name)
    .sort((a, b) => b.length - a.length)

  const segments = []
  let cursor = 0
  while (cursor < text.length) {
    const at = text.indexOf('@', cursor)
    if (at < 0) {
      segments.push({ text: text.slice(cursor), mention: false })
      break
    }
    if (at > cursor) segments.push({ text: text.slice(cursor, at), mention: false })

    let matched = ''
    for (const name of names) {
      if (text.startsWith(name, at + 1)) {
        matched = name
        break
      }
    }
    if (matched) {
      segments.push({ text: '@' + matched, mention: true })
      cursor = at + 1 + matched.length
    } else {
      const rest = text.slice(at + 1)
      const stop = rest.search(/[\s,，。:：!！?？]/)
      const token = stop >= 0 ? rest.slice(0, stop) : rest
      if (token) {
        segments.push({ text: '@' + token, mention: true })
        cursor = at + 1 + token.length
      } else {
        segments.push({ text: '@', mention: false })
        cursor = at + 1
      }
    }
  }
  return segments.filter((segment) => segment.text)
}

/** 后端消息 → 页面展示结构 */
function mapMessages(records) {
  const me = String(useUserStore().userId || '')
  return (records || []).map((m) => ({
    id: String(m.id),
    isSelf: String(m.senderId) === me,
    type: messageTypeOf(m.contentType),
    content: m.content,
    time: m.createTime,
    recalled: m.recalled === 1 || m.recalled === true,
    readFlag: Number(m.readFlag) === 1,
    quoteMessageId: m.quoteMessageId ? String(m.quoteMessageId) : '',
    quoteContent: m.quoteContent || '',
    mediaDuration: Number(m.mediaDuration || 0),
    mediaName: m.mediaName || '',
    mediaSize: Number(m.mediaSize || 0),
    senderId: String(m.senderId || ''),
    senderNickname: m.senderNickname || '',
    senderAvatarUrl: m.senderAvatar ? toAbsoluteImageUrl(m.senderAvatar) : '',
    senderLetter: String(m.senderNickname || 'U')
      .charAt(0)
      .toUpperCase(),
    mentionSegments: buildMentionSegments(m.content),
  }))
}

/** 加载会话历史消息（默认取最新一页，单页内按时间正序） */
async function loadMessages() {
  if (!conversationId.value) return
  loading.value = true
  try {
    const page = await messageApi.listMessages(conversationId.value, {
      page: 1,
      size: MESSAGE_PAGE_SIZE,
    })
    const records = (page && page.records) || []
    messages.value = mapMessages(records)
    messagePage.value = 1
    messageTotal.value = Number((page && page.total) ?? records.length)
    hasMore.value = records.length > 0 && messages.value.length < messageTotal.value
    nextTick(() => {
      if (messages.value.length) scrollToId.value = `msg-${messages.value.length - 1}`
    })
  } catch (e) {
    console.warn('[dm-chat] load messages failed', e)
    uni.showToast({ title: t('messagesCenter.loadFailed'), icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 加载更早的历史消息：追加到列表头部，并保持当前阅读位置 */
async function loadOlderMessages() {
  if (!conversationId.value || !hasMore.value || loadingOlder.value) return
  loadingOlder.value = true
  try {
    const nextPage = messagePage.value + 1
    const page = await messageApi.listMessages(conversationId.value, {
      page: nextPage,
      size: MESSAGE_PAGE_SIZE,
    })
    const records = (page && page.records) || []
    // 按 id 去重，避免与 WebSocket 新消息/重复请求交叉时出现重复气泡
    const existing = new Set(messages.value.map((m) => m.id))
    const older = mapMessages(records).filter((m) => !existing.has(m.id))
    if (older.length) {
      messages.value = older.concat(messages.value)
      messagePage.value = nextPage
    }
    messageTotal.value = Math.max(messageTotal.value, Number((page && page.total) || 0))
    hasMore.value = records.length > 0 && messages.value.length < messageTotal.value
    // 旧内容插入后，把原来第一条消息滚动回视口顶部附近
    if (older.length) {
      nextTick(() => {
        scrollToId.value = `msg-${older.length}`
      })
    }
  } catch (e) {
    uni.showToast({ title: t('messagesCenter.loadFailed'), icon: 'none' })
  } finally {
    loadingOlder.value = false
  }
}

/** 两位数字补零 */
function pad2(n) {
  return String(n).padStart(2, '0')
}

/** 消息时间展示：当天只显示时分，跨天显示日期 + 时分 */
function formatMessageTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const hm = `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) return hm
  if (d.getFullYear() === now.getFullYear()) {
    return `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${hm}`
  }
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${hm}`
}

/** 首条消息或与上一条间隔超过 5 分钟时展示时间分隔 */
function showTimeDivider(index) {
  if (index <= 0) return true
  const curr = messages.value[index]
  const prev = messages.value[index - 1]
  if (!curr || !curr.time) return false
  const currTs = new Date(curr.time).getTime()
  if (Number.isNaN(currTs)) return false
  const prevTs = prev && prev.time ? new Date(prev.time).getTime() : 0
  return !prevTs || Number.isNaN(prevTs) || currTs - prevTs >= TIME_DIVIDER_GAP_MS
}

/**
 * WebSocket 私信事件处理
 * - dm_message：接收方实时上屏，并立即标记已读
 * - dm_recall：双方原地更新为“已撤回”占位
 * - dm_read：对方已读本会话，把自己发送的消息标记为已读
 */
function onSocketEvent(payload) {
  if (!payload) return
  const data = payload.data || {}
  if (!conversationId.value) return
  if (String(data.conversationId) !== String(conversationId.value)) return

  // 已读回执：对方已读本会话，把自己发送的消息全部标记为已读
  if (payload.type === 'dm_read') {
    messages.value.forEach((m) => {
      if (m.isSelf) m.readFlag = true
    })
    return
  }

  // 撤回事件：按 messageId 原地更新，不清空其他消息
  if (payload.type === 'dm_recall') {
    const target = messages.value.find((m) => String(m.id) === String(data.messageId))
    if (target) {
      target.recalled = true
      target.content = ''
    }
    messageApi
      .unreadCount()
      .then((count) => syncMessageTabBadge(count))
      .catch(() => {})
    return
  }

  // 多端操作同步：同账号其他端删除消息后，当前页面同步移除
  if (payload.type === 'dm_conversation_changed') {
    if (data.action === 'messages_deleted' && Array.isArray(data.messageIds)) {
      const idSet = new Set(data.messageIds.map(String))
      messages.value = messages.value.filter((m) => !idSet.has(String(m.id)))
    }
    // 群成员变更：刷新群成员/群主信息
    if ((data.action === 'members_changed' || data.action === 'created') && isGroup.value) {
      loadGroupInfo()
    }
    // 任意变更都刷新未读角标（已读/删除会话等都会影响总数）
    messageApi
      .unreadCount()
      .then((count) => syncMessageTabBadge(count))
      .catch(() => {})
    return
  }

  if (payload.type !== 'dm_message') return
  const msg = data.message
  if (!msg || !msg.id) return
  const me = String(useUserStore().userId || '')
  if (String(msg.senderId) === me) return
  const msgId = String(msg.id)
  if (messages.value.some((m) => String(m.id) === msgId)) return

  messages.value.push({
    id: msgId,
    isSelf: false,
    type: messageTypeOf(msg.contentType),
    content: msg.content,
    time: msg.createTime,
    recalled: msg.recalled === 1 || msg.recalled === true,
    readFlag: Number(msg.readFlag) === 1,
    quoteMessageId: msg.quoteMessageId ? String(msg.quoteMessageId) : '',
    quoteContent: msg.quoteContent || '',
    mediaDuration: Number(msg.mediaDuration || 0),
    mediaName: msg.mediaName || '',
    mediaSize: Number(msg.mediaSize || 0),
    senderId: String(msg.senderId || ''),
    // 群聊实时消息：发送者资料来自 WS data，避免每条消息再查用户
    senderNickname: data.senderNickname || '',
    senderAvatarUrl: data.senderAvatar ? toAbsoluteImageUrl(data.senderAvatar) : '',
    senderLetter: String(data.senderNickname || 'U')
      .charAt(0)
      .toUpperCase(),
    mentionSegments: buildMentionSegments(msg.content),
  })
  nextTick(() => {
    scrollToId.value = `msg-${messages.value.length - 1}`
  })

  messageApi
    .markRead(conversationId.value)
    .then(() => messageApi.unreadCount())
    .then((count) => syncMessageTabBadge(count))
    .catch(() => {})
}

/** 订阅私信实时推送（单例连接，多页面共享） */
function subscribeSocket() {
  connectDmSocket()
  if (unsubDmSocket) return
  unsubDmSocket = onDmSocketEvent(onSocketEvent)
}

onUnmounted(() => {
  if (unsubDmSocket) {
    unsubDmSocket()
    unsubDmSocket = null
  }
  unbindVisualViewport()
  // 释放录音/播放资源，避免退出页面后麦克风或音频仍在占用
  cancelRecording()
  if (audioCtx) {
    try {
      audioCtx.stop()
      audioCtx.destroy()
    } catch (e) {
      // 忽略销毁异常
    }
    audioCtx = null
  }
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
  }
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})

function toggleProfile() {
  profileCollapsed.value = !profileCollapsed.value
}

/** 查看主页 / 群成员：群聊打开成员面板，单聊跳转社区主页 */
function viewProfile() {
  if (isGroup.value) {
    openGroupPanel()
    return
  }
  const id = targetUser.value.id
  if (!id) return
  const name = targetUser.value.name || ''
  uni.navigateTo({
    url: `/pages/community/profile?id=${id}&name=${encodeURIComponent(name)}`,
  })
}

function goBack() {
  uni.navigateBack()
}

/** 滚动到最新一条消息（scroll-into-view 同值不会重新触发，需先清空再设置） */
function scrollToBottom() {
  if (!messages.value.length) return
  scrollToId.value = ''
  nextTick(() => {
    scrollToId.value = `msg-${messages.value.length - 1}`
  })
}

/** 输入框聚焦（软键盘弹出）后滚到最新消息，避免键盘遮挡刚输入的内容 */
function onInputFocus() {
  // 键盘弹出与可视区收缩有动画，延迟滚动保证滚动发生在高度变化之后
  setTimeout(scrollToBottom, 260)
}

/**
 * H5 键盘适配：iOS Safari 键盘弹出时 window.innerHeight 不变，
 * 只有 visualViewport 高度/偏移变化，需要把页面高度对齐到可视区域，
 * 否则底部输入栏会被软键盘遮住。地址栏收起等小幅变化（<120px）不处理。
 */
function syncVisualViewport() {
  if (typeof window === 'undefined' || !window.visualViewport) return
  const vv = window.visualViewport
  const keyboardGap = window.innerHeight - vv.height
  if (keyboardGap > 120) {
    pageStyle.value = {
      height: `${Math.round(vv.height)}px`,
      transform: `translateY(${Math.round(vv.offsetTop)}px)`,
    }
  } else {
    pageStyle.value = {}
  }
}

/** 绑定 visualViewport 监听（APP 端由 pages.json 的 adjustResize 处理，这里运行时兜底） */
function bindVisualViewport() {
  if (typeof window === 'undefined' || !window.visualViewport) return
  if (visualViewportHandler) return
  visualViewportHandler = syncVisualViewport
  window.visualViewport.addEventListener('resize', visualViewportHandler)
  window.visualViewport.addEventListener('scroll', visualViewportHandler)
  syncVisualViewport()
}

/** 解绑 visualViewport 监听并恢复默认高度 */
function unbindVisualViewport() {
  if (typeof window === 'undefined' || !window.visualViewport || !visualViewportHandler) return
  window.visualViewport.removeEventListener('resize', visualViewportHandler)
  window.visualViewport.removeEventListener('scroll', visualViewportHandler)
  visualViewportHandler = null
  pageStyle.value = {}
}

/** 会话更多菜单：群聊展示群成员/退群，单聊展示主页/多选/举报/屏蔽 */
function showMoreOptions() {
  if (isGroup.value) {
    const actions = [{ label: t('dmChat.groupMembers'), run: openGroupPanel }]
    if (isGroupOwner.value) {
      actions.push({ label: t('dmChat.renameGroup'), run: promptRenameGroup })
      actions.push({ label: t('dmChat.editAnnouncement'), run: promptEditAnnouncement })
      actions.push({ label: t('dmChat.changeGroupAvatar'), run: changeGroupAvatar })
    }
    actions.push({ label: t('dmChat.leaveGroup'), run: confirmLeaveGroup })
    uni.showActionSheet({
      itemList: actions.map((action) => action.label),
      success: (res) => {
        const action = actions[res.tapIndex]
        if (action) action.run()
      },
    })
    return
  }
  uni.showActionSheet({
    itemList: [
      t('dm.viewProfile'),
      t('dmChat.selectMessages'),
      t('dmChat.reportUser'),
      t('dm.blockUser'),
    ],
    success: (res) => {
      if (res.tapIndex === 0) viewProfile()
      else if (res.tapIndex === 1) enterSelectMode()
      else if (res.tapIndex === 2) showReport(null)
      else if (res.tapIndex === 3) confirmBlockUser()
    },
  })
}

/** 拉黑前二次确认 */
function confirmBlockUser() {
  const id = targetUser.value.id
  if (!id) return
  uni.showModal({
    title: t('dm.blockConfirmTitle'),
    content: t('dm.blockConfirmContent'),
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: async (res) => {
      if (!res.confirm) return
      try {
        await blockApi.blockUser(id)
        uni.showToast({ title: t('dm.blockedToast'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      }
    },
  })
}

/** 加载群详情（群名/群主）并刷新成员列表 */
async function loadGroupInfo() {
  if (!conversationId.value || !isGroup.value) return
  try {
    const detail = await messageApi.groupDetail(conversationId.value)
    groupOwnerId.value = detail && detail.ownerId ? String(detail.ownerId) : ''
    if (detail && detail.name) targetUser.value.name = detail.name
    // 群公告/群头像
    groupAnnouncement.value = detail && detail.announcement ? detail.announcement : ''
    targetUser.value.avatarUrl = detail && detail.avatar ? toAbsoluteImageUrl(detail.avatar) : ''
    await loadGroupMembers()
  } catch (e) {
    console.warn('[dm-chat] load group info failed', e)
  }
}

/** 拉取群成员（含头像/角色），用于成员面板与消息头像补全 */
async function loadGroupMembers() {
  if (!conversationId.value || !isGroup.value) return
  try {
    const list = await messageApi.groupMembers(conversationId.value)
    groupMembers.value = (list || []).map((member) => ({
      userId: String(member.userId),
      nickname: member.nickname || '',
      avatarUrl: member.avatar ? toAbsoluteImageUrl(member.avatar) : '',
      letter: String(member.nickname || 'U')
        .charAt(0)
        .toUpperCase(),
      owner: !!member.owner,
      isSelf: !!member.isSelf,
    }))
    // 成员变化后重算 @高亮，保证昵称匹配最新成员
    messages.value.forEach((msg) => {
      if (msg.type === 'text') msg.mentionSegments = buildMentionSegments(msg.content)
    })
  } catch (e) {
    groupMembers.value = []
  }
}

/** 打开群成员面板 */
function openGroupPanel() {
  if (!isGroup.value) return
  groupPanelVisible.value = true
  loadGroupInfo()
}

function closeGroupPanel() {
  groupPanelVisible.value = false
}

/** 群主修改群名称 */
function promptRenameGroup() {
  if (!isGroupOwner.value || groupBusy.value) return
  uni.showModal({
    title: t('dmChat.renameGroupTitle'),
    editable: true,
    placeholderText: t('dmChat.renameGroupPlaceholder'),
    content: targetUser.value.name || '',
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: async (res) => {
      if (!res.confirm) return
      const name = String(res.content || '').trim()
      if (!name) {
        uni.showToast({ title: t('dmChat.renameGroupEmpty'), icon: 'none' })
        return
      }
      groupBusy.value = true
      try {
        await messageApi.renameGroup(conversationId.value, name)
        targetUser.value.name = name
        targetUser.value.avatar = name.charAt(0).toUpperCase()
        uni.showToast({ title: t('dmChat.renameGroupSuccess'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      } finally {
        groupBusy.value = false
      }
    },
  })
}

/** 群主修改群公告（取消/清空则删除公告） */
function promptEditAnnouncement() {
  if (!isGroupOwner.value || groupBusy.value) return
  uni.showModal({
    title: t('dmChat.editAnnouncementTitle'),
    editable: true,
    placeholderText: t('dmChat.announcementPlaceholder'),
    content: groupAnnouncement.value || '',
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: async (res) => {
      if (!res.confirm) return
      const text = String(res.content || '').trim()
      groupBusy.value = true
      try {
        await messageApi.updateGroup(conversationId.value, { announcement: text })
        groupAnnouncement.value = text
        uni.showToast({ title: t('dmChat.announcementSaved'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      } finally {
        groupBusy.value = false
      }
    },
  })
}

/** 群主更换群头像：选图 → 上传 → 更新群设置 */
function changeGroupAvatar() {
  if (!isGroupOwner.value || groupBusy.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const path = (res && res.tempFilePaths && res.tempFilePaths[0]) || ''
      if (!path) return
      groupBusy.value = true
      uni.showLoading({ title: t('dmChat.uploading'), mask: true })
      try {
        const uploaded = await uploadChatImage(path)
        if (!uploaded || !uploaded.url) throw new Error(t('dmChat.uploadFailed'))
        await messageApi.updateGroup(conversationId.value, { avatar: uploaded.url })
        targetUser.value.avatarUrl = toAbsoluteImageUrl(uploaded.url)
        uni.showToast({ title: t('dmChat.groupAvatarUpdated'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('dmChat.groupAvatarFailed'), icon: 'none' })
      } finally {
        uni.hideLoading()
        groupBusy.value = false
      }
    },
  })
}

/** 点击群公告：弹窗查看完整内容 */
function showGroupAnnouncement() {
  if (!groupAnnouncement.value) return
  uni.showModal({
    title: t('dmChat.groupAnnouncement'),
    content: groupAnnouncement.value,
    showCancel: false,
    confirmText: t('common.confirm'),
  })
}

/** @成员：选择群成员后把「@昵称」插入输入框 */
function showMentionPicker() {
  if (!isGroup.value) return
  const candidates = groupMembers.value.filter((member) => !member.isSelf && member.nickname)
  if (!candidates.length) {
    uni.showToast({ title: t('dmChat.mentionEmpty'), icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: candidates.map((member) => member.nickname),
    success: (res) => {
      const member = candidates[Number(res.tapIndex)]
      if (!member) return
      const prefix = inputText.value && /\S$/.test(inputText.value) ? ' ' : ''
      inputText.value = `${inputText.value}${prefix}@${member.nickname} `
    },
  })
}

/** 群主移除成员 */
function confirmRemoveMember(member) {
  if (!member || groupBusy.value) return
  uni.showModal({
    title: t('dmChat.removeMemberTitle'),
    content: t('dmChat.removeMemberContent', { name: member.nickname }),
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: async (res) => {
      if (!res.confirm) return
      groupBusy.value = true
      try {
        await messageApi.removeGroupMember(conversationId.value, member.userId)
        await loadGroupMembers()
        uni.showToast({ title: t('dmChat.removeMemberDone'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      } finally {
        groupBusy.value = false
      }
    },
  })
}

/** 退出群聊（群主退出会自动转让群主） */
function confirmLeaveGroup() {
  if (!isGroup.value || groupBusy.value) return
  uni.showModal({
    title: t('dmChat.leaveGroupTitle'),
    content: t('dmChat.leaveGroupContent'),
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: async (res) => {
      if (!res.confirm) return
      groupBusy.value = true
      try {
        const myId = useUserStore().userId
        if (!myId) return
        await messageApi.removeGroupMember(conversationId.value, myId)
        groupPanelVisible.value = false
        uni.navigateBack()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      } finally {
        groupBusy.value = false
      }
    },
  })
}

/** 打开邀请面板：从已有单聊联系人中排除已在群内的成员 */
async function openInvitePicker() {
  if (!isGroup.value) return
  inviteCandidates.value = []
  inviteVisible.value = true
  try {
    const page = await messageApi.listConversations({ page: 1, size: 50 })
    const memberIds = new Set(groupMembers.value.map((m) => m.userId))
    const me = String(useUserStore().userId || '')
    const seen = new Set()
    inviteCandidates.value = ((page && page.records) || [])
      .filter((conv) => (conv.type || 'SINGLE') === 'SINGLE')
      .map((conv) => ({
        userId: String(conv.targetUserId || ''),
        nickname: conv.nickname || '',
      }))
      .filter((user) => {
        if (!user.userId || user.userId === me) return false
        if (memberIds.has(user.userId) || seen.has(user.userId)) return false
        seen.add(user.userId)
        return true
      })
  } catch (e) {
    inviteCandidates.value = []
  }
}

function closeInvitePicker() {
  inviteVisible.value = false
}

/** 邀请单个成员入群 */
async function inviteMember(candidate) {
  if (!candidate || groupBusy.value) return
  groupBusy.value = true
  try {
    await messageApi.addGroupMembers(conversationId.value, [candidate.userId])
    closeInvitePicker()
    await loadGroupInfo()
    uni.showToast({ title: t('dmChat.inviteSuccess'), icon: 'none' })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
  } finally {
    groupBusy.value = false
  }
}

/** 生成本地待发送文本消息：先上屏，失败后保留并支持重试 */
function createLocalMessage(text, quote) {
  localMessageSeq += 1
  return {
    id: `local-${localMessageSeq}-${Date.now()}`,
    isSelf: true,
    type: 'text',
    content: text,
    time: new Date().toISOString(),
    recalled: false,
    readFlag: false,
    status: 'sending',
    quoteMessageId: quote ? String(quote.id) : '',
    quoteContent: quote ? quote.content : '',
    mediaDuration: 0,
    mediaName: '',
    mediaSize: 0,
    mentionSegments: buildMentionSegments(text),
  }
}

/**
 * 生成本地待发送媒体消息（图片/语音/文件）。
 * 本地路径先上屏（图片可直接预览），上传成功后 content 替换为服务端 URL；
 * 失败时保留本地内容并标记 failed，点击可重试（重试会重新上传）。
 */
function createLocalMediaMessage({
  type,
  content = '',
  localPath = '',
  localFileName = '',
  mediaDuration = 0,
  mediaName = '',
  mediaSize = 0,
  quote = null,
}) {
  localMessageSeq += 1
  return {
    id: `local-${localMessageSeq}-${Date.now()}`,
    isSelf: true,
    type,
    content,
    localPath,
    localFileName,
    time: new Date().toISOString(),
    recalled: false,
    readFlag: false,
    status: 'sending',
    quoteMessageId: quote ? String(quote.id) : '',
    quoteContent: quote ? quote.content : '',
    mediaDuration,
    mediaName,
    mediaSize,
  }
}

/** 是否已经是服务端可访问的媒体地址（否则视为本地临时路径，需要先上传） */
function isRemoteMedia(url) {
  const text = String(url || '')
  return text.startsWith('/uploads/') || text.startsWith('http://') || text.startsWith('https://')
}

/** 媒体消息上传：按类型调用对应上传接口，返回服务端 URL */
async function uploadMediaFile(target) {
  if (!target.localPath) return ''
  if (target.type === 'image') {
    const uploaded = await uploadChatImage(target.localPath)
    return (uploaded && uploaded.url) || ''
  }
  if (target.type === 'voice') {
    const uploaded = await uploadVoice(target.localPath, target.localFileName || 'voice.webm')
    return (uploaded && uploaded.url) || ''
  }
  if (target.type === 'file') {
    const uploaded = await uploadFile(target.localPath, target.mediaName)
    // 以服务端返回的真实大小为准（客户端拿到的 size 可能为 0）
    if (uploaded && uploaded.size) target.mediaSize = Number(uploaded.size)
    return (uploaded && uploaded.url) || ''
  }
  return ''
}

/** 发送一条图片/语音/文件消息：先上传再发消息，失败标记 failed 保留可重试 */
async function processMediaMessage(msg) {
  if (!conversationId.value) return t('messagesCenter.loadFailed')
  // 从响应式数组取代理对象，保证状态变更能触发视图更新
  const target = messages.value.find((m) => m.id === msg.id) || msg
  target.status = 'sending'
  target.error = ''
  try {
    let url = target.content
    if (!isRemoteMedia(url)) {
      url = await uploadMediaFile(target)
    }
    if (!url) throw new Error(t('dmChat.uploadFailed'))
    target.content = url

    const media = {}
    if (target.type === 'voice') media.mediaDuration = target.mediaDuration
    if (target.type === 'file') {
      media.mediaName = target.mediaName
      media.mediaSize = target.mediaSize
    }
    const saved = await messageApi.sendMessage(
      conversationId.value,
      url,
      MEDIA_CONTENT_TYPE[target.type] || 'TEXT',
      target.quoteMessageId || null,
      media,
    )
    target.id = String(saved.id)
    target.time = saved.createTime
    target.status = 'sent'
    return ''
  } catch (e) {
    const error = (e && e.message) || t('dmChat.sendFailed')
    target.status = 'failed'
    target.error = error
    return error
  }
}

/** 发送一条本地文本消息：成功替换为服务端消息，失败标记为 failed；返回空串表示成功 */
async function pushMessage(msg) {
  // 关键：从响应式数组里取代理对象再修改，直接改原始对象不会触发视图更新
  const target = messages.value.find((m) => m.id === msg.id) || msg
  target.status = 'sending'
  try {
    const saved = await messageApi.sendMessage(
      conversationId.value,
      target.content,
      'TEXT',
      target.quoteMessageId || null,
    )
    target.id = String(saved.id)
    target.time = saved.createTime
    target.status = 'sent'
    return ''
  } catch (e) {
    const error = (e && e.message) || t('dmChat.sendFailed')
    target.status = 'failed'
    target.error = error
    return error
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  if (!conversationId.value) {
    uni.showToast({ title: t('messagesCenter.loadFailed'), icon: 'none' })
    return
  }
  sending.value = true
  const quote = quotedMessage.value
  const local = createLocalMessage(text, quote)
  messages.value.push(local)
  inputText.value = ''
  quotedMessage.value = null
  clearDraft()
  scrollToBottom()
  const error = await pushMessage(local)
  if (error) uni.showToast({ title: error, icon: 'none' })
  sending.value = false
}

/** 发送失败的消息点击重试：文本直接重发，媒体消息重新上传后发送 */
async function retryMessage(msg) {
  if (!msg || msg.status !== 'failed' || sending.value || !conversationId.value) return
  sending.value = true
  const error = msg.type === 'text' ? await pushMessage(msg) : await processMediaMessage(msg)
  if (error) uni.showToast({ title: error, icon: 'none' })
  else scrollToBottom()
  sending.value = false
}

/** 进入多选删除模式 */
function enterSelectMode() {
  if (!messages.value.length) return
  selectMode.value = true
  selectedIds.value = []
}

/** 退出多选模式并清空选择 */
function exitSelectMode() {
  selectMode.value = false
  selectedIds.value = []
}

/** 消息是否被勾选 */
function isSelected(msg) {
  return !!msg && selectedIds.value.includes(msg.id)
}

/** 勾选/取消勾选一条消息 */
function toggleSelect(msg) {
  if (!msg) return
  const index = selectedIds.value.indexOf(msg.id)
  if (index >= 0) selectedIds.value.splice(index, 1)
  else selectedIds.value.push(msg.id)
}

/** 消息点击：多选模式下切换勾选，其余场景不处理 */
function onMessageTap(msg) {
  if (selectMode.value) toggleSelect(msg)
}

/** 批量删除（仅自己隐藏）：只提交已落库的消息，本地失败消息直接从视图移除 */
async function confirmDeleteSelected() {
  if (!selectedIds.value.length) return
  const ids = selectedIds.value.slice()
  uni.showModal({
    title: t('dmChat.deleteMessagesTitle'),
    content: t('dmChat.deleteMessagesContent', { count: ids.length }),
    confirmText: t('common.confirm'),
    cancelText: t('common.cancel'),
    success: async (res) => {
      if (!res.confirm) return
      const deliveredIds = ids.filter((id) => {
        const m = messages.value.find((x) => x.id === id)
        return m && (!m.status || m.status === 'sent')
      })
      try {
        if (deliveredIds.length && conversationId.value) {
          await messageApi.deleteMessages(conversationId.value, deliveredIds)
        }
        const idSet = new Set(ids)
        messages.value = messages.value.filter((m) => !idSet.has(m.id))
        exitSelectMode()
        uni.showToast({ title: t('dmChat.deleteMessagesDone'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      }
    },
  })
}

/** 引用摘要：媒体消息用占位文案，文本消息取原文 */
function quotePreviewOf(msg) {
  if (msg.type === 'image') return t('messagesCenter.imageMessage')
  if (msg.type === 'voice') return t('messagesCenter.voiceMessage')
  if (msg.type === 'file') return msg.mediaName || t('messagesCenter.fileMessage')
  return msg.content
}

/** 引用某条消息：输入栏上方展示引用预览，发送时携带 quoteMessageId */
function quoteMessage(msg) {
  if (!msg || msg.recalled) return
  quotedMessage.value = {
    id: String(msg.id),
    content: quotePreviewOf(msg),
  }
}

/** 点击引用块：定位到被引用的消息（不在已加载列表时提示） */
function jumpToQuoted(msg) {
  const id = msg && msg.quoteMessageId
  if (!id) return
  const index = messages.value.findIndex((m) => String(m.id) === String(id))
  if (index < 0) {
    uni.showToast({ title: t('dmChat.quoteExpired'), icon: 'none' })
    return
  }
  scrollToId.value = ''
  nextTick(() => {
    scrollToId.value = `msg-${index}`
  })
}

/** 打开转发面板：单条消息 */
function openForward(msg) {
  if (!msg || msg.recalled) return
  forwardMessages.value = [msg]
  showForwardPanel()
}

/** 打开转发面板：多选模式下批量转发 */
function openForwardSelected() {
  if (!selectedIds.value.length) return
  const selected = messages.value.filter((m) => selectedIds.value.includes(m.id) && !m.recalled)
  if (!selected.length) return
  forwardMessages.value = selected
  showForwardPanel()
}

/** 展示转发面板并拉取可转发会话（过滤自己，避免自己给自己发） */
async function showForwardPanel() {
  forwardVisible.value = true
  forwardConversations.value = []
  try {
    const page = await messageApi.listConversations({ page: 1, size: 50 })
    const me = String(useUserStore().userId || '')
    forwardConversations.value = ((page && page.records) || [])
      .map((conv) => ({
        id: String(conv.id),
        type: conv.type || 'SINGLE',
        targetUserId: String(conv.targetUserId || ''),
        nickname: conv.nickname || '',
      }))
      // 群聊可直接转发；单聊过滤掉目标用户为当前用户的会话（禁止转发给自己）
      .filter((conv) => conv.type === 'GROUP' || (conv.targetUserId && conv.targetUserId !== me))
  } catch (e) {
    forwardConversations.value = []
  }
}

/** 关闭转发面板 */
function closeForward() {
  forwardVisible.value = false
  forwardMessages.value = []
}

/** 转发到指定会话：按消息类型重新发送（文本/图片/语音/文件）；支持转发到群聊 */
async function doForward(conv) {
  const list = forwardMessages.value || []
  if (!list.length || forwarding.value || !conv || !conv.id) return
  const me = String(useUserStore().userId || '')
  const toGroup = conv.type === 'GROUP'
  // 单聊禁止转发给自己：防御性校验，避免目标数据异常时把消息发给自己
  if (!toGroup && conv.targetUserId === me) {
    uni.showToast({ title: t('dmChat.forwardToSelf'), icon: 'none' })
    return
  }
  forwarding.value = true
  let sent = 0
  let skipped = 0
  try {
    // 群聊已经是成员，直接复用会话ID；单聊先幂等创建会话
    const targetConversationId = toGroup
      ? conv.id
      : String((await messageApi.ensureConversation(conv.targetUserId)).id)
    for (const msg of list) {
      // 本地未落库/媒体尚未上传成功的消息无法转发，跳过
      if (
        String(msg.id).startsWith('local-') ||
        (msg.type !== 'text' && !isRemoteMedia(msg.content))
      ) {
        skipped += 1
        continue
      }
      try {
        const media =
          msg.type === 'voice'
            ? { mediaDuration: msg.mediaDuration }
            : msg.type === 'file'
              ? { mediaName: msg.mediaName, mediaSize: msg.mediaSize }
              : null
        await messageApi.sendMessage(
          targetConversationId,
          msg.content,
          MEDIA_CONTENT_TYPE[msg.type] || 'TEXT',
          null,
          media,
        )
        sent += 1
      } catch (e) {
        skipped += 1
      }
    }
    closeForward()
    if (selectMode.value) exitSelectMode()
    if (sent === 0) {
      uni.showToast({ title: t('dmChat.forwardNothing'), icon: 'none' })
    } else if (skipped > 0) {
      uni.showToast({ title: t('dmChat.forwardSkipped', { count: skipped }), icon: 'none' })
    } else {
      uni.showToast({ title: t('dmChat.forwarded'), icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
  } finally {
    forwarding.value = false
  }
}

/** 举报原因选项（本地化） */
const reportReasons = computed(() => [
  t('dmChat.reportReasonHarass'),
  t('dmChat.reportReasonSpam'),
  t('dmChat.reportReasonPorn'),
  t('dmChat.reportReasonFraud'),
  t('dmChat.reportReasonOther'),
])

/** 举报当前用户 / 指定消息：群聊按消息发送者，单聊按会话对方 */
function showReport(msg) {
  // 群聊举报消息时以发送者为被举报人；举报整个用户仅单聊可用
  const targetUserId =
    isGroup.value && msg && msg.senderId && !msg.isSelf ? String(msg.senderId) : targetUser.value.id
  if (!targetUserId) return
  uni.showActionSheet({
    itemList: reportReasons.value,
    success: async (res) => {
      const reason = reportReasons.value[Number(res.tapIndex)]
      const messageId = msg && !String(msg.id).startsWith('local-') ? msg.id : null
      try {
        await messageApi.report({ targetUserId, messageId, reason })
        uni.showToast({ title: t('dmChat.reportSubmitted'), icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || t('common.actionFailed'), icon: 'none' })
      }
    },
  })
}

/** 输入变化：防抖保存本地草稿（退出页面不丢失） */
function saveDraft() {
  const key = draftKey.value
  if (!key) return
  if (draftTimer) clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    if (!inputText.value.trim()) removeStorage(key)
    else setStorage(key, inputText.value)
  }, 300)
}

/** 清除草稿（发送成功或主动清空输入时） */
function clearDraft() {
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
  }
  const key = draftKey.value
  if (key) removeStorage(key)
}

/** 打开会话内搜索面板 */
function openSearch() {
  if (!conversationId.value) return
  searchVisible.value = true
  searchKeyword.value = ''
  searchResults.value = []
}

/** 关闭搜索面板并清理计时器 */
function closeSearch() {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  searchVisible.value = false
  searching.value = false
}

/** 搜索输入：500ms 防抖，避免每敲一个字都打后端 */
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchResults.value = []
    searching.value = false
    return
  }
  searching.value = true
  searchTimer = setTimeout(() => runSearch(kw), 500)
}

/** 调用后端会话内搜索接口 */
async function runSearch(keyword) {
  if (!conversationId.value) return
  try {
    const page = await messageApi.searchMessages(conversationId.value, keyword, {
      page: 1,
      size: 20,
    })
    if (keyword !== searchKeyword.value.trim()) return
    const me = String(useUserStore().userId || '')
    searchResults.value = ((page && page.records) || []).map((m) => ({
      id: String(m.id),
      content: m.content,
      time: m.createTime,
      isSelf: String(m.senderId) === me,
    }))
  } catch (e) {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

/** 点击搜索结果：定位到当前已加载列表中的对应消息 */
function jumpToSearchResult(item) {
  const index = messages.value.findIndex((m) => String(m.id) === String(item.id))
  closeSearch()
  if (index < 0) {
    uni.showToast({ title: t('dmChat.searchOlderHint'), icon: 'none' })
    return
  }
  scrollToId.value = ''
  nextTick(() => {
    scrollToId.value = `msg-${index}`
  })
}

/** 批量发送图片：本地临时图逐个上屏（可直接预览），再限流上传发送，失败项保留可重试 */
function sendImageMessages(filePaths) {
  if (!conversationId.value || !filePaths || !filePaths.length) return
  const locals = filePaths.map((path) => {
    const local = createLocalMediaMessage({ type: 'image', content: path, localPath: path })
    messages.value.push(local)
    return local
  })
  scrollToBottom()
  runMediaQueue(locals)
}

/** 媒体消息发送队列：最多 3 个并发，失败消息保留在列表可点击重试 */
async function runMediaQueue(locals) {
  const queue = locals.slice()
  let failed = 0
  let lastError = ''
  const worker = async () => {
    while (queue.length) {
      const msg = queue.shift()
      const error = await processMediaMessage(msg)
      if (error) {
        failed += 1
        lastError = error
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(3, locals.length) }, () => worker()))
  if (failed > 0) {
    uni.showToast({
      title:
        failed === locals.length && lastError
          ? lastError
          : t('dmChat.imagePartialFailed', { failed, total: locals.length }),
      icon: 'none',
    })
  }
}

/** 添加附件：弹出图片/文件选择 */
function addAttachment() {
  if (!conversationId.value) return
  uni.showActionSheet({
    itemList: [t('dmChat.attachImage'), t('dmChat.attachFile')],
    success: (res) => {
      if (res.tapIndex === 0) pickImages()
      else if (res.tapIndex === 1) pickFile()
    },
  })
}

/** 选择图片（最多 9 张） */
function pickImages() {
  uni.chooseImage({
    count: 9,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const paths = (res && res.tempFilePaths) || []
      if (paths.length) sendImageMessages(paths)
    },
  })
}

/** 选择文件（H5/APP 走 chooseFile，小程序端暂不支持） */
function pickFile() {
  // #ifdef H5 || APP-PLUS
  uni.chooseFile({
    count: 1,
    extension: [
      '.pdf',
      '.doc',
      '.docx',
      '.xls',
      '.xlsx',
      '.ppt',
      '.pptx',
      '.txt',
      '.md',
      '.csv',
      '.zip',
      '.rar',
      '.7z',
    ],
    success: (res) => {
      const file = res && res.tempFiles && res.tempFiles[0]
      if (!file) return
      const name = file.name || 'file'
      const size = Number(file.size || 0)
      if (size > MAX_FILE_SIZE) {
        uni.showToast({
          title: t('dmChat.fileTooLarge', { size: MAX_FILE_SIZE / 1024 / 1024 }),
          icon: 'none',
        })
        return
      }
      // H5 下 path 可能是 Blob/blob URL；APP 下是本地临时路径
      const source =
        typeof Blob !== 'undefined' && file.path instanceof Blob ? file.path : file.path || file
      sendFileMessage(source, name, size)
    },
  })
  // #endif
  // #ifdef MP
  uni.showToast({ title: t('dmChat.filePickerUnsupported'), icon: 'none' })
  // #endif
}

/** 发送文件消息：本地上屏 → 上传 → 发送 */
function sendFileMessage(source, name, size) {
  if (!conversationId.value) return
  const local = createLocalMediaMessage({
    type: 'file',
    localPath: source,
    mediaName: name,
    mediaSize: size,
    quote: quotedMessage.value,
  })
  quotedMessage.value = null
  messages.value.push(local)
  scrollToBottom()
  processMediaMessage(local).then((error) => {
    if (error) uni.showToast({ title: error, icon: 'none' })
  })
}

/** 开始录音：按住麦克风触发，松开发送 */
function startRecording() {
  if (recording.value || !conversationId.value) return
  recordCancelled = false
  recordSeconds.value = 0
  // #ifdef H5
  startH5Recording()
  // #endif
  // #ifndef H5
  startAppRecording()
  // #endif
}

/** 录音计时：每秒刷新提示，到上限自动停止发送 */
function startRecordTimer() {
  if (recordTimer) clearInterval(recordTimer)
  recordTimer = setInterval(() => {
    recordSeconds.value = Math.min(
      RECORD_LIMIT_SECONDS,
      Math.floor((Date.now() - recordStartAt) / 1000),
    )
    if (recordSeconds.value >= RECORD_LIMIT_SECONDS) stopRecording()
  }, 500)
}

function stopRecordTimer() {
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
}

/** APP 端录音：使用 RecorderManager（mp3） */
function startAppRecording() {
  // #ifndef H5
  const recorder = uni.getRecorderManager()
  if (!appRecorder) {
    appRecorder = recorder
    recorder.onStop((res) => {
      recording.value = false
      stopRecordTimer()
      if (recordCancelled) return
      const ms = Number(res.duration || Date.now() - recordStartAt)
      finishVoiceRecord(res.tempFilePath, Math.round(ms / 1000), 'mp3', Date.now() - recordStartAt)
    })
    recorder.onError(() => {
      recording.value = false
      stopRecordTimer()
      uni.showToast({ title: t('dmChat.recordFailed'), icon: 'none' })
    })
  }
  recordStartAt = Date.now()
  recorder.start({
    format: 'mp3',
    duration: RECORD_LIMIT_SECONDS * 1000,
    sampleRate: 44100,
    encodeBitRate: 96000,
    numberOfChannels: 1,
  })
  recording.value = true
  startRecordTimer()
  // #endif
}

/** H5 端录音：使用 MediaRecorder（webm/ogg） */
function startH5Recording() {
  // #ifdef H5
  if (typeof navigator === 'undefined' || !navigator.mediaDevices) {
    uni.showToast({ title: t('dmChat.recordPermission'), icon: 'none' })
    return
  }
  navigator.mediaDevices
    .getUserMedia({ audio: true })
    .then((stream) => {
      h5Stream = stream
      h5Chunks = []
      const useWebm =
        typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported('audio/webm')
      h5Recorder = new MediaRecorder(stream, useWebm ? { mimeType: 'audio/webm' } : undefined)
      h5Recorder.ondataavailable = (e) => {
        if (e.data && e.data.size) h5Chunks.push(e.data)
      }
      h5Recorder.onstop = () => {
        const ms = Date.now() - recordStartAt
        const type = h5Recorder.mimeType || 'audio/webm'
        const ext = type.indexOf('ogg') >= 0 ? 'ogg' : 'webm'
        const blob = new Blob(h5Chunks, { type })
        h5Chunks = []
        releaseH5Stream()
        if (recordCancelled) return
        finishVoiceRecord(blob, Math.round(ms / 1000), ext, ms)
      }
      recordStartAt = Date.now()
      h5Recorder.start()
      recording.value = true
      startRecordTimer()
    })
    .catch(() => {
      uni.showToast({ title: t('dmChat.recordPermission'), icon: 'none' })
    })
  // #endif
}

/** 释放 H5 录音麦克风流 */
function releaseH5Stream() {
  // #ifdef H5
  if (h5Stream) {
    h5Stream.getTracks().forEach((track) => {
      try {
        track.stop()
      } catch (e) {
        // 忽略释放异常
      }
    })
    h5Stream = null
  }
  // #endif
}

/** 松开手指：停止录音并发送 */
function stopRecording() {
  if (!recording.value) return
  recording.value = false
  stopRecordTimer()
  // #ifdef H5
  if (h5Recorder && h5Recorder.state !== 'inactive') {
    h5Recorder.stop()
    return
  }
  // #endif
  // #ifndef H5
  if (appRecorder) appRecorder.stop()
  // #endif
}

/** 取消录音（触摸中断/退出页面）：丢弃本次录音 */
function cancelRecording() {
  if (!recording.value) {
    releaseH5Stream()
    return
  }
  recordCancelled = true
  recording.value = false
  stopRecordTimer()
  // #ifdef H5
  if (h5Recorder && h5Recorder.state !== 'inactive') {
    try {
      h5Recorder.stop()
    } catch (e) {
      // 忽略重复停止
    }
  }
  releaseH5Stream()
  // #endif
  // #ifndef H5
  if (appRecorder) {
    try {
      appRecorder.stop()
    } catch (e) {
      // 忽略重复停止
    }
  }
  // #endif
}

/** 录音结束：时长不足 1 秒不发送，否则生成语音消息 */
function finishVoiceRecord(source, duration, ext, elapsedMs) {
  if (!source) return
  if ((elapsedMs || 0) < 1000) {
    uni.showToast({ title: t('dmChat.voiceTooShort'), icon: 'none' })
    return
  }
  sendVoiceMessage(source, Math.max(1, Number(duration) || 1), ext)
}

/** 发送语音消息：本地上屏 → 上传 → 发送 */
function sendVoiceMessage(source, duration, ext) {
  if (!conversationId.value) return
  const local = createLocalMediaMessage({
    type: 'voice',
    localPath: source,
    localFileName: `voice-${Date.now()}.${ext || 'mp3'}`,
    mediaDuration: duration,
    quote: quotedMessage.value,
  })
  quotedMessage.value = null
  messages.value.push(local)
  scrollToBottom()
  processMediaMessage(local).then((error) => {
    if (error) uni.showToast({ title: error, icon: 'none' })
  })
}

/** 媒体展示地址：本地临时路径直接用，服务端路径补全域名 */
function displayMediaUrl(msg) {
  const url = msg && msg.content ? msg.content : ''
  if (!url) return ''
  return isRemoteMedia(url) ? toAbsoluteImageUrl(url) : url
}

/** 语音气泡宽度：随时长增长并限制上限 */
function voiceBubbleWidth(msg) {
  const seconds = Math.max(1, Number(msg && msg.mediaDuration) || 1)
  return `${Math.min(420, 150 + seconds * 6)}rpx`
}

/** 当前是否正在播放该语音 */
function isPlayingVoice(msg) {
  return !!msg && playingVoiceId.value === String(msg.id)
}

/** 点击语音：播放/停止 */
function playVoice(msg) {
  if (!msg || msg.recalled) return
  if (!isRemoteMedia(msg.content)) {
    uni.showToast({ title: t('dmChat.sending'), icon: 'none' })
    return
  }
  if (!audioCtx) {
    audioCtx = uni.createInnerAudioContext()
    audioCtx.onEnded(() => {
      playingVoiceId.value = ''
    })
    audioCtx.onStop(() => {
      playingVoiceId.value = ''
    })
    audioCtx.onError(() => {
      playingVoiceId.value = ''
    })
  }
  // 再次点击同一条语音：停止播放
  if (playingVoiceId.value === String(msg.id)) {
    audioCtx.stop()
    playingVoiceId.value = ''
    return
  }
  audioCtx.stop()
  audioCtx.src = toAbsoluteImageUrl(msg.content)
  audioCtx.play()
  playingVoiceId.value = String(msg.id)
}

/** 文件大小展示：B/KB/MB */
function formatFileSize(bytes) {
  const size = Number(bytes || 0)
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/** 点击文件：H5 新窗口打开，APP 下载后用系统组件打开 */
function openFile(msg) {
  if (!msg || msg.recalled) return
  if (!isRemoteMedia(msg.content)) {
    uni.showToast({ title: t('dmChat.sending'), icon: 'none' })
    return
  }
  const abs = toAbsoluteImageUrl(msg.content)
  // #ifdef H5
  window.open(abs, '_blank')
  // #endif
  // #ifndef H5
  uni.showLoading({ title: t('dmChat.downloading'), mask: true })
  uni.downloadFile({
    url: abs,
    success: (res) => {
      uni.hideLoading()
      if (res.statusCode !== 200) {
        uni.showToast({ title: t('dmChat.openFileFailed'), icon: 'none' })
        return
      }
      uni.openDocument({
        filePath: res.tempFilePath,
        showMenu: true,
        fail: () => uni.showToast({ title: t('dmChat.openFileFailed'), icon: 'none' }),
      })
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: t('dmChat.openFileFailed'), icon: 'none' })
    },
  })
  // #endif
}

/** 保存图片到本地：H5 触发浏览器下载，APP 保存到相册 */
function saveImage(msg) {
  if (!msg || msg.recalled) return
  if (!isRemoteMedia(msg.content)) {
    uni.showToast({ title: t('dmChat.sendFailedRetry'), icon: 'none' })
    return
  }
  const abs = toAbsoluteImageUrl(msg.content)
  // #ifdef H5
  fetch(abs)
    .then((resp) => {
      if (!resp.ok) throw new Error('download failed')
      return resp.blob()
    })
    .then((blob) => {
      const ext = (blob.type.split('/')[1] || 'jpg').replace('jpeg', 'jpg')
      const objectUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = `moyuyo-${Date.now()}.${ext}`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(objectUrl)
      uni.showToast({ title: t('dmChat.saveSuccess'), icon: 'none' })
    })
    .catch(() => uni.showToast({ title: t('dmChat.saveFailed'), icon: 'none' }))
  // #endif
  // #ifndef H5
  uni.showLoading({ title: t('dmChat.saving'), mask: true })
  uni.downloadFile({
    url: abs,
    success: (res) => {
      uni.hideLoading()
      if (res.statusCode !== 200) {
        uni.showToast({ title: t('dmChat.saveFailed'), icon: 'none' })
        return
      }
      uni.saveImageToPhotosAlbum({
        filePath: res.tempFilePath,
        success: () => uni.showToast({ title: t('dmChat.saveSuccess'), icon: 'none' }),
        fail: () => uni.showToast({ title: t('dmChat.saveFailed'), icon: 'none' }),
      })
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: t('dmChat.saveFailed'), icon: 'none' })
    },
  })
  // #endif
}

/** 点击图片消息：全屏预览（多选模式下交给消息点击做勾选） */
function previewImage(msg) {
  if (selectMode.value) return
  if (!msg || !msg.content) return
  const abs = displayMediaUrl(msg)
  uni.previewImage({ urls: [abs], current: abs })
}

/** 复制文本消息到剪贴板 */
function copyMessage(msg) {
  if (!msg || !msg.content || msg.type !== 'text') return
  uni.setClipboardData({
    data: msg.content,
    success: () => uni.showToast({ title: t('dmChat.copySuccess'), icon: 'none' }),
  })
}

/**
 * 长按消息：文本可复制；自己发送且 2 分钟内的消息可撤回。
 * 菜单项按实际可用能力动态拼装，避免给用户展示无效操作。
 */
function onLongPressMessage(msg) {
  if (!msg || msg.recalled || selectMode.value) return
  const actions = []
  if (msg.type === 'text' && msg.content) {
    actions.push({ label: t('dmChat.copy'), run: () => copyMessage(msg) })
  }
  // 已发送成功的图片支持保存到本地/相册
  if (msg.type === 'image' && msg.content && (!msg.status || msg.status === 'sent')) {
    actions.push({ label: t('dmChat.saveImage'), run: () => saveImage(msg) })
  }
  actions.push({ label: t('dmChat.quote'), run: () => quoteMessage(msg) })
  actions.push({ label: t('dmChat.forward'), run: () => openForward(msg) })
  actions.push({ label: t('dmChat.report'), run: () => showReport(msg) })
  if (msg.isSelf && (!msg.status || msg.status === 'sent')) {
    const created = msg.time ? new Date(msg.time).getTime() : 0
    const withinWindow = created && Date.now() - created <= RECALL_WINDOW_MS
    if (withinWindow) {
      actions.push({ label: t('dmChat.recall'), run: () => onRecallMessage(msg) })
    } else if (!actions.length) {
      // 图片消息超时撤回时没有其他操作，直接提示，避免弹出空菜单
      uni.showToast({ title: t('dmChat.recallExpired'), icon: 'none' })
      return
    }
  }
  if (!actions.length) return
  uni.showActionSheet({
    itemList: actions.map((a) => a.label),
    success: (res) => {
      const action = actions[Number(res.tapIndex)]
      if (action) action.run()
    },
  })
}

/** 调用撤回接口并原地更新消息 */
async function onRecallMessage(msg) {
  if (!msg || !conversationId.value) return
  try {
    await messageApi.recallMessage(conversationId.value, msg.id)
    msg.recalled = true
    msg.content = ''
    uni.showToast({ title: t('dmChat.recallSuccess'), icon: 'none' })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || t('dmChat.recallFailed'), icon: 'none' })
  }
}

// 幂等启动：onLoad 先于 onMounted 触发，两者都调用，副作用只发生一次
// 不能只读 `getCurrentPages().slice(-1)[0].options` —— APP 运行时（initPageVm）
// 从不写 pageVm.options，APP 真机上恒读到 {}，结果是聊天对象信息丢失。
let booted = false

async function bootstrap(options) {
  if (booted) return
  booted = true
  // 群聊入口：groupId 为会话ID；单聊入口：id 为目标用户ID
  let groupId = ''
  try {
    const hasOptions = !!options && typeof options === 'object' && Object.keys(options).length > 0
    const q = hasOptions ? options : getPageQuery()
    if (q.groupId) {
      groupId = String(q.groupId)
      isGroup.value = true
    } else if (q.id) {
      targetUser.value.id = String(q.id) // 雪花 ID 用 String 承接，避免精度丢失
    }
    if (q.name) targetUser.value.name = q.name
    if (q.avatar) targetUser.value.avatar = q.avatar
    // 未传头像首字母时用昵称首字母兜底，避免会话列表里所有会话都显示默认字母
    if (!q.avatar && targetUser.value.name) {
      targetUser.value.avatar = String(targetUser.value.name).charAt(0).toUpperCase()
    }
  } catch (e) {
    console.warn('[dm-chat] read page query failed', e)
  }

  if (groupId) {
    conversationId.value = groupId
    try {
      await loadGroupInfo()
      await loadMessages()
      subscribeSocket()
    } catch (e) {
      console.warn('[dm-chat] load group chat failed', e)
      uni.showToast({ title: t('messagesCenter.loadFailed'), icon: 'none' })
    }
    return
  }

  if (!targetUser.value.id) {
    uni.showToast({ title: t('messagesCenter.loadFailed'), icon: 'none' })
    return
  }
  // 恢复上次未发送的草稿（按目标用户维度保存，退出页面不丢失）
  if (draftKey.value) {
    const savedDraft = getStorage(draftKey.value, '')
    if (savedDraft) inputText.value = String(savedDraft)
  }
  // 后端幂等创建会话 + 拉取历史消息
  try {
    const conv = await messageApi.ensureConversation(targetUser.value.id)
    conversationId.value = String(conv.id)
    if (conv.nickname) targetUser.value.name = conv.nickname
    // 对方真实头像：优先使用会话接口返回的 avatar URL，空时回落到昵称首字母
    targetUser.value.avatarUrl = conv.avatar ? toAbsoluteImageUrl(conv.avatar) : ''
    if (!conv.avatar && targetUser.value.name) {
      targetUser.value.avatar = String(targetUser.value.name).charAt(0).toUpperCase()
    }
    await loadMessages()
    // 建立/复用心跳连接，订阅新私信实时推送
    subscribeSocket()
  } catch (e) {
    console.warn('[dm-chat] ensure conversation failed', e)
    uni.showToast({ title: t('messagesCenter.loadFailed'), icon: 'none' })
  }
}

onLoad((options) => {
  bootstrap(options)
})

onMounted(() => {
  bootstrap()
  bindVisualViewport()
})
</script>

<style lang="scss" scoped>
.dm-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
  overflow: hidden;
}

/* 导航栏 */
.dm-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  /* 状态栏安全区：取 status-bar-height 与 env 的较大值，避免重复叠加 */
  padding-top: var(--status-bar-height, 0px);
  padding-top: max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px));
  min-height: calc(44px + var(--status-bar-height, 0px));
  min-height: calc(44px + max(var(--status-bar-height, 0px), env(safe-area-inset-top, 0px)));
  background: var(--color-background);
  border-bottom: 1rpx solid var(--color-border);
  box-sizing: border-box;
}
.nav-back,
.nav-action {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.nav-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.nav-title {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-back .luc,
.nav-action .luc {
  font-size: 36rpx;
  color: var(--color-text);
}
.back-text {
  font-size: 28rpx;
  color: var(--color-primary);
}
.nav-more {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.more-icon {
  font-size: 40rpx;
  color: var(--color-text);
}

/* 聊天主体 */
.dm-body {
  flex: 1;
  /* 关键：flex 子项默认 min-height:auto，会被消息内容撑高，
     导致 scroll-view 自身不产生滚动、底部输入栏被挤出视口。
     显式置 0 让 scroll-view 只占用剩余空间，内部滚动生效。 */
  min-height: 0;
  background: var(--color-fill, #f2f2f7);
  padding: 24rpx;
  width: 100%;
  box-sizing: border-box;
}

/* 信息卡片 */
.profile-card {
  margin-bottom: 16rpx;
  border-radius: 24rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
  overflow: hidden;
}

.profile-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
}

.profile-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar-circle {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-letter {
  font-size: 28rpx;
  font-weight: 700;
  color: #ffffff;
}

/* 资料卡真实头像：铺满圆形容器 */
.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.profile-info {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.profile-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.chevron {
  font-size: 28rpx;
  color: var(--color-text-secondary);
  transition: transform 0.3s ease;
  transform: rotate(-90deg);
}

.chevron.rotated {
  transform: rotate(-270deg);
}

.profile-card-body {
  max-height: 200rpx;
  overflow: hidden;
  transition:
    max-height 0.3s ease,
    opacity 0.25s ease;
  opacity: 1;
  padding: 0 24rpx 24rpx;
}

.profile-card-body.collapsed {
  max-height: 0;
  opacity: 0;
  padding: 0 24rpx;
}

.view-profile-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  border-radius: 36rpx;
  background: var(--color-fill, #f2f2f7);
}

.view-profile-text {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text);
}

/* 消息列表 */
.message-list {
  padding-bottom: 32rpx;
}

/* 顶部加载更早消息 */
.load-older {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
}
.load-older-text {
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  background: var(--color-surface);
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 时间分隔条 */
.msg-time-divider {
  display: flex;
  justify-content: center;
  margin: 24rpx 0 12rpx;
}
.msg-time-text {
  padding: 6rpx 20rpx;
  border-radius: 20rpx;
  background: var(--color-fill);
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 已撤回消息：居中灰色系统提示 */
.msg-recalled-row {
  display: flex;
  justify-content: center;
  padding: 8rpx 0 16rpx;
}
.msg-recalled-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 自己最后一条消息的已读回执 */
.msg-read-row {
  display: flex;
  justify-content: flex-end;
  padding: 0 8rpx 8rpx;
}
.msg-read-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 发送中 / 发送失败（失败点击重试） */
.msg-status-row {
  display: flex;
  justify-content: flex-end;
  padding: 0 8rpx 8rpx;
}
.msg-status-text {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.msg-status-text--failed {
  color: #e5484d;
}

/* 多选模式勾选圈 */
.msg-check {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-border);
  background: var(--color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.msg-check--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
}
.msg-check-icon {
  font-size: 24rpx;
  color: #ffffff;
}

/* 引用回复摘要 */
.msg-quote-row {
  max-width: 75%;
  margin: 0 0 8rpx 80rpx;
  padding: 10rpx 16rpx;
  border-left: 4rpx solid var(--color-primary);
  border-radius: 8rpx;
  background: var(--color-fill, #f2f2f7);
  box-sizing: border-box;
}
.msg-quote-row--self {
  margin-left: auto;
  margin-right: 0;
}
.msg-quote-text {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 会话内搜索面板 */
.dm-search-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: rgba(0, 0, 0, 0.28);
  display: flex;
  flex-direction: column;
}
.dm-search-panel {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  padding: 24rpx;
  padding-top: calc(24rpx + env(safe-area-inset-top));
}
.dm-search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 36rpx;
  background: var(--color-background);
}
.dm-search-icon {
  font-size: 32rpx;
  color: var(--color-text-secondary);
}
.dm-search-input {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: var(--color-text);
}
.dm-search-cancel {
  font-size: 26rpx;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.dm-search-results {
  height: 50vh;
  margin-top: 16rpx;
}
.dm-search-empty {
  padding: 48rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.dm-search-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 24rpx 8rpx;
  border-bottom: 1rpx solid var(--color-divider);
}
.dm-search-result-text {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dm-search-result-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

/* 空会话提示：居中浅色文案 */
.message-empty {
  padding: 120rpx 32rpx;
  display: flex;
  justify-content: center;
}
.message-empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.message-item {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.message-self {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 72rpx;
  height: 72rpx;
  /* 圆形头像（与资料卡头像保持一致） */
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.msg-avatar--self {
  background: var(--color-primary);
}

.msg-avatar-img {
  width: 100%;
  height: 100%;
}

.avatar-letter-sm {
  font-size: 22rpx;
  font-weight: 700;
  color: #ffffff;
}

/* 消息气泡 */
.bubble {
  max-width: 75%;
  padding: 20rpx 28rpx;
  word-break: break-all;
}

/* 图片消息：固定最大宽度，高度按原图比例 */
.msg-image {
  max-width: 360rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: var(--color-surface);
}

.msg-image-content {
  display: block;
  width: 360rpx;
  border-radius: 16rpx;
}

.bubble-text {
  font-size: 26rpx;
  line-height: 1.6;
}

.bubble-remote {
  background: var(--color-surface);
  border-radius: 28rpx 28rpx 28rpx 8rpx;
}

.bubble-remote .bubble-text {
  color: var(--color-text);
}

.bubble-self {
  background: var(--color-primary);
  border-radius: 28rpx 28rpx 8rpx 28rpx;
}

.bubble-self .bubble-text {
  color: #ffffff;
}

/* 商品卡片 */
.product-card {
  max-width: 75%;
  border-radius: 28rpx;
  overflow: hidden;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.product-image {
  height: 200rpx;
  background: linear-gradient(135deg, var(--color-fill, #f2f2f7), var(--color-surface, #ffffff));
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-emoji {
  font-size: 80rpx;
}

.product-info {
  padding: 24rpx;
}

.product-name {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--color-text);
}

.product-desc {
  font-size: 22rpx;
  color: var(--color-text-secondary);
  margin-top: 4rpx;
}

.product-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.product-price {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--color-primary);
}

.product-cta {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  background: var(--color-primary);
}

.product-cta-text {
  font-size: 22rpx;
  font-weight: 600;
  color: #ffffff;
}

/* 底部输入栏 */
.dm-input-bar {
  flex-shrink: 0;
  background: var(--color-surface-veil-95, rgba(255, 255, 255, 0.95));
  border-top: 1rpx solid var(--color-border);
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}

/* 引用预览条 */
.dm-quote-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 12rpx;
  padding: 8rpx 16rpx;
  border-left: 4rpx solid var(--color-primary);
  border-radius: 8rpx;
  background: var(--color-background);
}
.dm-quote-preview-text {
  flex: 1;
  min-width: 0;
  font-size: 22rpx;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dm-quote-preview-close {
  padding: 0 8rpx;
  font-size: 26rpx;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

/* 多选删除操作栏 */
.dm-select-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 32rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-border);
}
.dm-select-cancel {
  font-size: 28rpx;
  color: var(--color-text-secondary);
}
.dm-select-delete {
  padding: 16rpx 40rpx;
  border-radius: 36rpx;
  background: #e5484d;
}
.dm-select-delete--disabled {
  opacity: 0.5;
}
.dm-select-delete-text {
  font-size: 26rpx;
  color: #ffffff;
}

/* 转发面板 */
.dm-forward-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: rgba(0, 0, 0, 0.28);
  display: flex;
  align-items: flex-end;
}
.dm-forward-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-radius: 24rpx 24rpx 0 0;
  padding: 24rpx 0 calc(24rpx + env(safe-area-inset-bottom));
}
.dm-forward-title {
  text-align: center;
  padding: 8rpx 0 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}
.dm-forward-list {
  height: 46vh;
}
.dm-forward-empty {
  padding: 48rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.dm-forward-item {
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid var(--color-divider);
}
.dm-forward-name {
  font-size: 28rpx;
  color: var(--color-text);
}

.input-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.add-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  /* 原写死 #f2f2f7，深色下会变成亮色圆钮；改用 --color-background
     （浅 #f6f2ee / 深 #1a1816），保持"凹陷输入区比栏底更深"的关系。 */
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.add-btn--disabled {
  opacity: 0.5;
}

.add-icon {
  font-size: 36rpx;
  color: var(--color-text-secondary);
}

.input-wrapper {
  flex: 1;
  height: 72rpx;
  border-radius: 36rpx;
  background: var(--color-background);
  border: 1rpx solid var(--color-border);
  padding: 0 28rpx;
  display: flex;
  align-items: center;
}

.msg-input {
  width: 100%;
  font-size: 28rpx;
  color: var(--color-text);
}

.msg-input-placeholder {
  color: var(--color-text-secondary);
}

.send-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.send-icon {
  font-size: 32rpx;
  color: #ffffff;
}

/* 语音消息气泡：宽度由 JS 按录音时长动态设置 */
.msg-voice {
  max-width: 420rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 24rpx;
  border-radius: 28rpx;
}

.msg-voice--remote {
  background: var(--color-surface);
  border-radius: 28rpx 28rpx 28rpx 8rpx;
}

.msg-voice--self {
  background: var(--color-primary);
  border-radius: 28rpx 28rpx 8rpx 28rpx;
}

.voice-icon {
  font-size: 28rpx;
  flex-shrink: 0;
}

.msg-voice--remote .voice-icon {
  color: var(--color-text);
}

.msg-voice--self .voice-icon {
  color: #ffffff;
}

.voice-wave {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.voice-wave-bar {
  width: 4rpx;
  height: 16rpx;
  border-radius: 2rpx;
}

.voice-wave-bar:nth-child(2) {
  height: 30rpx;
}

.voice-wave-bar:nth-child(3) {
  height: 22rpx;
}

.voice-wave-bar:nth-child(4) {
  height: 12rpx;
}

.msg-voice--remote .voice-wave-bar {
  background: var(--color-text-secondary);
}

.msg-voice--self .voice-wave-bar {
  background: rgba(255, 255, 255, 0.7);
}

.voice-duration {
  font-size: 24rpx;
  flex-shrink: 0;
}

.msg-voice--remote .voice-duration {
  color: var(--color-text-secondary);
}

.msg-voice--self .voice-duration {
  color: #ffffff;
}

/* 文件消息气泡 */
.msg-file {
  max-width: 420rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: var(--color-surface);
}

.msg-file-icon {
  font-size: 44rpx;
  color: var(--color-primary);
  flex-shrink: 0;
}

.msg-file-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.msg-file-name {
  max-width: 300rpx;
  font-size: 26rpx;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-file-size {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 按住录音按钮 */
.voice-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.voice-btn .luc {
  font-size: 34rpx;
  color: var(--color-text-secondary);
}

.voice-btn--active {
  background: var(--color-primary);
}

.voice-btn--active .luc {
  color: #ffffff;
}

/* 录音中提示 */
.voice-recording-tip {
  position: fixed;
  left: 50%;
  bottom: 220rpx;
  transform: translateX(-50%);
  z-index: 998;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 32rpx;
  border-radius: 32rpx;
  background: rgba(0, 0, 0, 0.72);
}

.voice-recording-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #e5484d;
}

.voice-recording-text {
  font-size: 24rpx;
  color: #ffffff;
}

/* 多选：转发按钮与禁用态 */
.dm-select-forward {
  padding: 16rpx 32rpx;
  border-radius: 36rpx;
  background: var(--color-primary);
}

.dm-select-forward-text {
  font-size: 26rpx;
  color: #ffffff;
}

.dm-select-action--disabled {
  opacity: 0.5;
}

/* 群聊：标题旁成员数 */
.nav-group-count {
  font-size: 22rpx;
  font-weight: 400;
  color: var(--color-text-tertiary);
}

/* 群聊：对方消息发送者昵称（与头像留出间距） */
.msg-group-sender-row {
  padding-left: 96rpx;
  margin-bottom: 4rpx;
}

.msg-group-sender {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}

/* 群成员面板 */
.dm-group-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: rgba(0, 0, 0, 0.28);
  display: flex;
  align-items: flex-end;
}

.dm-group-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-radius: 24rpx 24rpx 0 0;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

.dm-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx 12rpx;
}

.dm-group-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}

.dm-group-close {
  padding: 0 8rpx;
  font-size: 30rpx;
  color: var(--color-text-tertiary);
}

.dm-group-list {
  height: 44vh;
}

.dm-group-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 32rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.dm-group-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.dm-group-avatar--letter {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
}

.dm-group-info {
  flex: 1;
  min-width: 0;
}

.dm-group-name-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.dm-group-name {
  font-size: 26rpx;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dm-group-owner-tag,
.dm-group-self-tag {
  padding: 2rpx 10rpx;
  border-radius: 10rpx;
  font-size: 18rpx;
}

.dm-group-owner-tag {
  color: #b7791f;
  background: rgba(183, 121, 31, 0.12);
}

.dm-group-self-tag {
  color: var(--color-text-secondary);
  background: var(--color-background);
}

.dm-group-remove {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  color: #e5484d;
  background: rgba(229, 72, 77, 0.08);
  flex-shrink: 0;
}

.dm-group-actions {
  display: flex;
  gap: 16rpx;
  padding: 20rpx 32rpx 0;
}

.dm-group-invite,
.dm-group-leave {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dm-group-invite {
  background: var(--color-primary);
}

.dm-group-invite-text {
  font-size: 26rpx;
  color: #ffffff;
}

.dm-group-leave {
  background: var(--color-background);
  border: 1rpx solid var(--color-border);
}

.dm-group-leave-text {
  font-size: 26rpx;
  color: #e5484d;
}

/* 群公告卡片 */
.group-announcement {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-bottom: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: var(--color-card);
  border: 1rpx solid var(--color-border);
}

.group-announcement-label {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--color-primary);
}

.group-announcement-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

/* @成员按钮 */
.mention-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mention-icon {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--color-text-secondary);
}

/* 消息中的 @昵称高亮 */
.bubble-mention {
  color: var(--color-primary);
  font-weight: 600;
}

.bubble-self .bubble-mention {
  color: #ffe08a;
}
</style>
