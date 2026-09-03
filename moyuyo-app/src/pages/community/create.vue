<template>
  <view class="create-post">
    <!-- 顶部导航:左侧返回 + 标题 + 右侧主操作按钮(圆角强调色,符合主流社交 APP 模式) -->
    <view class="navbar">
      <view class="navbar__back" @click="onBack">
        <text class="navbar__back-icon">‹</text>
      </view>
      <text class="navbar__title">发布帖子</text>
      <!-- 草稿操作:有内容时显示"存草稿",已有草稿时显示"草稿"标签 -->
      <view
        v-if="hasUnsavedContent || draftExists"
        class="navbar__draft"
        @click="onDraftTap"
      >
        <text v-if="hasUnsavedContent && !draftSavedAt" class="navbar__draft-text">存草稿</text>
        <text v-else class="navbar__draft-text navbar__draft-text--active">
          草稿{{ draftSavedAt ? ' · ' + formatDraftAge(draftSavedAt) : '' }}
        </text>
      </view>
      <!-- 主操作按钮:未填内容时置灰但不消失,持续可见引导用户 -->
      <view class="navbar__publish" :class="{ 'is-disabled': !canPublish }" @click="onPublish">
        <text v-if="submitting" class="navbar__publish-loading">发布中</text>
        <text v-else>发布</text>
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 内容输入:大尺寸文本框 + 字数计数(右下角) -->
      <view class="composer">
        <textarea
          v-model="content"
          class="composer__textarea"
          placeholder="说点什么吧…分享你和宠物的日常"
          :maxlength="2000"
          placeholder-class="composer__placeholder"
          :auto-height="true"
          @input="onContentInput"
          @blur="onContentBlur"
          @focus="onContentFocus"
          @keydown="onComposerKeydown"
        />
        <text class="composer__counter">{{ content.length }}/2000</text>

        <!-- 敏感词实时提示:命中时显示在 counter 上方,黄色警告条 -->
        <view v-if="sensitiveHits.length > 0" class="composer__warning">
          <text class="composer__warning-icon">⚠</text>
          <text class="composer__warning-text">
            包含敏感词:
            <text
              v-for="(hit, i) in sensitiveHits"
              :key="i"
              class="composer__warning-hit"
            >{{ hit }}{{ i < sensitiveHits.length - 1 ? '、' : '' }}</text>
            ,请修改
          </text>
        </view>

        <!-- # 话题联想浮层:输入 # 时浮在 textarea 下方,绝对定位避免影响布局 -->
        <view v-if="topicSuggest.visible" class="topic-suggest" catchtap="noop">
          <view class="topic-suggest__head">
            <text class="topic-suggest__title">话题</text>
            <text class="topic-suggest__hint">点击插入或选「创建话题」</text>
          </view>
          <scroll-view scroll-y class="topic-suggest__list">
            <view
              v-if="topicSuggest.allowCreate"
              class="topic-suggest__item topic-suggest__item--create"
              @tap="onCreateTopic"
            >
              <text class="topic-suggest__icon">+</text>
              <text class="topic-suggest__name">创建话题 #{{ topicSuggest.query }}</text>
            </view>
            <view
              v-for="(t, idx) in topicSuggest.candidates"
              :key="t.id || idx"
              class="topic-suggest__item"
              :class="{ active: idx === topicSuggest.activeIdx }"
              @tap="onPickTopicFromSuggest(t)"
            >
              <text class="topic-suggest__name"># {{ t.name }}</text>
              <text v-if="t.postCount != null" class="topic-suggest__count">{{ formatCount(t.postCount) }} 帖</text>
            </view>
            <view
              v-if="topicSuggest.candidates.length === 0 && !topicSuggest.allowCreate"
              class="topic-suggest__empty"
            >
              <text>没有匹配的话题</text>
            </view>
          </scroll-view>
        </view>

        <!-- @ 提及用户联想浮层:输入 @ 时触发,实时调用 /users/search 接口 -->
        <view v-if="mentionSuggest.visible" class="mention-suggest" catchtap="noop">
          <view class="mention-suggest__head">
            <text class="mention-suggest__title">提及用户</text>
            <text v-if="mentionSuggest.loading" class="mention-suggest__loading">搜索中…</text>
            <text v-else-if="mentionSuggest.query" class="mention-suggest__hint">
              匹配「{{ mentionSuggest.query }}」
            </text>
          </view>
          <scroll-view scroll-y class="mention-suggest__list">
            <view
              v-for="(u, idx) in mentionSuggest.candidates"
              :key="u.id || idx"
              class="mention-suggest__item"
              :class="{ active: idx === mentionSuggest.activeIdx }"
              @tap="onPickMentionFromSuggest(u)"
            >
              <image
                v-if="u.avatar"
                :src="resolveAvatarUrl(u.avatar)"
                class="mention-suggest__avatar"
                mode="aspectFill"
              />
              <view v-else class="mention-suggest__avatar mention-suggest__avatar--fallback">
                {{ avatarChar(u.nickname) }}
              </view>
              <view class="mention-suggest__info">
                <text class="mention-suggest__name">@ {{ u.nickname }}</text>
              </view>
            </view>
            <view
              v-if="!mentionSuggest.loading && mentionSuggest.candidates.length === 0 && mentionSuggest.query"
              class="mention-suggest__empty"
            >
              <text>没有匹配的用户</text>
            </view>
            <view
              v-else-if="!mentionSuggest.loading && mentionSuggest.candidates.length === 0 && !mentionSuggest.query"
              class="mention-suggest__empty"
            >
              <text>输入昵称关键词搜索</text>
            </view>
          </scroll-view>
        </view>
      </view>

      <!-- 视频区:视频与图片互斥,选了视频则隐藏图片九宫格 -->
      <view v-if="videoLocal || videoUrl" class="media-video">
        <view class="media-video__player">
          <video
            id="community-video-player"
            :src="videoLocal || videoUrl"
            class="media-video__el"
            controls
            :show-fullscreen-btn="true"
            :show-play-btn="true"
            object-fit="contain"
            @error="onVideoError"
            @loadedmetadata="onVideoLoadedMeta"
            @seeked="onVideoSeeked"
          />
          <view v-if="uploadingVideo" class="media-video__progress">
            <view class="media-video__progress-bar" :style="{ width: videoUploadProgress + '%' }" />
            <text class="media-video__progress-text">{{ videoUploadProgress }}%</text>
          </view>
        </view>
        <view class="media-video__meta">
          <text class="media-video__duration">⏱ {{ formatDuration(videoDuration) }}</text>
          <text class="media-video__size" v-if="videoSize">{{ formatSize(videoSize) }}</text>
          <text v-if="videoUrl" class="media-video__state">已上传</text>
          <text v-else class="media-video__state media-video__state--pending">待上传</text>
        </view>
        <!-- 封面选择按钮:点击生成 3 张候选帧并弹出选择面板 -->
        <view class="media-video__cover-btn" @click="onPickCover">
          <text class="media-video__cover-btn-icon">🎞</text>
          <text class="media-video__cover-btn-text">
            {{ coverUrl ? '已选封面 · 点击重选' : '选择视频封面' }}
          </text>
          <image
            v-if="coverUrl"
            :src="coverUrl"
            class="media-video__cover-thumb"
            mode="aspectFill"
          />
        </view>
        <view class="media-video__remove" @click="onRemoveVideo">×</view>
      </view>

      <!-- 图片九宫格:本地路径预览,有视频时隐藏(互斥) -->
      <view v-else class="media-section" @click="exitDragMode">
        <view
          v-for="(img, idx) in images"
          :key="idx"
          class="media-thumb"
          :class="{ 'media-thumb--dragging': draggingIndex === idx }"
          @longpress="onImageLongPress(idx)"
          @tap="onImageTapInDragMode(idx)"
          @touchmove.stop.prevent="noop"
        >
          <image :src="img" class="media-thumb__img" mode="aspectFill" />
          <!-- 拖拽模式下显示序号 -->
          <view v-if="draggingIndex >= 0" class="media-thumb__order">
            {{ idx + 1 }}
          </view>
          <!-- 比例角标:首图(idx=0)展示当前比例 + 是否最佳 -->
          <view
            v-if="imageMeta[idx] && imageMeta[idx].ratioLabel"
            class="media-thumb__ratio"
            :class="{ 'media-thumb__ratio--ideal': imageMeta[idx].isIdealRatio }"
          >
            {{ imageMeta[idx].ratioLabel }}{{ imageMeta[idx].isIdealRatio ? ' ✓' : '' }}
          </view>
          <!-- 拖拽模式下隐藏删除按钮(避免误删) -->
          <view v-if="draggingIndex < 0" class="media-thumb__remove" @click.stop="removeImage(idx)">×</view>
        </view>
        <view v-if="images.length < 9" class="media-add" @click="onPickImage">
          <text class="media-add__icon">+</text>
          <text class="media-add__text">{{ images.length || '' }} / 9</text>
        </view>
      </view>

      <!-- 设置项:Cell 列表统一风格 -->
      <view class="option-card">
        <view class="option-cell" @click="onPickTopic">
          <text class="option-cell__icon">🐾</text>
          <text class="option-cell__label">话题</text>
          <text class="option-cell__value" :class="{ 'is-placeholder': !topic }">
            {{ topic || '选择话题' }}
          </text>
          <text class="option-cell__arrow">›</text>
        </view>
        <view class="option-cell" @click="onPickLocation">
          <text class="option-cell__icon">📍</text>
          <text class="option-cell__label">位置</text>
          <text class="option-cell__value" :class="{ 'is-placeholder': !location }">
            {{ location || '不显示位置' }}
          </text>
          <text class="option-cell__arrow">›</text>
        </view>
        <!-- 已选位置支持清除 -->
        <view v-if="location" class="option-cell option-cell--clearable" @click="onClearLocation">
          <text class="option-cell__icon option-cell__icon--muted">🗑️</text>
          <text class="option-cell__label option-cell__label--muted">清除位置</text>
        </view>
        <view class="option-cell" @click="onPickVisibility">
          <text class="option-cell__icon">👁</text>
          <text class="option-cell__label">谁可以看</text>
          <text class="option-cell__value">{{ visibilityLabel }}</text>
          <text class="option-cell__arrow">›</text>
        </view>
        <!-- 定时发布:cell 模式,点击弹出日期+时间 picker -->
        <view class="option-cell" @click="onPickScheduled">
          <text class="option-cell__icon">⏰</text>
          <text class="option-cell__label">定时发布</text>
          <text class="option-cell__value" :class="{ 'is-placeholder': !scheduledAt }">
            {{ scheduledAt ? formatScheduled(scheduledAt) : '立即发布' }}
          </text>
          <text class="option-cell__arrow">›</text>
        </view>
        <!-- 清除定时发布(仅在已选时显示) -->
        <view v-if="scheduledAt" class="option-cell option-cell--clearable" @click="onClearScheduled">
          <text class="option-cell__icon option-cell__icon--muted">🗑️</text>
          <text class="option-cell__label option-cell__label--muted">清除定时</text>
        </view>
      </view>
    </scroll-view>

    <!-- 底部工具栏:快捷输入入口 -->
    <view class="toolbar">
      <view
        class="toolbar__btn"
        :class="{ active: emojiPanelVisible }"
        @click="toggleEmojiPanel"
      >
        <text class="toolbar__icon">😊</text>
      </view>
      <view class="toolbar__btn" @click="onMention">
        <text class="toolbar__icon">@</text>
      </view>
      <view class="toolbar__btn" @click="onInsertHash">
        <text class="toolbar__icon">#</text>
      </view>
      <view class="toolbar__btn" @click="onPickImage">
        <text class="toolbar__icon">🖼</text>
      </view>
      <view class="toolbar__btn" @click="onPickVideo">
        <text class="toolbar__icon">🎬</text>
      </view>
    </view>

    <!-- Emoji 面板:点击 😊 后从底部弹出,7 类分类 + 搜索 -->
    <view v-if="emojiPanelVisible" class="emoji-panel">
      <!-- 顶部搜索框 -->
      <view class="emoji-panel__search">
        <text class="luc luc-search emoji-panel__search-icon" />
        <input
          v-model="emojiKeyword"
          class="emoji-panel__search-input"
          placeholder="搜索 emoji(中文/英文/拼音首字母)"
          placeholder-class="emoji-panel__search-placeholder"
          confirm-type="search"
        >
        <text
          v-if="emojiKeyword"
          class="luc luc-x emoji-panel__search-clear"
          @tap="emojiKeyword = ''"
        />
      </view>
      <scroll-view scroll-x class="emoji-panel__tabs">
        <view
          v-for="(cat, idx) in emojiCategories"
          :key="idx"
          class="emoji-panel__tab"
          :class="{ active: emojiActiveCat === idx }"
          @tap="emojiActiveCat = idx; emojiKeyword = ''"
        >
          <text class="emoji-panel__tab-icon">{{ cat.icon }}</text>
          <text class="emoji-panel__tab-name">{{ cat.name }}</text>
        </view>
      </scroll-view>
      <scroll-view scroll-y class="emoji-panel__grid-wrap">
        <!-- 搜索结果视图:有 emojiKeyword 时显示搜索结果 -->
        <view v-if="emojiKeyword" class="emoji-panel__grid">
          <view
            v-for="(e, i) in emojiSearchResults"
            :key="'s-' + i"
            class="emoji-panel__item"
            @tap="insertEmojiAtCursor(e)"
          >{{ e }}</view>
          <view v-if="emojiSearchResults.length === 0" class="emoji-panel__empty">
            <text>没有匹配的 emoji</text>
          </view>
        </view>
        <!-- 默认视图:按当前分类显示 -->
        <view v-else class="emoji-panel__grid">
          <view
            v-for="(e, i) in emojiCategories[emojiActiveCat].list"
            :key="i"
            class="emoji-panel__item"
            @tap="insertEmojiAtCursor(e)"
          >{{ e }}</view>
        </view>
      </scroll-view>
      <view class="emoji-panel__footer">
        <view class="emoji-panel__backspace" @tap="onEmojiBackspace">
          <text class="emoji-panel__backspace-icon">⌫</text>
        </view>
      </view>
    </view>

    <!-- 话题选择面板:从真实后端 /api/v1/community/topics 拉取 -->
    <view v-if="topicPickerVisible" class="sheet-mask" @click="closeTopicPicker">
      <view class="sheet" @click.stop>
        <view class="sheet__header">
          <text class="sheet__title">选择话题</text>
          <view class="sheet__close" @click="closeTopicPicker">×</view>
        </view>
        <view v-if="topicsLoading" class="sheet__loading">
          <text>加载中...</text>
        </view>
        <view v-else-if="!topics.length" class="sheet__empty">
          <text>暂无可用话题</text>
        </view>
        <scroll-view v-else scroll-y class="sheet__list">
          <view
            v-for="t in topics"
            :key="t.id"
            class="topic-item"
            :class="{ 'is-active': topic === t.name }"
            @click="selectTopic(t)"
          >
            <view class="topic-item__main">
              <text class="topic-item__name"># {{ t.name }}</text>
              <text v-if="t.description" class="topic-item__desc">{{ t.description }}</text>
            </view>
            <text class="topic-item__count">{{ formatCount(t.postCount) }} 帖</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 定时发布面板:选择未来某个时间点发布帖子 -->
    <view v-if="scheduledPickerVisible" class="sheet-mask" @click="closeScheduledPicker">
      <view class="sheet" @click.stop>
        <view class="sheet__header">
          <text class="sheet__title">定时发布</text>
          <view class="sheet__close" @click="closeScheduledPicker">×</view>
        </view>
        <view class="scheduled-picker__hint">
          <text>选择未来某个时间点(至少 1 分钟后),系统到点自动发布</text>
        </view>
        <!-- 快捷选项 -->
        <view class="scheduled-picker__shortcuts">
          <view class="scheduled-picker__shortcut" @tap="onPickScheduledShortcut(30)">
            <text class="scheduled-picker__shortcut-name">30 分钟后</text>
            <text class="scheduled-picker__shortcut-desc">{{ formatFutureTime(30) }}</text>
          </view>
          <view class="scheduled-picker__shortcut" @tap="onPickScheduledShortcut(60)">
            <text class="scheduled-picker__shortcut-name">1 小时后</text>
            <text class="scheduled-picker__shortcut-desc">{{ formatFutureTime(60) }}</text>
          </view>
          <view class="scheduled-picker__shortcut" @tap="onPickScheduledShortcut(180)">
            <text class="scheduled-picker__shortcut-name">3 小时后</text>
            <text class="scheduled-picker__shortcut-desc">{{ formatFutureTime(180) }}</text>
          </view>
          <view class="scheduled-picker__shortcut" @tap="onPickScheduledShortcut(1440)">
            <text class="scheduled-picker__shortcut-name">明天同时</text>
            <text class="scheduled-picker__shortcut-desc">{{ formatFutureTime(1440) }}</text>
          </view>
        </view>
        <!-- 自定义时间:用 picker 选日期 + 时间 -->
        <view class="scheduled-picker__custom">
          <text class="scheduled-picker__custom-label">自定义时间</text>
          <picker mode="multiSelector" :range="customTimeRange" :value="customTimeValue" @change="onCustomTimeChange">
            <view class="scheduled-picker__custom-input">
              <text>{{ customTimeDisplay || '点击选择日期和时间' }}</text>
              <text class="scheduled-picker__custom-arrow">›</text>
            </view>
          </picker>
        </view>
        <view class="scheduled-picker__footer">
          <view class="scheduled-picker__btn" @click="closeScheduledPicker">取消</view>
          <view
            class="scheduled-picker__btn scheduled-picker__btn--primary"
            :class="{ disabled: !scheduledAt }"
            @click="onConfirmScheduled"
          >
            确定
          </view>
        </view>
      </view>
    </view>

    <!-- 视频封面选择面板:从视频中截 3 张候选帧,用户挑 1 张作为封面 -->
    <view v-if="coverPickerVisible" class="sheet-mask" @click="closeCoverPicker">
      <view class="sheet" @click.stop>
        <view class="sheet__header">
          <text class="sheet__title">选择视频封面</text>
          <view class="sheet__close" @click="closeCoverPicker">×</view>
        </view>
        <view class="cover-picker__hint">
          <text>系统已从视频 {{ formatDuration(videoDuration) }} 中抽取 3 帧,点击选择一张作为封面</text>
        </view>
        <view v-if="!coverCandidates.length && !coverCapturing" class="cover-picker__loading">
          <text>正在截取视频帧...</text>
        </view>
        <scroll-view v-else scroll-y class="sheet__list">
          <view class="cover-grid">
            <view
              v-for="(c, idx) in coverCandidates"
              :key="idx"
              class="cover-grid__item"
              :class="{ active: selectedCoverIdx === idx }"
              @tap="onSelectCover(idx)"
            >
              <image :src="c.dataUrl" class="cover-grid__img" mode="aspectFill" />
              <view class="cover-grid__time">{{ formatDuration(c.time) }}</view>
              <view v-if="selectedCoverIdx === idx" class="cover-grid__check">✓</view>
            </view>
          </view>
          <view v-if="uploadingCover" class="cover-picker__uploading">
            <text>上传封面中...</text>
          </view>
        </scroll-view>
        <view class="cover-picker__footer">
          <view class="cover-picker__btn" @click="closeCoverPicker">取消</view>
          <view
            class="cover-picker__btn cover-picker__btn--primary"
            :class="{ disabled: selectedCoverIdx < 0 || uploadingCover }"
            @click="onConfirmCover"
          >
            {{ uploadingCover ? '上传中...' : '确定' }}
          </view>
        </view>
      </view>
    </view>

    <!-- 位置选择面板:从底部弹出,提供 POI 搜索 + 使用当前定位 + 不显示 -->
    <view v-if="locationPickerVisible" class="sheet-mask" @click="closeLocationPicker">
      <view class="sheet" @click.stop>
        <view class="sheet__header">
          <text class="sheet__title">选择位置</text>
          <view class="sheet__close" @click="closeLocationPicker">×</view>
        </view>

        <!-- 顶部搜索框 -->
        <view class="location-picker__search">
          <text class="luc luc-search location-picker__search-icon" />
          <input
            v-model="locationKeyword"
            class="location-picker__search-input"
            placeholder="搜索附近地点"
            placeholder-class="location-picker__search-placeholder"
            @input="onLocationKeywordChange"
          >
          <text
            v-if="locationKeyword"
            class="luc luc-x location-picker__search-clear"
            @click="locationKeyword = ''; onLocationKeywordChange()"
          />
        </view>

        <!-- 快捷选项:使用当前定位 + 不显示位置 -->
        <view class="location-picker__shortcuts">
          <view class="location-picker__shortcut" @tap="onUseCurrentLocation">
            <text class="location-picker__shortcut-icon">📍</text>
            <view class="location-picker__shortcut-info">
              <text class="location-picker__shortcut-name">使用当前定位</text>
              <text class="location-picker__shortcut-desc">{{ locationLoading ? '获取中…' : '获取您所在位置' }}</text>
            </view>
          </view>
          <view class="location-picker__shortcut" @tap="onClearLocation">
            <text class="location-picker__shortcut-icon">🚫</text>
            <view class="location-picker__shortcut-info">
              <text class="location-picker__shortcut-name">不显示位置</text>
              <text class="location-picker__shortcut-desc">关闭位置标签</text>
            </view>
          </view>
        </view>

        <!-- 附近地点列表(本地演示数据,真实环境接入腾讯/高德 POI) -->
        <view class="location-picker__hint">
          <text>附近地点(本地演示)</text>
        </view>
        <scroll-view scroll-y class="sheet__list">
          <view
            v-for="(p, idx) in filteredPois"
            :key="p.name + '-' + idx"
            class="poi-item"
            :class="{ 'is-active': selectedLocation && selectedLocation.name === p.name }"
            @tap="onPickPoi(p)"
          >
            <text class="poi-item__icon">{{ p.icon }}</text>
            <view class="poi-item__main">
              <text class="poi-item__name">{{ p.name }}</text>
              <text class="poi-item__address">{{ p.address }}</text>
            </view>
            <text class="poi-item__distance">{{ p.distance }}</text>
          </view>
          <view v-if="filteredPois.length === 0" class="sheet__empty">
            <text>没有匹配的地点</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import { communityApi, uploadApi } from '@/api'
import { useUserStore } from '@/store'
import { STORAGE_KEYS } from '@/utils/storage'

export default {
  pageTitleKey: 'pageTitle.communityCreate',

  data() {
    return {
      // 表单数据
      content: '',
      // images: 本地预览路径,发布时逐张上传转 URL
      images: [],
      // uploadedImageUrls: 已上传的图片 URL,最终传给 createPost
      uploadedImageUrls: [],
      // 视频(单选):videoLocal = 本地预览路径,videoUrl = 已上传 URL,videoDuration = 秒
      videoLocal: '',
      videoUrl: '',
      videoDuration: 0,
      videoSize: 0,
      videoUploadProgress: 0,
      uploadingVideo: false,
      // 图片元信息(与 images 一一对应):{width, height, ratioLabel, isIdealRatio}
      // 用于在缩略图角标展示比例提示,引导用户使用 3:4 最佳比例
      imageMeta: [],
      // 拖拽模式:长按图片 500ms 进入;显示序号 + 半透明,点击其他图片交换
      draggingIndex: -1,
      // 实时敏感词提示:敏感词命中字符串列表(去重保序)
      sensitiveHits: [],
      // 防抖定时器
      sensitiveCheckTimer: null,
      // 当前草稿 id(每份草稿一个 UUID):null = 新建,非空 = 编辑已有
      draftId: null,
      // 视频封面候选帧:{ time:秒, dataUrl:base64 } 三条
      coverCandidates: [],
      // 选中的封面索引(0/1/2)
      selectedCoverIdx: -1,
      // 封面选择面板显示
      coverPickerVisible: false,
      // 视频元素 ref(用于 seek + canvas 截图)
      videoCtx: null,
      // 截帧上传状态
      uploadingCover: false,
      // 正在生成候选帧(用于面板 loading)
      coverCapturing: false,
      // 视频上传成功后得到的 cover URL(随帖子一起提交)
      coverUrl: '',
      // 定时发布:null = 立即发布;非空 = ISO 字符串
      scheduledAt: '',
      scheduledPickerVisible: false,
      // 自定义 picker:范围 [日期数组, 小时数组, 分钟数组]
      customTimeRange: [[], [], []],
      customTimeValue: [0, 0, 0],
      customTimeDisplay: '',
      topic: '',
      location: '',
      visibility: 'public',
      submitting: false,
      uploadingImages: false,
      // 话题选择器
      topicPickerVisible: false,
      topics: [],
      topicsLoading: false,
      // 草稿相关
      draftExists: false,    // 进入页面时检测到本地有草稿
      draftSavedAt: null,    // 草稿保存时间
      // # 话题联想
      topicSuggest: {
        visible: false,        // 浮层是否显示
        query: '',             // 当前光标前的 #xxx 查询词(去掉 #)
        rangeStart: -1,        // # 字符在文本中的索引
        rangeEnd: -1,          // 当前光标位置
        candidates: [],        // 候选话题列表
        activeIdx: -1,         // 键盘选中索引
        allowCreate: false,    // 是否显示"创建话题"
      },
      // Emoji 面板
      emojiPanelVisible: false,
      emojiActiveCat: 0,        // 当前分类索引
      emojiKeyword: '',         // emoji 搜索关键词(emoji 面板顶部搜索)
      // 记录 textarea 当前光标位置,emoji 插入用
      textareaCursor: 0,
      // @ 提及联想
      mentionSuggest: {
        visible: false,
        query: '',             // @ 后输入的查询词
        rangeStart: -1,        // @ 字符在文本中的索引
        rangeEnd: -1,          // 当前光标位置
        candidates: [],        // 候选用户列表
        activeIdx: -1,
        loading: false,
      },
      // 位置 POI 选择面板
      locationPickerVisible: false,
      locationKeyword: '',     // POI 搜索关键词
      locationLoading: false,   // 正在获取定位
      // 已选 POI:{ name: string, address: string, lat?: number, lon?: number }
      // 优先从 location 对象读;location 字符串仅用于 cell 显示
      selectedLocation: null,
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /** 是否可发布:有内容或图片或视频,且未提交中 */
    canPublish() {
      return !this.submitting
        && (!!(this.content || '').trim()
          || this.images.length > 0
          || !!this.videoUrl)
    },
    /** 是否含有未保存的文字内容(用于退出提示) */
    hasUnsavedContent() {
      return !!this.content.trim() || !!this.topic || !!this.location
    },
    /** 可见性文本映射 */
    visibilityLabel() {
      return { public: '公开', friends: '仅好友', private: '仅自己' }[this.visibility] || '公开'
    },
    /**
     * POI 数据 + 过滤:
     * 当前未接入第三方地图 SDK,使用本地演示数据。
     * 生产环境应替换为:调起腾讯/高德地图 SDK 搜索接口,按 keyword 返回 POI 列表。
     */
    poiList() {
      return [
        { name: '三里屯 SOHO', address: '北京市朝阳区工体北路', distance: '0.5km', icon: '🏢', lat: 39.9367, lon: 116.4561 },
        { name: '蓝色港湾', address: '北京市朝阳区朝阳公园路', distance: '1.2km', icon: '🛍️', lat: 39.9418, lon: 116.4779 },
        { name: '朝阳公园', address: '北京市朝阳区朝阳公园南路', distance: '1.5km', icon: '🌳', lat: 39.9388, lon: 116.4779 },
        { name: '悠唐购物中心', address: '北京市朝阳区三丰北里', distance: '1.8km', icon: '🛍️', lat: 39.9235, lon: 116.4624 },
        { name: '团结湖公园', address: '北京市朝阳区团结湖南里', distance: '2.0km', icon: '🌳', lat: 39.9305, lon: 116.4649 },
        { name: '宠物医院(24小时)', address: '北京市朝阳区工体北路', distance: '0.8km', icon: '🏥', lat: 39.9380, lon: 116.4570 },
        { name: '宠物咖啡馆 MOFUN', address: '北京市朝阳区三里屯太古里', distance: '0.6km', icon: '☕', lat: 39.9363, lon: 116.4537 },
        { name: '萌宠主题公园', address: '北京市朝阳区东风公园', distance: '3.2km', icon: '🐾', lat: 39.9421, lon: 116.4823 },
        { name: '三里屯宠物医院', address: '北京市朝阳区工人体育场北路', distance: '0.7km', icon: '🏥', lat: 39.9372, lon: 116.4559 },
      ]
    },
    /**
     * 根据 locationKeyword 过滤 POI 列表
     * 匹配方式:name 或 address 包含关键词(大小写不敏感)
     */
    filteredPois() {
      const all = this.poiList || []
      const q = (this.locationKeyword || '').trim().toLowerCase()
      if (!q) return all
      return all.filter((p) => {
        return (p.name || '').toLowerCase().includes(q)
            || (p.address || '').toLowerCase().includes(q)
      })
    },
  },

  onLoad(query) {
    // 登录态校验:未登录直接拦截并跳登录页
    if (!this.userStore.isLoggedIn) {
      uni.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => uni.reLaunch({ url: '/pages/user/login' }), 800)
      return
    }
    // 预加载话题列表,用户进入即可点选
    this.loadTopics()
    // 优先:从草稿列表跳转过来(query.draftId)→ 直接恢复该草稿
    if (query && query.draftId) {
      const all = this.readAllDrafts()
      const target = all.find((d) => d.id === query.draftId)
      if (target) {
        this.restoreDraft(target)
        return
      }
    }
    // 否则检测草稿列表(单份直接提示恢复,多份引导到列表页)
    this.checkDraft()
  },

  onUnload() {
    // 兜底:用户通过系统返回/手势直接退出时,如有内容自动保存草稿
    // (onBack 弹窗选择过"放弃"的话 hasUnsavedContent 会已被忽略,
    //  但这里再加一道保险:有内容且非 submitting 状态时静默保存)
    if (this.hasUnsavedContent && !this.submitting && !this._draftDiscarded) {
      this.saveDraft({ silent: true })
    }
  },

  methods: {
    /**
     * 返回处理:主流社交 APP 模式(三选项)
     * - 有未保存内容: 弹"保存草稿/放弃/取消"
     * - 无内容: 直接返回
     */
    onBack() {
      if (!this.hasUnsavedContent) {
        this._draftDiscarded = true
        uni.navigateBack()
        return
      }
      uni.showActionSheet({
        itemList: ['保存草稿', '放弃编辑', '继续编辑'],
        success: (res) => {
          if (res.tapIndex === 0) {
            // 保存草稿
            this.saveDraft({ silent: false, onSuccess: () => {
              this._draftDiscarded = true
              uni.navigateBack()
            } })
          } else if (res.tapIndex === 1) {
            // 放弃:清掉旧草稿,直接退出
            this._draftDiscarded = true
            this.clearDraft({ silent: true })
            uni.navigateBack()
          } else {
            // 继续编辑(什么都不做)
          }
        },
        fail: () => {
          // 用户取消 action sheet = 继续编辑
        },
      })
    },

    /**
     * 读取本地所有草稿(数组)。空或异常返回 []。
     */
    readAllDrafts() {
      try {
        const list = uni.getStorageSync(STORAGE_KEYS.COMMUNITY_POST_DRAFTS)
        return Array.isArray(list) ? list : []
      } catch (e) {
        return []
      }
    },

    /**
     * 把当前所有草稿写入本地存储。
     */
    writeAllDrafts(list) {
      try {
        uni.setStorageSync(STORAGE_KEYS.COMMUNITY_POST_DRAFTS, list)
        return true
      } catch (e) {
        console.error('[create] writeAllDrafts failed:', e)
        return false
      }
    },

    /**
     * 保存草稿到本地存储。
     * - 已存在 draftId → 更新该条;否则新增一条(分配 UUID)
     * - 30 天以上的旧草稿在写入前自动清理(避免列表膨胀)
     * 注意:仅保存文字字段(content/topic/location/visibility),本地图片路径无法持久化
     */
    saveDraft({ silent = true, onSuccess } = {}) {
      try {
        let all = this.readAllDrafts()
        // 清理过期草稿(30 天)
        const THIRTY_DAYS = 30 * 24 * 60 * 60 * 1000
        const now = Date.now()
        all = all.filter((d) => d && d.savedAt && now - d.savedAt < THIRTY_DAYS)
        const draft = {
          id: this.draftId || this._genDraftId(),
          content: this.content || '',
          topic: this.topic || '',
          location: this.location || '',
          visibility: this.visibility || 'public',
          savedAt: now,
        }
        this.draftId = draft.id
        // 更新或追加
        const idx = all.findIndex((d) => d.id === draft.id)
        if (idx >= 0) {
          all[idx] = draft
        } else {
          all.unshift(draft)
        }
        // 限制最多 20 份(超过则删最旧的)
        if (all.length > 20) all = all.slice(0, 20)
        this.writeAllDrafts(all)
        this.draftExists = true
        this.draftSavedAt = draft.savedAt
        if (!silent) {
          uni.showToast({ title: '已保存到草稿', icon: 'success' })
        }
        onSuccess && onSuccess()
      } catch (e) {
        console.error('[create] saveDraft failed:', e)
        if (!silent) {
          uni.showToast({ title: '保存失败', icon: 'none' })
        }
      }
    },

    /** 生成草稿 UUID(简化版:时间戳 + 随机串) */
    _genDraftId() {
      return `draft_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    },

    /**
     * 进入页面时检测草稿。
     * 30 天以上的草稿视为过期,自动清理;有未过期草稿时弹窗让用户选择恢复哪一份。
     */
    checkDraft() {
      let all = this.readAllDrafts()
      // 清理过期
      const THIRTY_DAYS = 30 * 24 * 60 * 60 * 1000
      const now = Date.now()
      const before = all.length
      all = all.filter((d) => d && d.savedAt && now - d.savedAt < THIRTY_DAYS)
      if (all.length !== before) this.writeAllDrafts(all)
      // 过滤空草稿
      all = all.filter((d) => (d.content || '').trim() || d.topic || d.location)
      if (all.length === 0) return
      // 只有 1 份:直接弹恢复提示
      if (all.length === 1) {
        const draft = all[0]
        const ageStr = this.formatDraftAge(draft.savedAt)
        uni.showModal({
          title: '发现草稿',
          content: `上次编辑于${ageStr},是否恢复?`,
          confirmText: '恢复',
          cancelText: '丢弃',
          success: (res) => {
            if (res.confirm) {
              this.restoreDraft(draft)
            } else {
              // 只删当前这份
              this.deleteDraftById(draft.id)
            }
          },
        })
        return
      }
      // 多份:引导用户去草稿列表选择
      uni.showModal({
        title: `发现 ${all.length} 份草稿`,
        content: '是否前往草稿列表选择恢复?',
        confirmText: '查看草稿',
        cancelText: '稍后',
        success: (res) => {
          if (res.confirm) {
            uni.navigateTo({ url: '/pages/community/drafts' })
          }
        },
      })
    },

    /** 把草稿恢复到当前表单 */
    restoreDraft(draft) {
      this.content = draft.content || ''
      this.topic = draft.topic || ''
      this.location = draft.location || ''
      this.visibility = draft.visibility || 'public'
      this.draftId = draft.id || null
      this.draftExists = true
      this.draftSavedAt = draft.savedAt
      uni.showToast({ title: '已恢复草稿', icon: 'success' })
    },

    /** 删除当前草稿(单条) */
    clearDraft({ silent = false } = {}) {
      if (this.draftId) {
        this.deleteDraftById(this.draftId)
      }
      this.draftExists = false
      this.draftSavedAt = null
      this.draftId = null
      if (!silent) {
        uni.showToast({ title: '草稿已删除', icon: 'success' })
      }
    },

    /** 按 id 删除某份草稿 */
    deleteDraftById(id) {
      const all = this.readAllDrafts().filter((d) => d.id !== id)
      this.writeAllDrafts(all)
    },

    /** 格式化草稿年龄(几分钟前/几小时前/几天前) */
    formatDraftAge(ts) {
      if (!ts) return ''
      const diff = Date.now() - ts
      const min = Math.floor(diff / 60000)
      if (min < 1) return '刚刚'
      if (min < 60) return `${min}分钟前`
      const hr = Math.floor(min / 60)
      if (hr < 24) return `${hr}小时前`
      const day = Math.floor(hr / 24)
      if (day < 30) return `${day}天前`
      const d = new Date(ts)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },

    /**
     * 草稿按钮点击:
     * - 有未保存内容: 直接保存(更新当前 draftId)
     * - 无未保存内容: 跳到草稿列表页查看 / 切换 / 删除
     */
    onDraftTap() {
      if (this.hasUnsavedContent) {
        // 直接保存
        this.saveDraft({ silent: false })
        return
      }
      // 无未保存内容:查看草稿列表
      uni.navigateTo({ url: '/pages/community/drafts' })
    },

    /** 文本输入:截断到 2000 字符,与后端 @Size(max=2000) 对齐 */
    onContentInput(e) {
      const val = e.detail.value || ''
      if (val.length > 2000) {
        this.content = val.slice(0, 2000)
      }
      // 记录当前光标位置(emoji 插入用)
      if (typeof e.detail.cursor === 'number') {
        this.textareaCursor = e.detail.cursor
      } else {
        this.textareaCursor = val.length
      }
      // # 和 @ 联想互斥:先看 #,再看 @,后者会自动隐藏前者
      this.detectTopicSuggest(e)
      this.detectMentionSuggest(e)
      // 敏感词实时检测(防抖)
      this.scheduleSensitiveCheck(val)
    },

    /** textarea 失焦:延迟隐藏联想(给用户点击候选留时间) */
    onContentBlur() {
      // 150ms 后隐藏,避免点击候选时浮层先消失
      setTimeout(() => {
        this.hideTopicSuggest()
        this.hideMentionSuggest()
      }, 150)
    },

    /** textarea 获得焦点:收起 emoji 面板(让位给系统键盘) */
    onContentFocus() {
      if (this.emojiPanelVisible) {
        this.emojiPanelVisible = false
      }
    },

    /**
     * 键盘快捷键处理(H5 / Web 端)
     * 当前支持:
     * - ArrowDown / ArrowUp:在 # 或 @ 联想浮层中切换候选
     * - Enter:选中当前候选(浮层打开时)
     * - Escape:关闭 emoji 面板 / # 浮层 / @ 浮层
     * 非浮层打开时,Enter 走 textarea 默认行为(换行)
     */
    onComposerKeydown(e) {
      // Escape:关闭所有面板
      if (e.key === 'Escape' || e.keyCode === 27) {
        if (this.emojiPanelVisible) {
          this.emojiPanelVisible = false
          e.preventDefault()
        } else if (this.topicSuggest.visible) {
          this.hideTopicSuggest()
          e.preventDefault()
        } else if (this.mentionSuggest.visible) {
          this.hideMentionSuggest()
          e.preventDefault()
        }
        return
      }
      // 方向键 + Enter 仅在联想浮层打开时生效
      const isTopic = this.topicSuggest.visible
      const isMention = this.mentionSuggest.visible
      if (!isTopic && !isMention) return
      // Enter:选中当前高亮候选
      if (e.key === 'Enter' || e.keyCode === 13) {
        e.preventDefault()
        if (isTopic) {
          const list = this.topicSuggest.candidates || []
          if (this.topicSuggest.allowCreate) {
            // 含"创建话题"项,Enter 默认选中创建(更符合"输入未匹配 → 创建"心智)
            this.onCreateTopic()
            return
          }
          const idx = this.topicSuggest.activeIdx
          if (idx >= 0 && idx < list.length) {
            this.onPickTopicFromSuggest(list[idx])
          }
        } else if (isMention) {
          const list = this.mentionSuggest.candidates || []
          const idx = this.mentionSuggest.activeIdx
          if (idx >= 0 && idx < list.length) {
            this.onPickMentionFromSuggest(list[idx])
          }
        }
        return
      }
      // ArrowDown:下一个候选
      if (e.key === 'ArrowDown' || e.keyCode === 40) {
        e.preventDefault()
        const target = isTopic ? this.topicSuggest : this.mentionSuggest
        const len = (target.candidates || []).length
        if (len === 0) return
        target.activeIdx = (target.activeIdx + 1 + len) % len
        return
      }
      // ArrowUp:上一个候选
      if (e.key === 'ArrowUp' || e.keyCode === 38) {
        e.preventDefault()
        const target = isTopic ? this.topicSuggest : this.mentionSuggest
        const len = (target.candidates || []).length
        if (len === 0) return
        target.activeIdx = (target.activeIdx - 1 + len) % len
        return
      }
    },

    /**
     * 检测光标前的 #xxx 模式,如果匹配则展开联想浮层。
     * 规则:
     *   - 当前行/段内,# 必须是这一段的开头或前面是空白/换行
     *   - # 后跟随 0~20 个非空白、非 # 字符才视为查询中
     *   - 输入空格或换行 → 关闭浮层
     */
    detectTopicSuggest(e) {
      const val = e.detail.value || ''
      const cursor = (typeof e.detail.cursor === 'number')
        ? e.detail.cursor
        : val.length
      const before = val.slice(0, cursor)
      // 匹配最后一个 #[^#\s]{0,20}(光标紧跟其后)
      const m = before.match(/(^|[\s\n])#[^#\s]{0,20}$/)
      if (!m) {
        this.hideTopicSuggest()
        return
      }
      // # 的绝对索引 = match 末尾到 # 的距离
      const hashIdx = before.lastIndexOf('#')
      const query = before.slice(hashIdx + 1) // # 之后的字符
      this.topicSuggest.rangeStart = hashIdx
      this.topicSuggest.rangeEnd = cursor
      this.topicSuggest.query = query
      this.updateSuggestCandidates(query)
    },

    /**
     * 检测光标前的 @xxx 模式,如果匹配则展开用户联想浮层。
     * 规则:
     *   - @ 必须是这一段开头或前面是空白/换行
     *   - @ 后跟随 0~20 个非空白、非 @ 字符
     *   - 输入空格 / 换行 → 关闭浮层
     *   - 取消 # 联想(同一时刻只能有一个)
     */
    detectMentionSuggest(e) {
      const val = e.detail.value || ''
      const cursor = (typeof e.detail.cursor === 'number')
        ? e.detail.cursor
        : val.length
      const before = val.slice(0, cursor)
      // @ 后非空白字符 0~20 个
      const m = before.match(/(^|[\s\n])@[^@\s]{0,20}$/)
      if (!m) {
        this.hideMentionSuggest()
        return
      }
      const atIdx = before.lastIndexOf('@')
      const query = before.slice(atIdx + 1)
      this.mentionSuggest.rangeStart = atIdx
      this.mentionSuggest.rangeEnd = cursor
      this.mentionSuggest.query = query
      this.fetchMentionCandidates(query)
      // 互斥:# 联想不能同时开
      this.hideTopicSuggest()
    },

    /** 节流查询 @ 候选:debounce 300ms,避免每次按键都打接口 */
    mentionSearchTimer: null,
    async fetchMentionCandidates(query) {
      // 显示浮层 + 初始态
      this.mentionSuggest.visible = true
      this.mentionSuggest.activeIdx = 0
      if (this.mentionSearchTimer) clearTimeout(this.mentionSearchTimer)
      // 空查询时不立刻打接口,展示提示语
      if (!query) {
        this.mentionSuggest.candidates = []
        this.mentionSuggest.loading = false
        return
      }
      this.mentionSuggest.loading = true
      this.mentionSearchTimer = setTimeout(async () => {
        try {
          const res = await communityApi.searchCommunityUsers({
            keyword: query,
            page: 1,
            size: 10,
          })
          const records = (res && res.records) || []
          // 过滤掉自己(避免提及自己)
          const selfId = this.userStore && this.userStore.userInfo && this.userStore.userInfo.id
          this.mentionSuggest.candidates = selfId
            ? records.filter((u) => String(u.id) !== String(selfId))
            : records
          this.mentionSuggest.activeIdx = this.mentionSuggest.candidates.length > 0 ? 0 : -1
        } catch (e) {
          console.warn('[create] mention search failed:', e.message)
          this.mentionSuggest.candidates = []
        } finally {
          this.mentionSuggest.loading = false
        }
      }, 300)
    },

    /** 关闭 @ 联想浮层 */
    hideMentionSuggest() {
      this.mentionSuggest.visible = false
      this.mentionSuggest.query = ''
      this.mentionSuggest.candidates = []
      this.mentionSuggest.activeIdx = -1
      this.mentionSuggest.rangeStart = -1
      this.mentionSuggest.rangeEnd = -1
      this.mentionSuggest.loading = false
    },

    /**
     * 从 @ 联想列表里选中用户:替换 @xxx 为 @昵称 + 空格
     * (后端目前不解析 @ 提及为外键,这里仅做展示用)
     */
    onPickMentionFromSuggest(u) {
      if (!u || !u.nickname) return
      const start = this.mentionSuggest.rangeStart
      const end = this.mentionSuggest.rangeEnd
      if (start < 0 || end < 0) return
      const before = (this.content || '').slice(0, start)
      const after = (this.content || '').slice(end)
      const tag = `@${u.nickname} `
      this.content = before + tag + after
      this.textareaCursor = start + tag.length
      this.hideMentionSuggest()
    },

    /** @ 用户头像地址解析:与 composer__textarea 中图片路径相同处理 */
    resolveAvatarUrl(url) {
      if (!url) return ''
      if (url.startsWith('http://') || url.startsWith('https://')) return url
      return url
    },

    /** 根据 query 过滤已有话题列表;若需创建,标记 allowCreate */
    updateSuggestCandidates(query) {
      const all = this.topics || []
      const q = (query || '').toLowerCase()
      // 大小写不敏感前缀匹配;空 query 时展示前 8 条热门
      const list = q
        ? all.filter((t) => (t.name || '').toLowerCase().includes(q)).slice(0, 8)
        : all.slice(0, 8)
      // 已选中话题(=已发布的 topic)就不再允许重复插入
      const allowCreate = !!q && q.length > 0 && !list.some((t) => (t.name || '').toLowerCase() === q)
      this.topicSuggest.candidates = list
      this.topicSuggest.allowCreate = allowCreate
      this.topicSuggest.activeIdx = list.length > 0 ? 0 : -1
      this.topicSuggest.visible = true
    },

    /** 关闭联想浮层 */
    hideTopicSuggest() {
      this.topicSuggest.visible = false
      this.topicSuggest.query = ''
      this.topicSuggest.candidates = []
      this.topicSuggest.activeIdx = -1
      this.topicSuggest.rangeStart = -1
      this.topicSuggest.rangeEnd = -1
      this.topicSuggest.allowCreate = false
    },

    /**
     * 从联想列表里选中一个话题:替换 #xxx 为 #话题名 + 空格
     * 例如文本是 "我喜欢 #宠",选 "宠物日常" → "我喜欢 #宠物日常 "
     * 副作用:同时把 topic 字段设为该话题名(兼容现有"主话题"字段)
     */
    onPickTopicFromSuggest(t) {
      if (!t || !t.name) return
      const start = this.topicSuggest.rangeStart
      const end = this.topicSuggest.rangeEnd
      if (start < 0 || end < 0) return
      const before = this.content.slice(0, start)
      const after = this.content.slice(end)
      const tag = `#${t.name} `
      this.content = before + tag + after
      // 同步更新主话题字段(若 cell 未选,这样发布时也能带上 topic)
      this.topic = t.name
      this.hideTopicSuggest()
    },

    /** 点击「创建话题 #xxx」:当前后端不支持直接发布新话题,做轻提示 */
    onCreateTopic() {
      const q = (this.topicSuggest.query || '').trim()
      if (!q) return
      uni.showModal({
        title: '创建话题',
        content: `暂不支持自定义创建话题,是否仍按"${q}"发布?`,
        confirmText: '仍然发布',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) {
            // 将其视为普通话题字符串填入(后端会按字符串接收,topic 字段无外键约束)
            this.onPickTopicFromSuggest({ name: q })
          }
        },
      })
    },

    /** 加载话题列表(真实后端 /api/v1/community/topics) */
    async loadTopics() {
      this.topicsLoading = true
      try {
        const list = await communityApi.getCommunityTopics()
        this.topics = Array.isArray(list) ? list : []
      } catch (e) {
        // 接口失败不阻塞用户,允许不选话题直接发布
        this.topics = []
        console.warn('[create] loadTopics failed:', e.message)
      } finally {
        this.topicsLoading = false
      }
    },

    /** 选择图片(最多 9 张)。选完后立即上传,得到真实 URL。
     *  本地路径保留用于即时预览,uploadedImageUrls 用于最终提交。
     */
    async onPickImage() {
      const remain = 9 - this.images.length
      if (remain <= 0) {
        uni.showToast({ title: '最多 9 张图片', icon: 'none' })
        return
      }
      const res = await new Promise((resolve) => {
        // 显式 sourceType=['album']:Android 系统不再弹"相册/拍照"系统 ActionSheet,
        // 直接进入系统相册,避免部分 uni-app 版本把 sourceType 选择弹层渲染成
        // 原始函数名字面值(如 "uni.chooseImage.cancel")误导用户。
        uni.chooseImage({
          count: remain,
          sourceType: ['album'],
          success: (r) => resolve(r),
          // 用户在系统选择器点击"取消"时,errMsg 形如 "chooseImage:fail cancel",
          // 不要原样弹 toast(避免误导);直接 resolve(null) 走静默路径。
          fail: () => resolve(null),
        })
      })
      if (!res || !res.tempFilePaths || !res.tempFilePaths.length) return
      const localPaths = res.tempFilePaths.slice(0, remain)
      // 1. 先把本地路径塞进去,马上能预览
      this.images = this.images.concat(localPaths).slice(0, 9)
      // 2. 同步读取每张图片的宽高,填充 imageMeta(用于角标比例提示)
      const startIdx = this.imageMeta.length
      for (let i = 0; i < localPaths.length; i++) {
        const meta = await this.readImageMeta(localPaths[i])
        this.imageMeta.push(meta)
      }
      // 3. 异步上传得到真实 URL
      this.uploadingImages = true
      uni.showLoading({ title: `上传中 0/${localPaths.length}...`, mask: true })
      let successCount = 0
      for (let i = 0; i < localPaths.length; i++) {
        try {
          const result = await uploadApi.uploadImage(localPaths[i])
          if (result?.url) {
            this.uploadedImageUrls.push(result.url)
            successCount++
          }
        } catch (e) {
          console.error('[create] upload failed:', e.message)
          uni.showToast({ title: `第 ${i + 1} 张上传失败`, icon: 'none' })
        }
        uni.showLoading({ title: `上传中 ${i + 1}/${localPaths.length}...`, mask: true })
      }
      uni.hideLoading()
      this.uploadingImages = false
      if (successCount > 0) {
        uni.showToast({ title: `已上传 ${successCount} 张`, icon: 'success' })
      }
      // 4. 首图比例校验提示:如果首图不是 3:4,提示用户(仅一次,不打扰)
      this.maybeShowCoverRatioHint()
    },

    /**
     * 读取单张图片的宽高并返回元信息。
     * - uni.getImageInfo 在 H5 通过 Image 对象,在小程序/APP 通过原生 API。
     * - 失败时(网络图/权限)返回默认元信息,不影响主流程。
     */
    readImageMeta(localPath) {
      return new Promise((resolve) => {
        uni.getImageInfo({
          src: localPath,
          success: (info) => {
            const w = Number(info.width) || 0
            const h = Number(info.height) || 0
            const ratio = h > 0 ? w / h : 0
            const ratioLabel = this.formatRatio(w, h)
            // 3:4 = 0.75,1:1 = 1,4:3 = 1.333,9:16 = 0.5625
            // 最佳比例 3:4,允许 0.65~0.85(覆盖常见 4:5 / 3:4)
            const isIdealRatio = ratio > 0 && ratio >= 0.65 && ratio <= 0.85
            resolve({ width: w, height: h, ratioLabel, isIdealRatio, ratio })
          },
          fail: () => {
            resolve({ width: 0, height: 0, ratioLabel: '', isIdealRatio: false, ratio: 0 })
          },
        })
      })
    },

    /**
     * 把宽高压缩成比例字符串:1080x1440 → "3:4";1080x1080 → "1:1"
     * 算法:同时除以最大公约数(GCD),失败时回退到原始宽高字符串
     */
    formatRatio(w, h) {
      if (!w || !h) return ''
      const gcd = (a, b) => (b === 0 ? a : gcd(b, a % b))
      const g = gcd(w, h)
      const rw = Math.round(w / g)
      const rh = Math.round(h / g)
      // 限制最大显示数字(如 1920x1080 → 16:9 而不是 1920/1080/...)
      if (rw <= 21 && rh <= 21) return `${rw}:${rh}`
      return `${w}x${h}`
    },

    /**
     * 首图比例提示:仅在选中≥1张图时,如果首图比例不是 3:4 区间,
     * toast 提示用户调整(只展示一次,不阻塞)
     */
    maybeShowCoverRatioHint() {
      if (this.images.length === 0) return
      const first = this.imageMeta[0]
      if (!first || first.isIdealRatio || first.width === 0) return
      uni.showToast({
        title: `首图建议 3:4 比例,当前 ${first.ratioLabel}`,
        icon: 'none',
        duration: 2500,
      })
    },

    /** 删除某张图片(本地预览 + 已上传 URL + 元信息 同步移除) */
    removeImage(index) {
      this.images.splice(index, 1)
      this.imageMeta.splice(index, 1)
      // 已上传 URL 数组与本地预览数组下标保持一一对应
      if (index < this.uploadedImageUrls.length) {
        this.uploadedImageUrls.splice(index, 1)
      }
    },

    /**
     * 长按图片:进入拖拽模式(以该图片作为拖动源)
     * 长按时间 uni 默认约 350ms,这里不重复计时,直接进入拖拽态
     */
    onImageLongPress(idx) {
      if (this.draggingIndex === idx) return
      this.draggingIndex = idx
      uni.showToast({ title: '点击其他图片交换位置', icon: 'none', duration: 1800 })
    },

    /**
     * 拖拽模式下点击其他图片:与 draggingIndex 交换位置
     * 拖拽源上再点击 → 退出拖拽模式
     */
    onImageTapInDragMode(idx) {
      if (this.draggingIndex < 0) return
      if (this.draggingIndex === idx) {
        this.exitDragMode()
        return
      }
      this.swapImages(this.draggingIndex, idx)
      this.exitDragMode()
    },

    /**
     * 交换两个索引位置的图片 + 已上传 URL + 元信息
     * (注意顺序:避免 splice + 立即 push 时的下标错位)
     */
    swapImages(i, j) {
      if (i === j) return
      const swapArr = (arr) => {
        if (!Array.isArray(arr) || i >= arr.length || j >= arr.length) return
        const tmp = arr[i]
        // Vue2 不能直接 arr[i] = arr[j];使用 splice 保持响应式
        arr.splice(i, 1, arr[j])
        arr.splice(j, 1, tmp)
      }
      swapArr(this.images)
      swapArr(this.imageMeta)
      swapArr(this.uploadedImageUrls)
    },

    /** 退出拖拽模式(点击空白处时触发) */
    exitDragMode() {
      if (this.draggingIndex >= 0) {
        this.draggingIndex = -1
      }
    },

    /** 空操作(阻止事件冒泡/默认行为) */
    noop() {},

    /**
     * 防抖触发敏感词实时检测。
     * - 空文本清空命中列表(避免遗留)
     * - 防抖 600ms:用户连续输入时不会频繁打接口
     * - 接口失败静默:不打扰用户发布
     */
    scheduleSensitiveCheck(text) {
      if (this.sensitiveCheckTimer) {
        clearTimeout(this.sensitiveCheckTimer)
      }
      if (!text || !text.trim()) {
        this.sensitiveHits = []
        return
      }
      this.sensitiveCheckTimer = setTimeout(async () => {
        try {
          const hits = await communityApi.sensitiveCheck(text)
          this.sensitiveHits = Array.isArray(hits) ? hits : []
        } catch (e) {
          // 静默:不让敏感词检查失败影响用户发布
          console.warn('[create] sensitive check failed:', e.message)
        }
      }, 600)
    },

    /**
     * 选择视频:uni.chooseVideo。
     * 限制:≤60 秒(主流短视频时长)、≤200MB(后端 multipart 上限)
     * 选中后立即上传并显示进度;视频与图片互斥。
     */
    async onPickVideo() {
      // 已选视频:不允许再加(避免覆盖),提示先删除
      if (this.videoLocal || this.videoUrl) {
        uni.showToast({ title: '请先删除已选视频', icon: 'none' })
        return
      }
      // 收起键盘/面板
      this.emojiPanelVisible = false
      this.hideTopicSuggest()
      this.hideMentionSuggest()

      const res = await new Promise((resolve) => {
        // 视频从相册选;显式 ['album'] 绕过 Android 上 uni-app
        // chooseVideo sourceType 弹层的文案渲染 bug。
        uni.chooseVideo({
          sourceType: ['album'],
          maxDuration: 60, // 60 秒上限
          camera: 'back',
          success: (r) => resolve(r),
          fail: () => resolve(null),
        })
      })
      if (!res || !res.tempFilePath) return
      // 时长二次校验(部分平台不严格遵守 maxDuration)
      if (res.duration && res.duration > 60) {
        uni.showToast({ title: '视频时长不能超过 60 秒', icon: 'none' })
        return
      }
      // 大小校验(后端 200MB 上限;前端兜底 180MB 留余量)
      const sizeMB = (res.size || 0) / (1024 * 1024)
      if (sizeMB > 180) {
        uni.showToast({ title: `视频过大(${(sizeMB).toFixed(0)}MB),请压缩到 180MB 以内`, icon: 'none' })
        return
      }
      // 清理旧的图片(互斥)
      this.images = []
      this.uploadedImageUrls = []
      // 设置视频状态
      this.videoLocal = res.tempFilePath
      this.videoDuration = res.duration || 0
      this.videoSize = res.size || 0
      this.videoUrl = ''
      this.videoUploadProgress = 0
      // 上传到后端
      this.uploadingVideo = true
      uni.showLoading({ title: '上传视频中...', mask: true })
      try {
        const result = await uploadApi.uploadVideo(res.tempFilePath, (p) => {
          this.videoUploadProgress = p
        })
        if (result?.url) {
          this.videoUrl = result.url
          uni.hideLoading()
          uni.showToast({ title: '视频已上传', icon: 'success' })
        }
      } catch (e) {
        uni.hideLoading()
        console.error('[create] video upload failed:', e.message)
        uni.showToast({ title: e.message || '视频上传失败', icon: 'none' })
        // 上传失败:清掉本地预览,允许用户重选
        this.videoLocal = ''
        this.videoDuration = 0
        this.videoSize = 0
      } finally {
        this.uploadingVideo = false
        this.videoUploadProgress = 0
      }
    },

    /** 删除已选视频(本地预览 + 已上传 URL 一并清掉) */
    onRemoveVideo() {
      this.videoLocal = ''
      this.videoUrl = ''
      this.videoDuration = 0
      this.videoSize = 0
      this.videoUploadProgress = 0
      // 同步清掉封面相关状态
      this.coverCandidates = []
      this.selectedCoverIdx = -1
      this.coverUrl = ''
      this.coverPickerVisible = false
    },

    /** 视频元数据加载完成:记录时长,准备截帧 */
    onVideoLoadedMeta(e) {
      // 部分平台只通过 detail.duration 传,fallback 到 videoDuration
      if (e && e.detail && e.detail.duration) {
        this.videoDuration = e.detail.duration
      }
    },

    /** 视频 seek 完成事件:用于按需截帧 */
    onVideoSeeked() {
      // 这里不在事件回调里直接操作,统一由 onPickCover 调用 captureFrame 完成截图
    },

    /**
     * 点击"选择视频封面":弹出面板 + 生成 3 张候选帧。
     * 跨端实现策略:
     * - H5:用 video 元素 + canvas drawImage,逐帧 seek + 截图
     * - 小程序/APP:视频刚上传完成时,uni.chooseVideo 返回的 tempFilePath 可在某些版本直接截帧;
     *   兼容不到时退化为"使用视频首帧"提示用户手动截图后用图片选择器选图
     */
    async onPickCover() {
      // 收起其他面板
      this.emojiPanelVisible = false
      this.hideTopicSuggest()
      this.hideMentionSuggest()
      this.coverCandidates = []
      this.selectedCoverIdx = -1
      this.coverPickerVisible = true
      this.coverCapturing = true
      try {
        // 仅在 H5 平台(canvas 可用 + 可访问 video 元素)走自动截图
        // #ifdef H5
        const candidates = await this.captureFramesH5()
        this.coverCandidates = candidates
        if (candidates.length === 0) {
          uni.showToast({ title: '自动截帧失败,请手动上传图片作为封面', icon: 'none', duration: 3000 })
        }
        // #endif
        // #ifndef H5
        // 小程序/APP 自动截帧复杂,先给出友好提示,留接口扩展
        uni.showToast({ title: '小程序暂不支持自动截帧,请上传图片或选择首帧', icon: 'none', duration: 3000 })
        // #endif
      } catch (e) {
        console.warn('[create] capture cover failed:', e.message)
      } finally {
        this.coverCapturing = false
      }
    },

    /**
     * H5 平台用 video + canvas 截 3 帧。
     * 取视频时长 25% / 50% / 75% 处的画面,统一缩放到 720 宽,jpeg 0.85 压缩。
     */
    captureFramesH5() {
      return new Promise((resolve) => {
        const video = document.getElementById('community-video-player')
        if (!video || !video.duration || video.duration === Infinity) {
          resolve([])
          return
        }
        const dur = video.duration
        const ratios = [0.25, 0.5, 0.75]
        // 用 canvas 离线截帧
        const canvas = document.createElement('canvas')
        const ctx = canvas.getContext('2d')
        const TARGET_W = 720
        const candidates = []
        let i = 0
        const seekAndCapture = () => {
          if (i >= ratios.length) {
            resolve(candidates)
            return
          }
          const t = Math.max(0.1, ratios[i] * dur)
          video.currentTime = t
          // 监听当前帧已绘制完成
          const onSeeked = () => {
            video.removeEventListener('seeked', onSeeked)
            try {
              // 缩放:保持宽高比
              const vw = video.videoWidth || 1280
              const vh = video.videoHeight || 720
              const ratio = TARGET_W / vw
              canvas.width = TARGET_W
              canvas.height = Math.round(vh * ratio)
              ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
              const dataUrl = canvas.toDataURL('image/jpeg', 0.85)
              candidates.push({ time: t, dataUrl })
            } catch (e) {
              console.warn('[create] frame capture error', e)
            }
            i++
            // 给浏览器一点喘息时间再截下一帧
            setTimeout(seekAndCapture, 100)
          }
          video.addEventListener('seeked', onSeeked)
        }
        seekAndCapture()
      })
    },

    /** 选中某个候选帧(高亮) */
    onSelectCover(idx) {
      this.selectedCoverIdx = idx
    },

    /** 关闭封面选择面板 */
    closeCoverPicker() {
      if (this.uploadingCover) return
      this.coverPickerVisible = false
    },

    /** 确认选择:把 dataURL 转 File,走 uploadApi 上传,得到 URL 存到 coverUrl */
    async onConfirmCover() {
      if (this.selectedCoverIdx < 0) {
        uni.showToast({ title: '请先选择一帧', icon: 'none' })
        return
      }
      const item = this.coverCandidates[this.selectedCoverIdx]
      if (!item) return
      this.uploadingCover = true
      try {
        const filePath = await this.dataUrlToFilePath(item.dataUrl, `cover_${Date.now()}.jpg`)
        const result = await uploadApi.uploadImage(filePath)
        if (result?.url) {
          this.coverUrl = result.url
          uni.showToast({ title: '封面已设置', icon: 'success' })
          this.coverPickerVisible = false
        }
      } catch (e) {
        console.error('[create] cover upload failed:', e.message)
        uni.showToast({ title: e.message || '封面上传失败', icon: 'none' })
      } finally {
        this.uploadingCover = false
      }
    },

    /**
     * 把 dataURL(base64) 转成可上传的本地文件路径。
     * - H5:转 Blob 再用 URL.createObjectURL + 转 File,再走 uni.uploadFile
     * - 小程序/APP:dataURL 直接可作为 filePath(H5 写法返回临时路径)
     */
    async dataUrlToFilePath(dataUrl, filename) {
      // #ifdef H5
      const res = await fetch(dataUrl)
      const blob = await res.blob()
      const file = new File([blob], filename, { type: 'image/jpeg' })
      // uni.uploadFile 在 H5 需要 File 对象 → 这里借助 Blob URL
      // 直接传 File 给 uploadApi.uploadImage 即可(uni.uploadFile H5 接受 File)
      return file
      // #endif
      // #ifndef H5
      // 小程序/APP:dataURL 需要先写到临时文件,这里返回 dataURL 字符串,
      // 适配器层 uni.uploadFile 在某些平台也接受 base64,但更稳妥是先写临时文件
      // 此处简化处理,如有需要再扩展
      return dataUrl
      // #endif
    },

    /** 视频元素播放错误(如编码不支持),提示用户 */
    onVideoError() {
      uni.showToast({ title: '视频预览失败,但不影响发布', icon: 'none' })
    },

    /** 格式化时长:62 秒 → "01:02" */
    formatDuration(seconds) {
      const s = Math.max(0, Math.floor(Number(seconds) || 0))
      const mm = Math.floor(s / 60)
      const ss = s % 60
      return `${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
    },

    /** 格式化文件大小:10485760 → "10.0MB" */
    formatSize(bytes) {
      const b = Number(bytes) || 0
      if (b < 1024) return `${b}B`
      if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)}KB`
      return `${(b / (1024 * 1024)).toFixed(1)}MB`
    },

    /**
     * 打开定时发布面板,并初始化 picker 范围(未来 7 天可选)。
     */
    onPickScheduled() {
      // 收起其他面板
      this.emojiPanelVisible = false
      this.hideTopicSuggest()
      this.hideMentionSuggest()
      this.scheduledPickerVisible = true
      // 初始化 picker 范围(日期 = 未来 7 天,小时 0-23,分钟 0-59)
      const dates = []
      const now = new Date()
      for (let i = 0; i < 7; i++) {
        const d = new Date(now.getTime() + i * 86400000)
        dates.push(this.formatDate(d))
      }
      const hours = []
      for (let h = 0; h < 24; h++) hours.push(String(h).padStart(2, '0'))
      const minutes = []
      for (let m = 0; m < 60; m += 5) minutes.push(String(m).padStart(2, '0'))
      this.customTimeRange = [dates, hours, minutes]
      // 默认值:今天 +1 小时(避开整点冲突)
      this.customTimeValue = [
        0,
        Math.min(23, now.getHours() + 1),
        0,
      ]
      this.customTimeDisplay = ''
    },

    /** 关闭面板 */
    closeScheduledPicker() {
      this.scheduledPickerVisible = false
    },

    /** 快捷选项:相对当前时间的分钟数 */
    onPickScheduledShortcut(minutes) {
      const t = new Date(Date.now() + minutes * 60000)
      // 转 ISO 字符串(本地时间,后端会按 LocalDateTime 处理)
      const pad = (n) => String(n).padStart(2, '0')
      const iso = `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}T${pad(t.getHours())}:${pad(t.getMinutes())}:00`
      this.scheduledAt = iso
      this.customTimeDisplay = `${this.formatDate(t)} ${pad(t.getHours())}:${pad(t.getMinutes())}`
    },

    /** 自定义 picker 选完 */
    onCustomTimeChange(e) {
      const [di, hi, mi] = e.detail.value
      const dates = this.customTimeRange[0]
      const hours = this.customTimeRange[1]
      const minutes = this.customTimeRange[2]
      const dateStr = dates[di]
      const hourStr = hours[hi]
      const minuteStr = minutes[mi]
      // 拼 ISO
      const t = new Date(`${dateStr}T${hourStr}:${minuteStr}:00`)
      const pad = (n) => String(n).padStart(2, '0')
      const iso = `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}T${pad(t.getHours())}:${pad(t.getMinutes())}:00`
      this.scheduledAt = iso
      this.customTimeDisplay = `${dateStr} ${hourStr}:${minuteStr}`
    },

    /** 确认定时发布 */
    onConfirmScheduled() {
      if (!this.scheduledAt) {
        uni.showToast({ title: '请选择发布时间', icon: 'none' })
        return
      }
      // 必须未来时间
      const t = new Date(this.scheduledAt)
      if (t.getTime() <= Date.now()) {
        uni.showToast({ title: '请选择未来的时间', icon: 'none' })
        return
      }
      this.scheduledPickerVisible = false
      uni.showToast({
        title: `已设置定时:${this.formatScheduled(this.scheduledAt)}`,
        icon: 'none',
      })
    },

    /** 清除定时发布 */
    onClearScheduled() {
      this.scheduledAt = ''
      this.customTimeDisplay = ''
    },

    /** 格式化已选时间:显示 "今天 18:30" 或 "明天 09:00" */
    formatScheduled(iso) {
      if (!iso) return ''
      const t = new Date(iso)
      const now = new Date()
      const dayDiff = Math.floor((t.getTime() - now.setHours(0, 0, 0, 0)) / 86400000)
      const pad = (n) => String(n).padStart(2, '0')
      const hm = `${pad(t.getHours())}:${pad(t.getMinutes())}`
      if (dayDiff <= 0) return `今天 ${hm}`
      if (dayDiff === 1) return `明天 ${hm}`
      return `${t.getMonth() + 1}/${t.getDate()} ${hm}`
    },

    /** 格式化"未来时间"(用于快捷选项预览) */
    formatFutureTime(minutes) {
      const t = new Date(Date.now() + minutes * 60000)
      const pad = (n) => String(n).padStart(2, '0')
      return `${this.formatDate(t)} ${pad(t.getHours())}:${pad(t.getMinutes())}`
    },

    /** 格式化为 YYYY-MM-DD */
    formatDate(d) {
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    },

    /** 打开话题选择器 */
    onPickTopic() {
      // 列表为空且未在加载,触发一次兜底请求
      if (!this.topics.length && !this.topicsLoading) {
        this.loadTopics()
      }
      this.topicPickerVisible = true
    },

    /** 关闭话题选择器 */
    closeTopicPicker() {
      this.topicPickerVisible = false
    },

    /** 选中话题 */
    selectTopic(t) {
      // 后端约束 topic ≤32 字符
      if (t.name && t.name.length > 32) {
        this.topic = t.name.slice(0, 32)
      } else {
        this.topic = t.name || ''
      }
      this.closeTopicPicker()
    },

    /** 选择位置:弹出 POI 选择面板 */
    onPickLocation() {
      // 收起 emoji 面板避免冲突
      this.emojiPanelVisible = false
      this.hideTopicSuggest()
      this.hideMentionSuggest()
      this.locationPickerVisible = true
      // 重置搜索词
      this.locationKeyword = ''
    },

    /** 关闭位置面板 */
    closeLocationPicker() {
      this.locationPickerVisible = false
      this.locationKeyword = ''
    },

    /** 搜索关键词变化(无需处理逻辑,v-model 自动更新,filteredPois computed 自动重算) */
    onLocationKeywordChange() {
      // 留空:computed 自动响应
    },

    /**
     * 选择一个 POI:
     * - selectedLocation:存结构化对象(name/address/lat/lon)
     * - location(cell 显示):"POI 名称 + 简短地址"
     * 这样后端 createPost 拿到的是字符串(后端只存 topic 字段,location 仅前端展示)
     */
    onPickPoi(p) {
      this.selectedLocation = { ...p }
      this.location = `${p.name} · ${p.address}`
      this.locationPickerVisible = false
      uni.showToast({ title: '位置已选', icon: 'success' })
    },

    /**
     * 使用当前定位:请求 uni.getLocation,成功后把坐标写入 location
     * (后端未支持位置字段存储,这里仅前端展示)
     */
    onUseCurrentLocation() {
      // H5 / 浏览器:用 navigator.geolocation
      // 微信小程序 / APP:用 uni.authorize + uni.getLocation
      // 这里采用通用路径,优先 uni.authorize 失败回退到 H5 geolocation
      this.locationLoading = true
      const onSuccess = (lat, lon, accuracy) => {
        // 取 lat/lon 保留 4 位小数;同时尝试逆地理(浏览器无 key,简单显示)
        this.selectedLocation = {
          name: '当前位置',
          address: `${lat.toFixed(4)}, ${lon.toFixed(4)}`,
          lat,
          lon,
        }
        this.location = `当前位置 · ${lat.toFixed(4)}, ${lon.toFixed(4)}`
        this.locationPickerVisible = false
        this.locationLoading = false
        uni.showToast({ title: '已获取当前位置', icon: 'success' })
      }
      const onFail = (msg) => {
        this.locationLoading = false
        uni.showToast({ title: msg || '获取位置失败', icon: 'none' })
      }
      // 尝试 uni 路径(APP / 小程序)
      if (typeof uni !== 'undefined' && uni.getLocation) {
        uni.authorize({
          scope: 'scope.userLocation',
          success: () => {
            uni.getLocation({
              type: 'wgs84',
              success: (res) => onSuccess(res.latitude, res.longitude, res.accuracy),
              fail: () => onFail('获取位置失败'),
            })
          },
          fail: () => {
            // 浏览器环境:用 navigator.geolocation 兜底
            if (navigator && navigator.geolocation) {
              navigator.geolocation.getCurrentPosition(
                (pos) => onSuccess(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy),
                () => onFail('浏览器拒绝定位或不可用'),
                { enableHighAccuracy: false, timeout: 8000 }
              )
            } else {
              onFail('当前环境不支持定位')
            }
          },
        })
      } else if (navigator && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (pos) => onSuccess(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy),
          () => onFail('浏览器拒绝定位或不可用'),
          { enableHighAccuracy: false, timeout: 8000 }
        )
      } else {
        onFail('当前环境不支持定位')
      }
    },

    /** 清除位置 */
    onClearLocation() {
      this.selectedLocation = null
      this.location = ''
      this.locationPickerVisible = false
    },

    /** 选择可见性:当前后端未支持,仅前端占位 */
    onPickVisibility() {
      uni.showActionSheet({
        itemList: ['公开', '仅好友', '仅自己'],
        success: (res) => {
          const map = ['public', 'friends', 'private']
          this.visibility = map[res.tapIndex]
        },
      })
    },

    /**
     * Emoji 面板:7 大分类覆盖常用场景(表情/动物/食物/活动/旅行/物品/符号)
     * 数据放在 computed-less data 字段中(分类固定,不会动态变),节省重渲染
     */
    emojiCategories: [
      {
        name: '表情', icon: '😀',
        list: ['😀','😁','😂','🤣','😃','😄','😅','😆','😉','😊','😋','😎','😍','😘','🥰','😗','😙','😚','🙂','🤗','🤩','🤔','🤨','😐','😑','😶','🙄','😏','😣','😥','😮','🤐','😯','😪','😫','🥱','😴','😌','😛','😜','😝','🤤','😒','😓','😔','😕','🙃','🤑','😲','☹️','🙁','😖','😞','😟','😤','😢','😭','😦','😧','😨','😩','🤯','😬','😰','😱','🥵','🥶','😳','🤪','😵','🥴','😠','😡','🤬','😷','🤒','🤕','🤢','🤮','🥳','🥺','🤠','🤡','🤥','🤫','🤭','🧐','🤓','😈','👿'],
      },
      {
        name: '爱心', icon: '❤️',
        list: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❣️','💕','💞','💓','💗','💖','💘','💝','💟','♥️','💌','💋','💯','💢','💥','💫','💦','💨','🕳️','💣','💬','👁️‍🗨️','🗨️','🗯️','💭','💤'],
      },
      {
        name: '动物', icon: '🐾',
        list: ['🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🐔','🐧','🐦','🐤','🦆','🦅','🦉','🦇','🐺','🐗','🐴','🦄','🐝','🐛','🦋','🐌','🐞','🐜','🪰','🪲','🐢','🐍','🦎','🦖','🦕','🐙','🦑','🦐','🦞','🦀','🐡','🐠','🐟','🐬','🐳','🐋','🦈','🐊','🐅','🐆','🦓','🦍','🦧','🐘','🦣','🦛','🦏','🐪','🐫','🦒','🦘','🐃','🐂','🐄','🐎','🐖','🐏','🐑','🦙','🐐','🦌','🐕','🐩','🦮','🐕‍🦺','🐈','🐈‍⬛','🪶','🐓','🦃','🦚','🦜','🦢','🦩','🕊️','🐇','🦝','🦨','🦡','🦫','🦦','🦥','🐁','🐀','🐿️','🦔','🐲','🐉','🦖','🦕','🐳','🐋','🐬','🐟','🐠','🐡','🦈','🐊','🐅','🐆','🦓','🦍','🐘','🐁','🐀'],
      },
      {
        name: '食物', icon: '🍔',
        list: ['🍏','🍎','🍐','🍊','🍋','🍌','🍉','🍇','🍓','🫐','🍈','🍒','🍑','🥭','🍍','🥥','🥝','🍅','🍆','🥑','🥦','🥬','🥒','🌶','🫑','🌽','🥕','🫒','🧄','🧅','🥔','🍠','🥐','🥯','🍞','🥖','🥨','🧀','🥚','🍳','🧈','🥞','🧇','🥓','🥩','🍗','🍖','🦴','🌭','🍔','🍟','🍕','🥪','🥙','🧆','🌮','🌯','🫔','🥗','🥘','🫕','🥫','🍝','🍜','🍲','🍛','🍣','🍱','🥟','🦪','🍤','🍙','🍚','🍘','🍥','🥠','🥮','🍢','🍡','🍧','🍨','🍦','🥧','🧁','🍰','🎂','🍮','🍭','🍬','🍫','🍿','🍩','🍪'],
      },
      {
        name: '活动', icon: '⚽',
        list: ['⚽','🏀','🏈','⚾','🥎','🎾','🏐','🏉','🥏','🎱','🪀','🏓','🏸','🏒','🏑','🥍','🏏','🪃','🥅','⛳','🪁','🏹','🎣','🤿','🥊','🥋','🎽','🛹','🛼','🛷','⛸','🥌','🎿','⛷','🏂','🪂','🏋️','🤼','🤸','⛹️','🤺','🤾','🏌️','🏇','🧘','🏄','🏊','🤽','🚣','🧗','🚵','🚴','🏆','🥇','🥈','🥉','🏅','🎖️','🏵️','🎗️','🎫','🎟️','🎪','🤹','🎭','🩰','🎨','🎬','🎤','🎧','🎼','🎹','🥁','🪘','🎷','🎺','🎸','🪕','🎻','🎲','♟️','🎯','🎳','🎮','🎰','🧩'],
      },
      {
        name: '旅行', icon: '🚗',
        list: ['🚗','🚕','🚙','🚌','🚎','🏎','🚓','🚑','🚒','🚐','🛻','🚚','🚛','🚜','🛴','🚲','🛵','🏍','🛺','🚨','🚔','🚍','🚘','🚖','🚡','🚠','🚟','🚃','🚋','🚞','🚝','🚄','🚅','🚈','🚂','🚆','🚇','🚊','🚉','✈️','🛫','🛬','🛩️','💺','🛰️','🚀','🛸','🚁','🛶','⛵','🚤','🛥️','🛳️','⛴️','🚢','⚓','🚧','⛽','🚏','🚦','🚥','🗺️','🗿','🗽','🗼','🏰','🏯','🏟️','🎡','🎢','🎠','⛲','🏖️','🏝️','🏜️','🌋','⛰️','🏔️','🗻','🏕️','⛺','🏠','🏡','🏘️','🏚️','🏗️','🏭','🏢','🏬','🏣','🏤','🏥','🏦','🏨','🏪','🏫','🏩','💒','🏛️','⛪','🕌','🕍','🛕','🕋','⛩️'],
      },
      {
        name: '物品', icon: '🏠',
        list: ['⌚','📱','📲','💻','⌨️','🖥','🖨','🖱','🖲','🕹','🗜','💽','💾','💿','📀','📼','📷','📸','📹','🎥','📽','🎞','📞','☎️','📟','📠','📺','📻','🎙','🎚','🎛','🧭','⏱','⏲','⏰','🕰','⌛','⏳','📡','🔋','🔌','💡','🔦','🕯','🪔','🧯','🛢','💸','💵','💴','💶','💷','💰','💳','💎','⚖️','🪜','🧰','🪛','🔧','🔨','⚒','🛠','⛏','🪚','🔩','⚙️','🪤','🧱','⛓','🧲','🔫','💣','🧨','🪓','🔪','🗡','⚔️','🛡','🚬','⚰️','🪦','⚱️','🏺','🔮','📿','🧿','💈','⚗️','🔭','🔬','🕳','🩹','🩺','💊','💉','🩸','🧬','🦠','🧫','🩻','🎒','👜','👝','🛍','🎓','👔','👕','👖','🧦','🧤','🧣','🧥','🥼','🦺','👗','👘','🥻','🩱','🩳','🩲','👙','👚','👛','🪖','🎩','🧢','👒','🎓','⛑','💄','💋','👑','👒','🎒','🧳','🌂','☂️','💼','🛅','🛌','🛏','🛋','🚽','🚾','🧻','🧼','🪒','🧽','🧯','🛒','🚮','🧺'],
      },
    ],

    /**
     * Emoji 搜索关键词字典(中英文 + 拼音首字母)
     * key: 搜索词(用户输入),value: emoji 列表
     * 例: '笑' → ['😀','😁','😂','🤣','😃',...] ; 'xiao' → ['😀','😁',...] ; 'x' → ['笑/心/熊/...]
     * 这里精选常用关键词,够 90% 用户场景
     */
    emojiKeywords: {
      '笑': ['😀','😁','😂','🤣','😃','😄','😅','😆','😉','😊','😋','😎','😏','🙂','🤗','🤭','🤪','😝','😜','😛','🤤'],
      '哭': ['😢','😭','😿','🥺'],
      '爱': ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','💕','💖','💗','💘','💝','💞','💓','😻','🥰','😍','😘','😗'],
      '心': ['❤️','💔','💖','💗','💘','💝','💞','💓','💕','🖤','🤍','💟','♥️','💌'],
      '花': ['🌸','🌺','🌻','🌷','🌹','🌼','💐','🏵️'],
      '狗': ['🐶','🐕','🐩','🦮','🐕‍🦺'],
      '猫': ['🐱','🐈','🐈‍⬛','😺','😸','😹','😻','😼','😽','🙀'],
      '食物': ['🍎','🍌','🍔','🍕','🍣','🍜','🍱','🍝','🍲','🍳','🍰','🎂','🍦','🍩','🍪','🍫','🍬','🍭','🍮','🍯','🍇','🍓','🍑','🥝','🍍','🥥','🍒','🍊','🍋','🍌','🍐','🍏'],
      '喝': ['☕','🍵','🍶','🍾','🍷','🍸','🍹','🍺','🥂','🥃','🥤'],
      '运动': ['⚽','🏀','🏈','⚾','🎾','🏐','🏉','🥏','🎱','🏓','🏸','⛳','🏹','🎣','🏊','🏃','🚴','🏋️','🤸','⛹️','🤾','🏌️','🏇','🧗','🧘','🥊','🥋','🎽','🎿','⛷','🏂','🛹'],
      '车': ['🚗','🚕','🚙','🚌','🚎','🏎','🚓','🚑','🚒','🚐','🛻','🚚','🚛','🚜','🏍','🛵','🚲','🛴','🛺','🚜','🚦','🚥'],
      '家': ['🏠','🏡','🏘️','🏚️','🏢','🏣','🏤','🏥','🏦','🏨','🏩','🏪','🏫','🏬','🏭','🏯','🏰','💒'],
      '工作': ['💼','📁','📂','📄','📊','📈','📉','📋','📌','📍','📎','📏','📐','🖇️','📆','📅','📇','🗂️','🗃️','🗄️'],
      '钱': ['💰','💵','💴','💶','💷','💸','💳','💎','🏦','💱'],
      '点赞': ['👍','👎','👏','🙌','👐','🤝','🙏'],
      '手': ['👌','✌️','🤞','🤟','🤘','🤙','👈','👉','👆','👇','☝️','✋','🤚','🖐️','🖖','👋','🤛','🤜','✊','👊','🫶','🫱','🫲','🫳','🫴','🫰'],
      '火': ['🔥','💥','✨','🎇','🎆','🌟','⭐','🌠'],
      '水': ['💧','💦','🌊','🚰','🛁','🚿','💦'],
      '生日': ['🎂','🎉','🎊','🎈','🎁','🎀','🍰'],
      '爪': ['🐾','🐱','🐶','🐯','🐻','🦁'],
      '好': ['👍','👌','✅','💯','🙆','🙆‍♂️','🙆‍♀️'],
      '哭脸': ['😢','😭','😿','🥺','😞','😔','😟','😕','🙁','☹️'],
      'no': ['🙅','🙅‍♂️','🙅‍♀️','❌','⛔','🚫','👎'],
      'yes': ['🙆','🙆‍♂️','🙆‍♀️','✅','👍','✔️','🆗','👌'],
      '歌': ['🎤','🎵','🎶','🎸','🎹','🎺','🎻','🎷','🥁','🎧','📻'],
      'gift': ['🎁','🎀','🎊','🎉','🎈','🏆','🥇','🏅','🎖️','💝'],
      'star': ['⭐','🌟','✨','💫','🌠','🎇','🎆'],
      'rainbow': ['🌈','🌤️','⛅','🌥️','☁️','🌦️','🌧️','⛈️','🌩️','🌨️','❄️','☃️','⛄','🌬️','💨','💧','💦','☔','☂️'],
      'happy': ['😀','😁','😂','🤣','😃','😄','😅','😆','😉','😊','😋','😎','😏','🙂','🤗','😺','😸','😹','😻'],
      'sad': ['😢','😭','😿','🥺','😞','😔','😟','😕','🙁','☹️','😦','😧','😨','😩','🤯','😬','😰','😱','🥵','🥶','😳','🤪','😵','🥴'],
      'cool': ['😎','🤩','🤘','🤟','🤞','✌️'],
      'fire': ['🔥','💥','✨','🎇','🎆','🌟','⭐','🌠','🚒','🧯'],
      'ok': ['👌','🙆','🙆‍♂️','🙆‍♀️','✅','👍','✔️','🆗','🟢'],
      'angry': ['😠','😡','🤬','😤','😾','💢'],
    },

    /**
     * 计算属性:根据 emojiKeyword 过滤所有 emoji
     * - 命中 emojiKeywords 字典:精确返回对应 emoji
     * - 未命中字典:对所有 emoji 做拼音首字母包含匹配(简易版)
     */
    emojiSearchResults() {
      const q = (this.emojiKeyword || '').trim().toLowerCase()
      if (!q) return []
      // 1. 完全匹配字典
      if (this.emojiKeywords[q]) {
        return this.emojiKeywords[q]
      }
      // 2. 中文/英文全名子串匹配(对所有 emoji 分类聚合)
      const all = (this.emojiCategories || []).flatMap((c) => c.list || [])
      // 由于 emoji 没有内置文本名,只能基于字典 + 分类名前缀匹配
      // 字典 key 子串匹配
      const dictMatches = []
      for (const [k, v] of Object.entries(this.emojiKeywords || {})) {
        if (k.includes(q) || (q.length >= 2 && k.startsWith(q))) {
          dictMatches.push(...v)
        }
      }
      // 3. 去重(保留第一次出现顺序)
      const seen = new Set()
      const out = []
      for (const e of [...dictMatches, ...all]) {
        if (!seen.has(e)) {
          seen.add(e)
          out.push(e)
        }
        if (out.length >= 40) break // 限制最多 40 个
      }
      return out
    },

    /**
     * 切换 emoji 面板:打开时同步 textarea 当前光标位置,关闭时收起面板
     */
    toggleEmojiPanel() {
      this.emojiPanelVisible = !this.emojiPanelVisible
      if (this.emojiPanelVisible) {
        // 打开面板时记录光标(用户可能在 v-model 中没移动过)
        this.textareaCursor = (this.content || '').length
        // 打开时清空搜索词,展示默认分类
        this.emojiKeyword = ''
      }
    },

    /**
     * 在 textarea 当前光标处插入 emoji。
     * 注意:v-model 直接赋值会让光标跳到末尾——这是已知 trade-off,
     * 大多数 emoji 面板(包括微博/微信)也是这种行为,用户可点击输入框重定位光标。
     */
    insertEmojiAtCursor(e) {
      const cur = this.textareaCursor || (this.content || '').length
      const before = (this.content || '').slice(0, cur)
      const after = (this.content || '').slice(cur)
      this.content = before + e + after
      this.textareaCursor = cur + e.length
    },

    /** emoji 面板 ⌫ 按钮:删除光标前一个字符(等同键盘 Backspace) */
    onEmojiBackspace() {
      const cur = this.textareaCursor || (this.content || '').length
      if (cur <= 0) return
      // 处理 emoji 是 surrogate pair(2 个 UTF-16 code unit)的情况,先去掉整字符
      const before = (this.content || '').slice(0, cur)
      let removeLen = 1
      // surrogate pair 起始位:charCodeAt 落在 0xD800~0xDBFF
      if (cur >= 2 && before.charCodeAt(cur - 1) >= 0xDC00 && before.charCodeAt(cur - 2) >= 0xD800) {
        removeLen = 2
      }
      this.content = before.slice(0, cur - removeLen) + (this.content || '').slice(cur)
      this.textareaCursor = cur - removeLen
    },

    /** 工具栏:@好友 —— 在 textarea 末尾插入 "@" 并展开联想浮层 */
    onMention() {
      // 收起 emoji 面板避免冲突
      this.emojiPanelVisible = false
      const cur = this.textareaCursor || (this.content || '').length
      const before = (this.content || '').slice(0, cur)
      const after = (this.content || '').slice(cur)
      // 若光标前不是空白/换行/开头,补一个空格(避免和已有文字粘连)
      const needSpace = before.length > 0 && !/[\s\n]$/.test(before)
      const insertText = (needSpace ? ' ' : '') + '@'
      this.content = before + insertText + after
      this.textareaCursor = cur + insertText.length
      // 触发联想检测:会展开浮层
      this.detectMentionSuggest({
        detail: { value: this.content, cursor: this.textareaCursor },
      })
      // 主动聚焦 textarea(用户已可能在工具栏上失焦)
      // #ifndef H5
      // uni.hideKeyboard()
      // #endif
    },

    /** 工具栏:#话题(占位,实际通过右侧 cell 选话题) */
    onInsertHash() {
      this.onPickTopic()
    },

    /** 数字格式化(1200 -> 1.2k) */
    formatCount(n) {
      if (n == null) return '0'
      if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
      if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
      return String(n)
    },

    /** 发布:调用真实后端 /api/v1/community/posts */
    async onPublish() {
      if (!this.userStore.isLoggedIn) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        return
      }
      if (!this.canPublish) {
        uni.showToast({ title: '请输入内容或上传图片', icon: 'none' })
        return
      }
      if (this.submitting) return
      if (this.uploadingImages) {
        uni.showToast({ title: '图片正在上传,请稍候', icon: 'none' })
        return
      }
      if (this.uploadingVideo) {
        uni.showToast({ title: '视频正在上传,请稍候', icon: 'none' })
        return
      }

      // 视频帖特殊处理:有视频时不强求文字,但视频 URL 必须已上传成功
      const hasVideo = !!(this.videoLocal || this.videoUrl)
      const videoUrl = this.videoUrl
      if (hasVideo && !videoUrl) {
        uni.showToast({ title: '视频上传失败,请重新选择', icon: 'none' })
        return
      }

      // 防御性校验:与后端 DTO 约束保持一致
      const trimmed = (this.content || '').trim()
      // 视频帖允许纯视频发布(图文帖仍要求有内容)
      if (!trimmed && !hasVideo && this.images.length === 0) {
        uni.showToast({ title: '帖子内容不能为空', icon: 'none' })
        return
      }
      if (trimmed.length > 2000) {
        uni.showToast({ title: '内容不能超过 2000 字', icon: 'none' })
        return
      }
      if (this.topic && this.topic.length > 32) {
        uni.showToast({ title: '话题不能超过 32 字符', icon: 'none' })
        return
      }
      if (this.images.length > 9) {
        uni.showToast({ title: '图片不能超过 9 张', icon: 'none' })
        return
      }

      // 真实图片 URL(已上传过的)。如果用户选了图但上传失败,这里不会包含失败的那张,
      // 这种情况下要求用户删除该图重新选择,避免给后端传本地路径
      const imageUrls = this.uploadedImageUrls
      if (this.images.length > 0 && imageUrls.length === 0) {
        uni.showToast({ title: '图片上传失败,请重新选择', icon: 'none' })
        return
      }

      this.submitting = true
      uni.showLoading({ title: this.scheduledAt ? '设置定时中...' : '发布中...', mask: true })
      try {
        const result = await communityApi.createPost(
          trimmed,
          imageUrls,
          hasVideo ? videoUrl : null,
          hasVideo && this.coverUrl ? this.coverUrl : null,
          this.topic || null,
          this.scheduledAt || null
        )
        uni.hideLoading()
        uni.showToast({
          title: this.scheduledAt ? '已设置定时发布' : '发布成功',
          icon: 'success',
        })
        // 发布成功后清理草稿(避免下次进入还提示恢复)
        this.clearDraft({ silent: true })
        // 标记为已发布,避免 onUnload 兜底保存旧草稿
        this._draftDiscarded = true
        // 发布成功后通知上一页刷新列表
        const pages = getCurrentPages()
        if (pages.length >= 2) {
          const prevPage = pages[pages.length - 2]
          if (prevPage && typeof prevPage.$vm?.loadPosts === 'function') {
            prevPage.$vm.loadPosts()
          }
        }
        setTimeout(() => uni.navigateBack(), 800)
        return result
      } catch (e) {
        uni.hideLoading()
        console.error('[create] onPublish error:', e)
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style lang="scss" scoped>
/* ========== 整体容器 ========== */
.create-post {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: var(--color-background, #f5f6f8);
  display: flex;
  flex-direction: column;
}

/* ========== 顶部导航 ========== */
.navbar {
  display: flex;
  align-items: center;
  /* APP 端因 navigationStyle:custom 自渲染 header,
     需为系统状态栏预留顶部空间 */
  height: calc(88rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  padding: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px)) 24rpx 0;
  box-sizing: border-box;
  background: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.navbar__back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8rpx;
}
.navbar__back-icon {
  font-size: 56rpx;
  color: var(--color-text-primary, #1a1a1a);
  font-weight: 300;
  line-height: 1;
}
.navbar__title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);
}
/* 草稿按钮:位于标题和发布按钮之间,弱化的二级操作 */
.navbar__draft {
  display: flex;
  align-items: center;
  padding: 0 16rpx;
  height: 56rpx;
  margin-right: 8rpx;
  border-radius: 28rpx;
  background: var(--color-background, #f5f6f8);
  transition: opacity 0.2s ease;
}
.navbar__draft:active {
  opacity: 0.7;
}
.navbar__draft-text {
  font-size: 24rpx;
  color: var(--color-text-secondary, #666);
}
.navbar__draft-text--active {
  color: var(--color-primary, #18b367);
  font-weight: 500;
}
.navbar__publish {
  min-width: 112rpx;
  height: 60rpx;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary, #18b367);
  border-radius: 30rpx;
  font-size: 28rpx;
  color: #ffffff;
  font-weight: 500;
  transition: opacity 0.2s ease;
}
.navbar__publish.is-disabled {
  opacity: 0.4;
}
.navbar__publish-loading {
  font-size: 24rpx;
}

/* ========== 内容滚动区 ========== */
.content {
  flex: 1;
  width: 100%;
  padding: 24rpx;
  box-sizing: border-box;
}

/* ========== 文本输入区 ========== */
.composer {
  position: relative;  /* 让 .topic-suggest 浮层能以 composer 左上角为锚点 */
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  padding: 24rpx;
  margin-bottom: 24rpx;
  position: relative;
}
.composer__textarea {
  width: 100%;
  min-height: 200rpx;
  font-size: 32rpx;
  line-height: 1.6;
  color: var(--color-text-primary, #1a1a1a);
}
.composer__placeholder {
  color: var(--color-text-tertiary, #999);
}
.composer__counter {
  display: block;
  text-align: right;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
}
/* 敏感词警告条:柔和黄色背景,放在 counter 上方 */
.composer__warning {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  margin-top: 12rpx;
  padding: 12rpx 16rpx;
  background: #fff7e6;
  border: 1rpx solid #ffd591;
  border-radius: var(--radius-sm, 8rpx);
}
.composer__warning-icon {
  font-size: 28rpx;
  color: #fa8c16;
  flex-shrink: 0;
  line-height: 1.4;
}
.composer__warning-text {
  flex: 1;
  font-size: 24rpx;
  color: #d46b08;
  line-height: 1.4;
}
.composer__warning-hit {
  font-weight: 600;
  color: #fa541c;
  background: rgba(250, 84, 28, 0.08);
  padding: 0 4rpx;
  border-radius: 4rpx;
}

/* ========== # 话题联想浮层 ========== */
.topic-suggest {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 8rpx;
  background: var(--color-surface, #ffffff);
  border: 1rpx solid var(--color-divider, #ececec);
  border-radius: var(--radius-md, 16rpx);
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
  z-index: 10;
  overflow: hidden;
  max-height: 400rpx;
  display: flex;
  flex-direction: column;
}
.topic-suggest__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 20rpx;
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.topic-suggest__title {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text, #1a1a1a);
}
.topic-suggest__hint {
  font-size: 20rpx;
  color: var(--color-text-tertiary, #999);
}
.topic-suggest__list {
  max-height: 340rpx;
  width: 100%;
}
.topic-suggest__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 20rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.topic-suggest__item:last-child {
  border-bottom: none;
}
.topic-suggest__item.active {
  background: var(--color-background, #f5f6f8);
}
.topic-suggest__item--create {
  background: rgba(24, 179, 103, 0.06);
}
.topic-suggest__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36rpx;
  height: 36rpx;
  margin-right: 12rpx;
  background: var(--color-primary, #18b367);
  color: #ffffff;
  border-radius: 50%;
  font-size: 28rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.topic-suggest__name {
  flex: 1;
  font-size: 28rpx;
  color: var(--color-text, #1a1a1a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topic-suggest__item--create .topic-suggest__name {
  color: var(--color-primary, #18b367);
}
.topic-suggest__count {
  flex-shrink: 0;
  font-size: 22rpx;
  color: var(--color-text-tertiary, #999);
  margin-left: 12rpx;
}
.topic-suggest__empty {
  padding: 32rpx 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
}

/* ========== @ 提及用户联想浮层 ========== */
.mention-suggest {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 8rpx;
  background: var(--color-surface, #ffffff);
  border: 1rpx solid var(--color-divider, #ececec);
  border-radius: var(--radius-md, 16rpx);
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
  z-index: 10;
  overflow: hidden;
  max-height: 400rpx;
  display: flex;
  flex-direction: column;
}
.mention-suggest__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 20rpx;
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.mention-suggest__title {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--color-text, #1a1a1a);
}
.mention-suggest__loading,
.mention-suggest__hint {
  font-size: 20rpx;
  color: var(--color-text-tertiary, #999);
}
.mention-suggest__list {
  max-height: 340rpx;
  width: 100%;
}
.mention-suggest__item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 20rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.mention-suggest__item:last-child {
  border-bottom: none;
}
.mention-suggest__item.active {
  background: var(--color-background, #f5f6f8);
}
.mention-suggest__avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-background, #f5f6f8);
}
.mention-suggest__avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary, #18b367);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
}
.mention-suggest__info {
  flex: 1;
  min-width: 0;
}
.mention-suggest__name {
  font-size: 28rpx;
  color: var(--color-primary, #18b367);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mention-suggest__empty {
  padding: 32rpx 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
}

/* ========== 图片九宫格 ========== */
.media-section {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.media-thumb {
  position: relative;
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-md, 16rpx);
  overflow: hidden;
  background: var(--color-surface, #ffffff);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
/* 拖拽模式:被拖动的图片轻微放大 + 半透明 + 高亮边框 */
.media-thumb--dragging {
  transform: scale(1.08);
  opacity: 0.85;
  box-shadow: 0 4rpx 16rpx rgba(24, 179, 103, 0.4);
  border: 2rpx solid var(--color-primary, #18b367);
  box-sizing: border-box;
}
/* 拖拽序号:贴在右上角的圆形序号徽章(覆盖删除按钮) */
.media-thumb__order {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 8rpx;
  line-height: 36rpx;
  text-align: center;
  background: var(--color-primary, #18b367);
  color: #ffffff;
  border-radius: 18rpx;
  font-size: 22rpx;
  font-weight: 600;
  z-index: 2;
}
.media-thumb__img {
  width: 100%;
  height: 100%;
}
.media-thumb__remove {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  border-radius: 50%;
  font-size: 28rpx;
}
/* 比例角标:贴在缩略图左下角,展示当前图片宽高比 */
.media-thumb__ratio {
  position: absolute;
  left: 6rpx;
  bottom: 6rpx;
  padding: 2rpx 10rpx;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  font-size: 20rpx;
  border-radius: 10rpx;
  line-height: 1.4;
}
/* 理想比例(3:4 / 4:5):用主色高亮,提示"已最佳" */
.media-thumb__ratio--ideal {
  background: var(--color-primary, #18b367);
}
.media-add {
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-md, 16rpx);
  background: var(--color-surface, #ffffff);
  border: 2rpx dashed var(--color-divider, #d0d0d0);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary, #666);
}
.media-add__icon {
  font-size: 60rpx;
  font-weight: 200;
  line-height: 1;
}
.media-add__text {
  margin-top: 8rpx;
  font-size: 22rpx;
}

/* ========== 视频卡片 ========== */
.media-video {
  position: relative;
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  overflow: hidden;
  margin-bottom: 24rpx;
}
.media-video__player {
  position: relative;
  width: 100%;
  background: #000;
  /* 16:9 默认高度,视频本身会按 object-fit 自适应 */
  aspect-ratio: 16 / 9;
}
.media-video__el {
  width: 100%;
  height: 100%;
  display: block;
}
.media-video__progress {
  position: absolute;
  bottom: 16rpx;
  left: 16rpx;
  right: 16rpx;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 16rpx;
  padding: 12rpx 16rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.media-video__progress-bar {
  height: 8rpx;
  background: var(--color-primary, #18b367);
  border-radius: 4rpx;
  transition: width 0.2s ease;
}
.media-video__progress-text {
  color: #fff;
  font-size: 22rpx;
  text-align: center;
}
.media-video__meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary, #666);
}
.media-video__duration {
  color: var(--color-text-primary, #1a1a1a);
  font-weight: 500;
}
.media-video__size {
  color: var(--color-text-tertiary, #999);
}
.media-video__state {
  margin-left: auto;
  padding: 4rpx 12rpx;
  border-radius: 12rpx;
  font-size: 22rpx;
  background: rgba(24, 179, 103, 0.12);
  color: var(--color-primary, #18b367);
}
.media-video__state--pending {
  background: rgba(255, 159, 28, 0.12);
  color: #ff9f1c;
}
.media-video__remove {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  border-radius: 50%;
  font-size: 32rpx;
  z-index: 2;
}
/* 封面选择按钮:视频下方一行,左侧文字 + 右侧小缩略图 */
.media-video__cover-btn {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  margin: 0 24rpx 16rpx;
  background: var(--color-background, #f5f6f8);
  border: 1rpx dashed var(--color-divider, #d0d0d0);
  border-radius: var(--radius-md, 12rpx);
  font-size: 26rpx;
  color: var(--color-text-secondary, #666);
}
.media-video__cover-btn:active {
  background: rgba(24, 179, 103, 0.06);
  border-color: var(--color-primary, #18b367);
}
.media-video__cover-btn-icon {
  font-size: 32rpx;
}
.media-video__cover-btn-text {
  flex: 1;
}
.media-video__cover-thumb {
  width: 80rpx;
  height: 80rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}
/* 封面选择面板内部样式 */
.cover-picker__hint {
  padding: 12rpx 24rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
  background: var(--color-background, #f5f6f8);
}
.cover-picker__loading {
  padding: 80rpx 24rpx;
  text-align: center;
  color: var(--color-text-tertiary, #999);
  font-size: 28rpx;
}
.cover-grid {
  display: flex;
  gap: 16rpx;
  padding: 16rpx;
  flex-wrap: wrap;
}
.cover-grid__item {
  position: relative;
  width: calc((100% - 32rpx) / 3);
  aspect-ratio: 3 / 4;
  border-radius: 12rpx;
  overflow: hidden;
  border: 2rpx solid transparent;
  box-sizing: border-box;
}
.cover-grid__item.active {
  border-color: var(--color-primary, #18b367);
}
.cover-grid__img {
  width: 100%;
  height: 100%;
}
.cover-grid__time {
  position: absolute;
  left: 8rpx;
  bottom: 8rpx;
  padding: 2rpx 8rpx;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  font-size: 20rpx;
  border-radius: 6rpx;
}
.cover-grid__check {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  background: var(--color-primary, #18b367);
  color: #ffffff;
  border-radius: 50%;
  font-size: 24rpx;
  font-weight: 600;
}
.cover-picker__uploading {
  text-align: center;
  padding: 24rpx;
  color: var(--color-primary, #18b367);
  font-size: 26rpx;
}
.cover-picker__footer {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  border-top: 1rpx solid var(--color-divider, #ececec);
}
.cover-picker__btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  font-size: 28rpx;
  border-radius: 12rpx;
  background: var(--color-background, #f5f6f8);
  color: var(--color-text-secondary, #666);
}
.cover-picker__btn--primary {
  background: var(--color-primary, #18b367);
  color: #ffffff;
}
.cover-picker__btn--primary.disabled {
  opacity: 0.4;
}

/* ========== 设置项 Cell 卡 ========== */
.option-card {
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-md, 16rpx);
  overflow: hidden;
}
.option-cell {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.option-cell:last-child {
  border-bottom: none;
}
.option-cell__icon {
  font-size: 36rpx;
  margin-right: 16rpx;
  width: 44rpx;
  text-align: center;
}
.option-cell__label {
  font-size: 30rpx;
  color: var(--color-text-primary, #1a1a1a);
  flex-shrink: 0;
}
.option-cell__value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: var(--color-text-secondary, #666);
  margin-right: 12rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.option-cell__value.is-placeholder {
  color: var(--color-text-tertiary, #999);
}
.option-cell__arrow {
  font-size: 36rpx;
  color: var(--color-text-tertiary, #ccc);
  line-height: 1;
}

/* ========== 底部工具栏 ========== */
.toolbar {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 16rpx;
  background: var(--color-surface, #ffffff);
  border-top: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.toolbar__btn {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toolbar__icon {
  font-size: 40rpx;
}
/* 工具栏按钮激活态(emoji 面板打开时高亮) */
.toolbar__btn.active {
  background: var(--color-background, #f5f6f8);
}

/* ========== Emoji 面板 ========== */
.emoji-panel {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 96rpx; /* 紧贴工具栏上方 */
  background: var(--color-background, #f5f6f8);
  border-top: 1rpx solid var(--color-divider, #ececec);
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 50;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
}
.emoji-panel__search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 12rpx 24rpx 0;
  padding: 12rpx 20rpx;
  background: var(--color-surface, #ffffff);
  border-radius: var(--radius-pill, 999rpx);
  border: 1rpx solid var(--color-divider, #ececec);
}
.emoji-panel__search-icon {
  font-size: 28rpx;
  color: var(--color-text-tertiary, #999);
  flex-shrink: 0;
}
.emoji-panel__search-input {
  flex: 1;
  font-size: var(--font-size-sm, 26rpx);
  background: transparent;
}
.emoji-panel__search-placeholder {
  color: var(--color-text-tertiary, #999);
}
.emoji-panel__search-clear {
  font-size: 32rpx;
  color: var(--color-text-tertiary, #999);
  padding: 0 8rpx;
}
.emoji-panel__empty {
  width: 100%;
  padding: 48rpx 0;
  text-align: center;
  font-size: var(--font-size-sm, 24rpx);
  color: var(--color-text-tertiary, #999);
}
.emoji-panel__tabs {
  display: flex;
  white-space: nowrap;
  background: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
}
.emoji-panel__tab {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 24rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary, #666);
  border-bottom: 3rpx solid transparent;
  flex-shrink: 0;
}
.emoji-panel__tab.active {
  color: var(--color-primary, #18b367);
  border-bottom-color: var(--color-primary, #18b367);
}
.emoji-panel__tab-icon {
  font-size: 28rpx;
}
.emoji-panel__tab-name {
  font-size: 22rpx;
}
.emoji-panel__grid-wrap {
  height: 360rpx;
  width: 100%;
}
.emoji-panel__grid {
  display: flex;
  flex-wrap: wrap;
  padding: 8rpx;
}
.emoji-panel__item {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  border-radius: 12rpx;
  transition: background-color 0.1s ease;
}
.emoji-panel__item:active {
  background: var(--color-surface, #ffffff);
}
.emoji-panel__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 8rpx 16rpx;
  background: var(--color-surface, #ffffff);
}
.emoji-panel__backspace {
  width: 72rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8rpx;
  background: var(--color-background, #f5f6f8);
}
.emoji-panel__backspace:active {
  background: var(--color-divider, #ececec);
}
.emoji-panel__backspace-icon {
  font-size: 32rpx;
  color: var(--color-text, #1a1a1a);
}

/* ========== 话题选择底部弹层 ========== */
.sheet-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  max-height: 70vh;
  background: var(--color-surface, #ffffff);
  border-radius: 24rpx 24rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sheet__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  flex-shrink: 0;
}
.sheet__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);
}
.sheet__close {
  width: 48rpx;
  height: 48rpx;
  line-height: 44rpx;
  text-align: center;
  font-size: 40rpx;
  color: var(--color-text-secondary, #666);
}
.sheet__loading,
.sheet__empty {
  padding: 80rpx 24rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--color-text-tertiary, #999);
}
.sheet__list {
  flex: 1;
  width: 100%;
}
.topic-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.topic-item.is-active {
  background: var(--color-background, #f5f6f8);
}
.topic-item__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.topic-item__name {
  font-size: 30rpx;
  color: var(--color-primary, #18b367);
  font-weight: 500;
}
.topic-item__desc {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.topic-item__count {
  flex-shrink: 0;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
  margin-left: 16rpx;
}

/* 定时发布面板:复用 sheet-mask/sheet,内部样式独立 */
.scheduled-picker__hint {
  padding: 12rpx 24rpx;
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
  background: var(--color-background, #f5f6f8);
}
.scheduled-picker__shortcuts {
  display: flex;
  flex-direction: column;
  gap: 1rpx;
  background: var(--color-divider, #ececec);
  border-top: 1rpx solid var(--color-divider, #ececec);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
}
.scheduled-picker__shortcut {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  background: var(--color-surface, #ffffff);
}
.scheduled-picker__shortcut:active {
  background: var(--color-background, #f5f6f8);
}
.scheduled-picker__shortcut-name {
  font-size: 28rpx;
  color: var(--color-text-primary, #1a1a1a);
  font-weight: 500;
}
.scheduled-picker__shortcut-desc {
  font-size: 24rpx;
  color: var(--color-text-tertiary, #999);
}
.scheduled-picker__custom {
  padding: 24rpx;
}
.scheduled-picker__custom-label {
  font-size: 24rpx;
  color: var(--color-text-secondary, #666);
  margin-bottom: 12rpx;
  display: block;
}
.scheduled-picker__custom-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 20rpx;
  background: var(--color-background, #f5f6f8);
  border-radius: 12rpx;
  font-size: 28rpx;
  color: var(--color-text-primary, #1a1a1a);
}
.scheduled-picker__custom-arrow {
  color: var(--color-text-tertiary, #ccc);
  font-size: 36rpx;
}
.scheduled-picker__footer {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  border-top: 1rpx solid var(--color-divider, #ececec);
}
.scheduled-picker__btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  font-size: 28rpx;
  border-radius: 12rpx;
  background: var(--color-background, #f5f6f8);
  color: var(--color-text-secondary, #666);
}
.scheduled-picker__btn--primary {
  background: var(--color-primary, #18b367);
  color: #ffffff;
}
.scheduled-picker__btn--primary.disabled {
  opacity: 0.4;
}

/* ========== 位置选择面板(共用 sheet-mask/sheet,新增内部样式) ========== */
.option-cell--clearable {
  background: var(--color-background, #f5f6f8);
}
.option-cell__icon--muted {
  opacity: 0.5;
}
.option-cell__label--muted {
  color: var(--color-text-tertiary, #999);
  font-size: var(--font-size-sm, 26rpx);
}
.location-picker__search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 0 24rpx 16rpx;
  padding: 14rpx 20rpx;
  background: var(--color-background, #f5f6f8);
  border-radius: var(--radius-pill, 999rpx);
}
.location-picker__search-icon {
  font-size: 28rpx;
  color: var(--color-text-tertiary, #999);
  flex-shrink: 0;
}
.location-picker__search-input {
  flex: 1;
  font-size: var(--font-size-base, 28rpx);
  background: transparent;
  color: var(--color-text, #1a1a1a);
}
.location-picker__search-placeholder {
  color: var(--color-text-tertiary, #999);
}
.location-picker__search-clear {
  font-size: 36rpx;
  color: var(--color-text-tertiary, #999);
  padding: 0 8rpx;
}
.location-picker__shortcuts {
  display: flex;
  flex-direction: column;
  gap: 1rpx;
  background: var(--color-divider, #ececec);
  border-top: 1rpx solid var(--color-divider, #ececec);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  margin-bottom: 16rpx;
}
.location-picker__shortcut {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: var(--color-surface, #ffffff);
}
.location-picker__shortcut:active {
  background: var(--color-background, #f5f6f8);
}
.location-picker__shortcut-icon {
  font-size: 40rpx;
  flex-shrink: 0;
}
.location-picker__shortcut-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.location-picker__shortcut-name {
  font-size: var(--font-size-base, 28rpx);
  color: var(--color-text, #1a1a1a);
  font-weight: 500;
}
.location-picker__shortcut-desc {
  font-size: var(--font-size-sm, 22rpx);
  color: var(--color-text-tertiary, #999);
  margin-top: 4rpx;
}
.location-picker__hint {
  padding: 8rpx 24rpx 12rpx;
  font-size: var(--font-size-sm, 22rpx);
  color: var(--color-text-tertiary, #999);
}

/* POI 列表项 */
.poi-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider, #f0f0f0);
}
.poi-item.is-active {
  background: var(--color-background, #f5f6f8);
}
.poi-item__icon {
  font-size: 40rpx;
  width: 56rpx;
  text-align: center;
  flex-shrink: 0;
}
.poi-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.poi-item__name {
  font-size: var(--font-size-base, 28rpx);
  color: var(--color-text, #1a1a1a);
  font-weight: 500;
}
.poi-item__address {
  font-size: var(--font-size-sm, 22rpx);
  color: var(--color-text-tertiary, #999);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.poi-item__distance {
  font-size: var(--font-size-sm, 22rpx);
  color: var(--color-text-tertiary, #999);
  flex-shrink: 0;
}
</style>
