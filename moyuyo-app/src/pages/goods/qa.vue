<template>
  <view class="qa">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">商品问答</text>
      <view class="header-btn" @click="onOpenAskModal">
        <text class="ask-btn-text">提问</text>
      </view>
    </view>

    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-field">
        <text class="search-icon"><text class="luc luc-search" /></text>
        <input
          v-model="searchText"
          class="search-input"
          type="text"
          placeholder="搜索问题..."
          @input="onSearch"
        >
      </view>
    </view>

    <!-- 热门标签 -->
    <scroll-view class="hot-tags" scroll-x show-scrollbar="false">
      <view
        v-for="tag in hotTags"
        :key="tag"
        class="hot-tag"
        :class="{ 'hot-tag--active': activeHotTag === tag }"
        @click="onHotTagClick(tag)"
      >
        <text v-if="tag === hotTags[0]" class="hot-tag-icon">
          <text class="luc luc-flame" />
        </text>
        <text>{{ tag }}</text>
      </view>
    </scroll-view>

    <!-- 筛选 Tab -->
    <view class="filter-bar">
      <view class="filter-tabs">
        <view
          v-for="tab in filterTabs"
          :key="tab.value"
          class="filter-tab"
          :class="{ 'filter-tab--active': activeFilter === tab.value }"
          @click="activeFilter = tab.value"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>
    </view>

    <!-- 问答列表 -->
    <view v-if="filteredQaList.length > 0" class="qa-list">
      <view v-for="item in filteredQaList" :key="item.id" class="qa-item">
        <text class="qa-question">{{ item.question }}</text>
        <view class="qa-asker">
          <view class="asker-avatar">
            <text class="asker-icon"><text class="luc luc-user" /></text>
          </view>
          <text class="asker-name">{{ item.asker }}</text>
          <text class="asker-time">{{ item.time }}</text>
        </view>

        <!-- 有回答 -->
        <view v-if="item.answer" class="qa-answer">
          <view class="answer-header">
            <text
              class="answer-tag"
              :class="item.answerTag === '商家' ? 'tag-merchant' : 'tag-buyer'"
            >
              {{ item.answerTag }}
            </text>
            <text class="answer-author">{{ item.answerAuthor }}</text>
            <text class="answer-time">{{ item.answerTime }}</text>
          </view>
          <text class="answer-content">{{ item.answer }}</text>
          <view class="answer-like">
            <text class="like-btn" :class="{ liked: item.isLiked }" @click="onToggleLike(item)">
              <text class="luc luc-thumbs-up" />
              <text class="like-text">有帮助</text>
            </text>
            <text class="like-count">{{ item.likeCount }} 人觉得有帮助</text>
          </view>
        </view>

        <!-- 无回答 -->
        <view v-else class="qa-no-answer">
          <text class="no-answer-icon">⏰</text>
          <text class="no-answer-text">暂无回答，等待商家或用户回复</text>
        </view>
      </view>
    </view>

    <!-- 空搜索状态 -->
    <view v-else-if="searchText" class="empty-state">
      <text class="empty-icon"><text class="luc luc-search" /></text>
      <text class="empty-title">没有找到相关问题</text>
      <text class="empty-desc">试试换个关键词搜索</text>
    </view>

    <!-- 底部 FAB 提问按钮 -->
    <view class="fab-ask" @click="onOpenAskModal">
      <text class="fab-icon"><text class="luc luc-pencil" /></text>
    </view>

    <!-- 提问弹窗 -->
    <view class="modal-overlay" :class="{ active: showAskModal }" @click="onCloseAskModal">
      <view class="modal-panel" @click.stop>
        <view class="modal-handle" />
        <text class="modal-title">我要提问</text>
        <text class="modal-desc">请描述你在使用商品时遇到的问题</text>
        <view class="modal-textarea-wrap">
          <textarea v-model="askText" class="modal-textarea" placeholder="请输入你的问题..." />
        </view>
        <view class="modal-actions">
          <view class="modal-btn modal-btn--cancel" @click="onCloseAskModal">取消</view>
          <view
            class="modal-btn modal-btn--submit"
            :class="{ disabled: !askText.trim() }"
            @click="onSubmitQuestion"
          >
            提交
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      searchText: '',
      activeHotTag: '',
      activeFilter: 'all',
      showAskModal: false,
      askText: '',
      hotTags: ['成分安全吗', '适合多大宠物', '多久发货', '可以混用吗'],
      filterTabs: [
        { label: '全部', value: 'all' },
        { label: '未回复', value: 'unanswered' },
        { label: '已回复', value: 'answered' },
      ],
      qaList: [
        {
          id: 1,
          question:
            '这款洗护套装的成分对猫咪皮肤敏感的情况适用吗？我家猫咪之前用过其他品牌会红肿。',
          asker: '小猫咪的铲屎官',
          time: '2025-06-12',
          answer:
            '您好！本产品采用天然植物萃取成分，不含人工香精、色素和防腐剂，经过皮肤科兽医测试，敏感肌猫咪也可以放心使用。建议首次使用时先在小面积皮肤试用观察24小时。',
          answerTag: '商家',
          answerAuthor: 'MOYUYO 官方',
          answerTime: '2025-06-13',
          likeCount: 32,
          isLiked: false,
        },
        {
          id: 2,
          question: '3个月大的金毛幼犬可以用这款吗？用量大概是多少？',
          asker: '金毛妈妈',
          time: '2025-06-10',
          answer:
            '我家柴犬从2个月就开始用了，完全没有问题。幼犬用量大概是成年犬的三分之一到一半，具体看狗狗体型。可以先买小包装试试。',
          answerTag: '已购用户',
          answerAuthor: '柴柴酱的主人',
          answerTime: '2025-06-11',
          likeCount: 18,
          isLiked: false,
        },
        {
          id: 3,
          question: '这款洗护套装和你们家的护毛素可以一起用吗？会不会冲突？',
          asker: '布偶猫铲屎官',
          time: '2025-06-08',
          answer:
            '可以的！我们的洗护套装和护毛素是同一系列产品，配方互相兼容。使用顺序是先用洗护套装清洁，冲洗干净后再使用护毛素，效果更佳。',
          answerTag: '商家',
          answerAuthor: 'MOYUYO 官方',
          answerTime: '2025-06-08',
          likeCount: 9,
          isLiked: false,
        },
        {
          id: 4,
          question: '请问这款套装包含几瓶？保质期是多久？开封后需要多久用完？',
          asker: '新手铲屎官',
          time: '2025-07-02',
          answer: '',
          answerTag: '',
          answerAuthor: '',
          answerTime: '',
          likeCount: 0,
          isLiked: false,
        },
        {
          id: 5,
          question: '有没有试用装可以买？想先试试味道和效果再买正装。',
          asker: '哈士奇爸爸',
          time: '2025-07-06',
          answer: '',
          answerTag: '',
          answerAuthor: '',
          answerTime: '',
          likeCount: 0,
          isLiked: false,
        },
      ],
    }
  },
  computed: {
    filteredQaList() {
      let list = this.qaList
      if (this.activeFilter === 'answered') {
        list = list.filter((item) => item.answer)
      } else if (this.activeFilter === 'unanswered') {
        list = list.filter((item) => !item.answer)
      }
      if (this.searchText.trim()) {
        const keyword = this.searchText.trim().toLowerCase()
        list = list.filter((item) => item.question.toLowerCase().includes(keyword))
      }
      return list
    },
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    onSearch() {
      // 实时搜索已在 computed 中处理
    },
    onHotTagClick(tag) {
      this.activeHotTag = tag
      this.searchText = tag
    },
    onToggleLike(item) {
      item.isLiked = !item.isLiked
      if (item.isLiked) {
        item.likeCount++
      } else {
        item.likeCount--
      }
    },
    onOpenAskModal() {
      this.showAskModal = true
    },
    onCloseAskModal() {
      this.showAskModal = false
      this.askText = ''
    },
    onSubmitQuestion() {
      if (!this.askText.trim()) return
      this.qaList.unshift({
        id: Date.now(),
        question: this.askText.trim(),
        asker: '我',
        time: new Date().toISOString().slice(0, 10),
        answer: '',
        answerTag: '',
        answerAuthor: '',
        answerTime: '',
        likeCount: 0,
        isLiked: false,
      })
      this.onCloseAskModal()
      uni.showToast({ title: '提问成功', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.qa {
  min-height: 100vh;
  background: var(--background);
  padding-bottom: 160rpx;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 30rpx;
  background: rgba(255, 255, 255, 0.95);
  border-bottom: 1rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 44rpx;
  color: var(--icon-700);
  line-height: 1;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}

.ask-btn-text {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--primary);
}

/* ===== 搜索栏 ===== */
.search-bar {
  padding: 20rpx 30rpx;
}

.search-field {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: var(--background-200);
}

.search-icon {
  font-size: 28rpx;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 26rpx;
  color: var(--foreground);
}

.search-input::placeholder {
  color: var(--text-400);
}

/* ===== 热门标签 ===== */
.hot-tags {
  padding: 0 30rpx 16rpx;
  white-space: nowrap;
}

.hot-tag {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  border: 2rpx solid var(--border);
  font-size: 22rpx;
  font-weight: 500;
  color: var(--text-600);
  margin-right: 16rpx;
  background: var(--background);
}

.hot-tag--active {
  border-color: var(--primary);
  background: var(--brand-50);
  color: var(--primary);
}

.hot-tag-icon {
  font-size: 24rpx;
}

/* ===== 筛选 Tab ===== */
.filter-bar {
  padding: 0 30rpx 16rpx;
}

.filter-tabs {
  display: inline-flex;
  gap: 8rpx;
  padding: 6rpx;
  border-radius: 999rpx;
  background: var(--background-200);
}

.filter-tab {
  height: 52rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
  color: var(--text-500);
  display: flex;
  align-items: center;
  justify-content: center;
}

.filter-tab--active {
  background: var(--background);
  color: var(--text-800);
  box-shadow: var(--shadow-sm);
}

/* ===== 问答列表 ===== */
.qa-list {
  padding: 0 30rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.qa-item {
  padding: 32rpx;
  border-radius: 24rpx;
  border: 2rpx solid var(--border);
  background: var(--card);
}

.qa-question {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  line-height: 1.5;
  display: block;
}

.qa-asker {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 20rpx;
}

.asker-avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.asker-icon {
  font-size: 24rpx;
}

.asker-name {
  font-size: 22rpx;
  color: var(--text-500);
}

.asker-time {
  font-size: 22rpx;
  color: var(--text-400);
}

.qa-answer {
  margin-top: 24rpx;
  margin-left: 8rpx;
  padding-left: 20rpx;
  border-left: 4rpx solid var(--background-300);
}

.answer-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.answer-tag {
  display: inline-flex;
  align-items: center;
  height: 36rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 600;
}

.tag-merchant {
  background: var(--brand-50);
  color: var(--primary);
}

.tag-buyer {
  background: var(--state-success-surface);
  color: var(--state-success);
}

.answer-author {
  font-size: 22rpx;
  color: var(--text-400);
}

.answer-time {
  font-size: 22rpx;
  color: var(--text-400);
}

.answer-content {
  font-size: 26rpx;
  color: var(--text-600);
  line-height: 1.6;
  display: block;
}

.answer-like {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 20rpx;
}

.like-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: var(--text-500);
}

.like-btn.liked {
  color: var(--primary);
}

.like-text {
  font-size: 22rpx;
}

.like-count {
  font-size: 22rpx;
  color: var(--text-400);
}

.qa-no-answer {
  margin-top: 24rpx;
  margin-left: 8rpx;
  padding-left: 20rpx;
  border-left: 4rpx solid var(--background-300);
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.no-answer-icon {
  font-size: 24rpx;
}

.no-answer-text {
  font-size: 22rpx;
  color: var(--text-400);
}

/* ===== 空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 60rpx;
}

.empty-icon {
  font-size: 80rpx;
  margin-bottom: 24rpx;
}

.empty-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 12rpx;
}

.empty-desc {
  font-size: 26rpx;
  color: var(--text-500);
}

/* ===== FAB 按钮 ===== */
.fab-ask {
  position: fixed;
  bottom: 60rpx;
  right: 40rpx;
  z-index: 40;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: var(--shadow-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}

.fab-icon {
  font-size: 40rpx;
}

/* ===== 提问弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s ease;
}

.modal-overlay.active {
  opacity: 1;
  pointer-events: auto;
}

.modal-panel {
  width: 100%;
  background: var(--card);
  border-radius: calc(var(--radius) * 1.2) calc(var(--radius) * 1.2) 0 0;
  padding: 24rpx 40rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}

.modal-overlay.active .modal-panel {
  transform: translateY(0);
}

.modal-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: var(--background-400);
  margin: 0 auto 32rpx;
}

.modal-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
}

.modal-desc {
  font-size: 26rpx;
  color: var(--muted-foreground);
  display: block;
  margin-top: 12rpx;
}

.modal-textarea-wrap {
  margin-top: 28rpx;
  padding: 20rpx;
  border: 2rpx solid var(--input);
  border-radius: var(--radius);
  background: var(--background);
}

.modal-textarea-wrap:focus-within {
  border-color: var(--ring);
}

.modal-textarea {
  width: 100%;
  min-height: 180rpx;
  border: none;
  outline: none;
  background: transparent;
  font-size: 28rpx;
  color: var(--foreground);
  line-height: 1.5;
}

.modal-textarea::placeholder {
  color: var(--muted-foreground);
}

.modal-actions {
  margin-top: 40rpx;
  display: flex;
  gap: 20rpx;
}

.modal-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.modal-btn--cancel {
  background: var(--secondary);
  color: var(--secondary-foreground);
}

.modal-btn--submit {
  background: var(--primary);
  color: var(--primary-foreground);
}

.modal-btn--submit.disabled {
  opacity: 0.5;
}
</style>
