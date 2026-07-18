<template>
  <div class="sw-page">
    <!-- 页面标题 -->
    <div class="sw-header">
      <div class="sw-header-text">
        <h2>敏感词管理</h2>
        <p class="sw-subtitle">管理内容安全策略，配置敏感词过滤规则</p>
      </div>
    </div>

    <!-- 顶部操作栏 -->
    <div class="sw-toolbar">
      <div class="sw-search-field">
        <!-- 搜索图标 SVG -->
        <svg class="sw-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input
          v-model="searchKeyword"
          type="text"
          class="sw-search-input"
          placeholder="搜索敏感词..."
          @input="handleSearch"
        />
      </div>
      <button class="sw-btn sw-btn-primary" @click="toggleAddForm">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新增敏感词
      </button>
      <button class="sw-btn sw-btn-outline sw-btn-danger" @click="handleBatchDelete">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 6 5 6 21 6"/>
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
        </svg>
        批量删除
      </button>
    </div>

    <!-- 分类筛选 -->
    <div class="sw-category-section">
      <span class="sw-section-label">分类筛选</span>
      <div class="sw-category-chips">
        <button
          v-for="cat in categories"
          :key="cat.key"
          class="sw-category-chip"
          :class="{ active: activeCategory === cat.key }"
          @click="activeCategory = cat.key; filterWords()"
        >
          {{ cat.label }}
          <span class="sw-chip-count">{{ cat.count }}</span>
        </button>
      </div>
    </div>

    <!-- 新增/编辑折叠区域 -->
    <div class="sw-collapsible" :class="{ open: showForm }">
      <div class="sw-collapsible-header" @click="toggleAddForm">
        <div class="sw-collapsible-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="16"/>
            <line x1="8" y1="12" x2="16" y2="12"/>
          </svg>
          <span>{{ formTitle }}</span>
        </div>
        <svg class="sw-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>
      <div class="sw-collapsible-body" :class="{ open: showForm }">
        <div class="sw-form">
          <div class="sw-form-row">
            <div class="sw-form-group">
              <label class="sw-form-label">敏感词</label>
              <input v-model="formData.word" type="text" class="sw-form-input" placeholder="输入敏感词或正则表达式" />
            </div>
            <div class="sw-form-group">
              <label class="sw-form-label">替换词</label>
              <input v-model="formData.replacement" type="text" class="sw-form-input" placeholder="输入替换词" />
            </div>
          </div>
          <div class="sw-form-row">
            <div class="sw-form-group">
              <label class="sw-form-label">匹配模式</label>
              <select v-model="formData.matchMode" class="sw-form-select">
                <option value="精确匹配">精确匹配</option>
                <option value="模糊匹配">模糊匹配</option>
                <option value="正则表达式">正则表达式</option>
              </select>
            </div>
            <div class="sw-form-group">
              <label class="sw-form-label">分类</label>
              <select v-model="formData.category" class="sw-form-select">
                <option v-for="cat in categoryOptions" :key="cat" :value="cat">{{ cat }}</option>
              </select>
            </div>
          </div>
          <div class="sw-form-actions">
            <button class="sw-btn sw-btn-text" @click="cancelForm">取消</button>
            <button class="sw-btn sw-btn-primary" @click="handleSubmit">{{ isEditing ? '保存修改' : '添加敏感词' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 敏感词列表（卡片布局） -->
    <div class="sw-list-header">
      <span class="sw-list-title">敏感词列表</span>
      <span class="sw-list-count">共 {{ filteredWords.length }} 条</span>
    </div>

    <div class="sw-word-list">
      <div v-for="item in filteredWords" :key="item.id" class="sw-word-item">
        <!-- 顶部：敏感词文本 + 匹配模式标签 -->
        <div class="sw-word-item-top">
          <span class="sw-word-content">{{ item.word }}</span>
          <span class="sw-match-mode-tag" :class="item.matchMode === '精确匹配' ? 'exact' : item.matchMode === '模糊匹配' ? 'fuzzy' : 'regex'">
            {{ item.matchMode }}
          </span>
        </div>
        <!-- 中部：分类标签 + 命中次数 + 替换词 -->
        <div class="sw-word-item-meta">
          <span class="sw-category-tag">{{ item.category }}</span>
          <span class="sw-hit-info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            命中 <span class="sw-hit-count">{{ item.hitCount }}</span> 次
          </span>
          <span class="sw-replace-word" v-if="item.replacement">
            替换为：<span class="sw-replace-value">{{ item.replacement }}</span>
          </span>
        </div>
        <!-- 底部：最近命中时间 + 操作按钮 -->
        <div class="sw-word-item-bottom">
          <span class="sw-last-hit-time">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            最近命中: {{ item.lastHitTime }}
          </span>
          <div class="sw-word-actions">
            <button class="sw-word-action-btn" title="编辑" @click="handleEdit(item)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
            </button>
            <button class="sw-word-action-btn sw-action-delete" title="删除" @click="handleDelete(item)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="filteredWords.length === 0" class="sw-empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <span>没有匹配的敏感词</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSensitiveList, getSensitiveCategories, createSensitive, updateSensitive, deleteSensitive, batchDeleteSensitive } from '../api/admin'

// ---- 搜索 ----
const searchKeyword = ref('')
const activeCategory = ref('全部')

// ---- 分类数据 ----
const categories = ref([])

// 分类选项（用于表单下拉）
const categoryOptions = ref(['广告', '政治', '色情', '暴力', '其他'])

// ---- 数据（从API加载） ----
const words = ref([])

// ---- 表单状态 ----
const showForm = ref(false)
const isEditing = ref(false)
const editingId = ref(null)

const formTitle = computed(() => isEditing.value ? '编辑敏感词' : '新增敏感词')

const formData = reactive({
  word: '',
  replacement: '',
  matchMode: '精确匹配',
  category: '广告'
})

// ---- 筛选逻辑 ----
const filteredWords = ref([])

function filterWords() {
  let list = [...words.value]
  // 分类筛选
  if (activeCategory.value !== '全部') {
    list = list.filter(w => w.category === activeCategory.value)
  }
  // 关键词搜索
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(w => w.word.toLowerCase().includes(kw) || w.replacement.toLowerCase().includes(kw))
  }
  filteredWords.value = list
}

function handleSearch() {
  filterWords()
}

// ---- 从API加载数据 ----
async function loadData() {
  try {
    // 并行加载敏感词列表和分类数据
    const [listRes, catRes] = await Promise.all([
      getSensitiveList(),
      getSensitiveCategories()
    ])
    const wordList = (listRes && listRes.records) || listRes || []
    words.value = wordList
    // 填充分类数据
    if (catRes) {
      categories.value = catRes
      // 提取分类选项
      categoryOptions.value = catRes.filter(c => c.key !== '全部').map(c => c.key)
    }
    filterWords()
  } catch (e) {
    console.error('加载敏感词数据失败:', e)
    ElMessage.error('加载敏感词数据失败')
  }
}
// ---- 表单操作 ----
function toggleAddForm() {
  if (isEditing.value && showForm.value) {
    resetForm()
  }
  showForm.value = !showForm.value
}

function resetForm() {
  formData.word = ''
  formData.replacement = ''
  formData.matchMode = '精确匹配'
  formData.category = '广告'
  isEditing.value = false
  editingId.value = null
}

function cancelForm() {
  resetForm()
  showForm.value = false
}

// 新增/编辑敏感词（调用API）
async function handleSubmit() {
  if (!formData.word.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  if (!formData.replacement.trim()) {
    ElMessage.warning('请输入替换词')
    return
  }

  try {
    if (isEditing.value && editingId.value !== null) {
      // 编辑保存
      await updateSensitive({
        id: editingId.value,
        word: formData.word,
        replacement: formData.replacement,
        matchMode: formData.matchMode,
        category: formData.category
      })
      ElMessage.success('修改成功')
    } else {
      // 新增
      await createSensitive({
        word: formData.word,
        replacement: formData.replacement,
        matchMode: formData.matchMode,
        category: formData.category
      })
      ElMessage.success('添加成功')
    }
    resetForm()
    showForm.value = false
    // 重新加载数据
    loadData()
  } catch (e) {
    console.error('提交敏感词失败:', e)
    ElMessage.error('操作失败')
  }
}

function handleEdit(item) {
  isEditing.value = true
  editingId.value = item.id
  formData.word = item.word
  formData.replacement = item.replacement
  formData.matchMode = item.matchMode
  formData.category = item.category
  showForm.value = true
}

// 删除单个敏感词（调用API）
async function handleDelete(item) {
  try {
    await ElMessageBox.confirm('确认删除该敏感词吗？', '提示', { type: 'warning' })
    await deleteSensitive(item.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除敏感词失败:', e)
    }
  }
}

// 批量删除敏感词（调用API）
async function handleBatchDelete() {
  if (filteredWords.value.length === 0) {
    ElMessage.info('没有可删除的词条')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除当前筛选出的 ${filteredWords.value.length} 条敏感词？`, '提示', { type: 'warning' })
    const ids = filteredWords.value.map(w => w.id)
    await batchDeleteSensitive(ids)
    ElMessage.success('批量删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('批量删除敏感词失败:', e)
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* ---- 页面容器 ---- */
.sw-page {
  padding: 24px 28px;
}

/* ---- 页面标题 ---- */
.sw-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.sw-header-text h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}

.sw-subtitle {
  font-size: 13px;
  color: var(--text-400);
  margin: 6px 0 0;
}

/* ---- 顶部操作栏 ---- */
.sw-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.sw-search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 360px;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--background);
  box-shadow: var(--shadow-xs);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.sw-search-field:focus-within {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

.sw-search-icon {
  width: 16px;
  height: 16px;
  color: var(--text-400);
  flex-shrink: 0;
}

.sw-search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--foreground);
  font-size: 14px;
  font-family: inherit;
}

.sw-search-input::placeholder {
  color: var(--text-400);
}

/* ---- 按钮 ---- */
.sw-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: calc(var(--radius) * 0.7);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
}

.sw-btn svg {
  width: 16px;
  height: 16px;
}

.sw-btn-primary {
  background: var(--primary);
  color: var(--primary-foreground);
}

.sw-btn-primary:hover {
  filter: brightness(0.92);
}

.sw-btn-outline {
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-600);
}

.sw-btn-outline:hover {
  background: var(--background-100);
}

.sw-btn-danger {
  color: var(--state-error);
  border-color: var(--state-error);
}

.sw-btn-danger:hover {
  background: var(--state-error-surface);
}

.sw-btn-text {
  background: transparent;
  color: var(--text-500);
  box-shadow: none;
  padding: 0 12px;
}

.sw-btn-text:hover {
  color: var(--text-800);
}

/* ---- 分类筛选 ---- */
.sw-category-section {
  margin-bottom: 20px;
}

.sw-section-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-500);
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.sw-category-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sw-category-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--background);
  color: var(--text-500);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
  font-family: inherit;
}

.sw-category-chip.active {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
  box-shadow: 0 2px 8px rgba(0, 122, 255, 0.2);
}

.sw-category-chip:not(.active):hover {
  border-color: var(--primary);
  color: var(--primary);
}

.sw-chip-count {
  font-size: 11px;
  opacity: 0.7;
}

/* ---- 折叠表单区域 ---- */
.sw-collapsible {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--card);
  box-shadow: var(--shadow-sm);
  margin-bottom: 20px;
  overflow: hidden;
}

.sw-collapsible-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s ease;
}

.sw-collapsible-header:hover {
  background: var(--background-100);
}

.sw-collapsible-header svg {
  width: 20px;
  height: 20px;
  color: var(--primary);
}

.sw-collapsible-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}

.sw-chevron {
  width: 18px;
  height: 18px;
  color: var(--text-400);
  transition: transform 0.25s ease;
}

.sw-collapsible.open .sw-chevron {
  transform: rotate(180deg);
}

.sw-collapsible-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
}

.sw-collapsible-body.open {
  max-height: 400px;
}

/* ---- 表单 ---- */
.sw-form {
  padding: 0 18px 18px;
}

.sw-form-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.sw-form-group {
  flex: 1;
}

.sw-form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-500);
  margin-bottom: 6px;
}

.sw-form-input,
.sw-form-select {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--input);
  border-radius: calc(var(--radius) * 0.6);
  background: var(--background);
  color: var(--foreground);
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  box-sizing: border-box;
}

.sw-form-input:focus,
.sw-form-select:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

.sw-form-input::placeholder {
  color: var(--text-400);
}

.sw-form-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%238e8e93' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 32px;
  cursor: pointer;
}

.sw-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

/* ---- 列表头部 ---- */
.sw-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.sw-list-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-600);
}

.sw-list-count {
  font-size: 13px;
  color: var(--text-400);
}

/* ---- 敏感词卡片列表 ---- */
.sw-word-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sw-word-item {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  padding: 18px 20px;
  transition: box-shadow 0.2s ease;
}

.sw-word-item:hover {
  box-shadow: var(--shadow-md);
}

/* 顶部：词 + 匹配模式标签 */
.sw-word-item-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sw-word-content {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  line-height: 1.4;
  word-break: break-all;
  flex: 1;
}

.sw-match-mode-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.sw-match-mode-tag.exact {
  background: var(--brand-50);
  color: var(--brand-600);
}

.sw-match-mode-tag.fuzzy {
  background: var(--state-warning-surface);
  color: var(--state-warning);
}

.sw-match-mode-tag.regex {
  background: #f0e6ff;
  color: #5856d6;
}

/* 中部：分类 + 命中信息 + 替换词 */
.sw-word-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.sw-category-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: var(--background-200);
  color: var(--text-600);
}

.sw-hit-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-400);
}

.sw-hit-info svg {
  width: 14px;
  height: 14px;
  color: var(--text-400);
}

.sw-hit-count {
  font-weight: 600;
  color: var(--text-600);
}

.sw-replace-word {
  font-size: 12px;
  color: var(--text-400);
}

.sw-replace-value {
  font-weight: 600;
  color: var(--text-600);
}

/* 底部：命中时间 + 操作按钮 */
.sw-word-item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--background-200);
}

.sw-last-hit-time {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-400);
}

.sw-last-hit-time svg {
  width: 13px;
  height: 13px;
  color: var(--text-400);
}

.sw-word-actions {
  display: flex;
  gap: 8px;
}

.sw-word-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--background);
  color: var(--text-500);
  cursor: pointer;
  transition: all 0.15s ease;
}

.sw-word-action-btn svg {
  width: 15px;
  height: 15px;
}

.sw-word-action-btn:hover {
  background: var(--background-200);
  color: var(--text-800);
}

.sw-action-delete:hover {
  background: var(--state-error-surface);
  color: var(--state-error);
  border-color: var(--state-error);
}

/* ---- 空状态 ---- */
.sw-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-400);
  gap: 12px;
}

.sw-empty svg {
  width: 48px;
  height: 48px;
  opacity: 0.4;
}

.sw-empty span {
  font-size: 14px;
}
</style>
