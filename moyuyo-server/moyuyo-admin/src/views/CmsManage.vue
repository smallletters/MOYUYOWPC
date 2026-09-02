<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-section">
      <h1 class="page-title">CMS 管理</h1>
      <p class="page-desc">管理首页 Banner、推荐位、专题页及 Push 推送内容</p>
    </div>

    <!-- Tab 切换 -->
    <div class="custom-tab-bar">
      <button
        v-for="tab in tabList"
        :key="tab.key"
        class="custom-tab"
        :class="{ 'custom-tab-active': activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- ===== Banner 面板 ===== -->
    <div v-show="activeTab === 'banner'">
      <div class="section-header">
        <h2 class="section-title">Banner 管理</h2>
        <button class="btn btn-sm btn-primary" @click="handleAdd">新建 Banner</button>
      </div>
      <div class="banner-grid">
        <div
          v-for="(item, index) in bannerData"
          :key="item.id"
          class="banner-card"
          :class="{ 'banner-card-expired': item.status === 'EXPIRED' }"
        >
          <div class="banner-img-wrap">
            <div
              class="banner-img-placeholder"
              :style="{
                opacity: item.status === 'PAUSED' || item.status === 'EXPIRED' ? 0.6 : 1,
                backgroundImage: item.imageUrl ? `url(${item.imageUrl})` : 'none'
              }"
            >
              <span v-if="!item.imageUrl" class="banner-img-label">{{ item.title }}</span>
            </div>
            <div class="banner-order-num">{{ index + 1 }}</div>
          </div>
          <div class="banner-body">
            <div class="banner-body-top">
              <div class="banner-info">
                <h3 class="banner-title">{{ item.title }}</h3>
                <div class="banner-locations">
                  <span class="location-tag" v-for="loc in item.locations" :key="loc">{{ loc }}</span>
                </div>
              </div>
              <span :class="statusTagClass(item.status)">{{ statusLabel(item.status) }}</span>
            </div>
            <div class="banner-meta">
              <div class="banner-meta-item">
                <span class="meta-icon">📅</span>
                <span class="meta-text">{{ item.dateRange }}</span>
              </div>
              <div class="banner-meta-item">
                <span class="meta-icon">👆</span>
                <span class="meta-text meta-ctr">CTR {{ item.ctr }}</span>
              </div>
            </div>
            <div class="banner-actions">
              <button class="action-btn action-btn-primary" @click="handleEdit(item)">编辑</button>
              <button class="action-btn" @click="handleMove(item, 'up')">↑</button>
              <button class="action-btn" @click="handleMove(item, 'down')">↓</button>
              <button class="action-btn" @click="handleToggleStatus(item)">
                {{ item.status === STATUS_ACTIVE ? '⏸' : '▶' }}
              </button>
              <button class="action-btn action-btn-danger" @click="handleDelete(item)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 推荐位面板 ===== -->
    <div v-show="activeTab === 'recommend'">
      <div class="section-header">
        <h2 class="section-title">推荐位管理</h2>
        <button class="btn btn-sm btn-primary" @click="handleAddRecommend">新建推荐位</button>
      </div>
      <div v-if="recommendData.length === 0" class="empty-panel">
        <p>暂无推荐位数据</p>
        <button class="btn btn-outline" style="margin-top:12px" @click="handleAddRecommend">添加第一个推荐位</button>
      </div>
      <div v-else class="cms-list">
        <div v-for="item in recommendData" :key="item.id" class="cms-list-item">
          <div class="cms-list-info">
            <span class="cms-list-title">{{ item.title }}</span>
            <span :class="statusTagClass(item.status)">{{ statusLabel(item.status) }}</span>
          </div>
          <span class="cms-list-date">{{ item.dateRange || '未设置日期' }}</span>
          <div class="cms-list-actions">
            <button class="action-btn action-btn-primary" @click="handleEdit(item)">编辑</button>
            <button class="action-btn action-btn-danger" @click="handleDelete(item)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 专题页面板 ===== -->
    <div v-show="activeTab === 'topic'">
      <div class="section-header">
        <h2 class="section-title">专题页管理</h2>
        <button class="btn btn-sm btn-primary" @click="handleAddTopic">新建专题页</button>
      </div>
      <div v-if="topicData.length === 0" class="empty-panel">
        <p>暂无专题页数据</p>
        <button class="btn btn-outline" style="margin-top:12px" @click="handleAddTopic">添加第一个专题页</button>
      </div>
      <div v-else class="cms-list">
        <div v-for="item in topicData" :key="item.id" class="cms-list-item">
          <div class="cms-list-info">
            <span class="cms-list-title">{{ item.title }}</span>
            <span :class="statusTagClass(item.status)">{{ statusLabel(item.status) }}</span>
          </div>
          <span class="cms-list-date">{{ item.dateRange || '未设置日期' }}</span>
          <div class="cms-list-actions">
            <button class="action-btn action-btn-primary" @click="handleEdit(item)">编辑</button>
            <button class="action-btn action-btn-danger" @click="handleDelete(item)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Push推送面板 ===== -->
    <div v-show="activeTab === 'push'" class="empty-panel">
      <p>Push 推送功能请前往 <a href="#" @click.prevent="router.push('/push-manage')" style="color:var(--primary);font-weight:600;">推送管理</a> 页面操作</p>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="请输入 Banner 标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editForm.type" style="width:100%">
            <el-option label="BANNER" value="BANNER" />
            <el-option label="RECOMMEND" value="RECOMMEND" />
            <el-option label="TOPIC" value="TOPIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图片URL">
          <el-input v-model="editForm.imageUrl" placeholder="请输入封面图片 URL" />
        </el-form-item>
        <el-form-item label="链接URL">
          <el-input v-model="editForm.linkUrl" placeholder="请输入跳转链接" />
        </el-form-item>
        <el-form-item label="内容描述">
          <el-input
            v-model="editForm.content"
            type="textarea"
            :rows="3"
            placeholder="请输入内容描述"
          />
        </el-form-item>
        <el-form-item label="类型标签">
          <el-input v-model="editForm.tag" placeholder="如 HOT / NEW / 限时 / 新品上架" />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="editForm.sortOrder" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-input v-model="editForm.dateRange" placeholder="例如: 03/01 - 04/30" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width:100%">
              <el-option label="投放中" value="ACTIVE" />
              <el-option label="已暂停" value="PAUSED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCmsList, createCms, updateCms, deleteCms, updateCmsStatus, reorderCms } from '../api/admin'

const router = useRouter()

const activeTab = ref('banner')
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = ref({})

const tabList = [
  { key: 'banner', label: 'Banner' },
  { key: 'recommend', label: '推荐位' },
  { key: 'topic', label: '专题页' },
  { key: 'push', label: 'Push推送' }
]

// Banner 列表（通过API获取）
const bannerData = ref([])
// 推荐位 / 专题页列表（通过API获取）
const recommendData = ref([])
const topicData = ref([])

// 列表项字段映射（后端 → 前端展示）
function mapItem(item) {
  return {
    id: item.id,
    title: item.title || '',
    type: item.type || 'BANNER',
    imageUrl: item.cover || item.imageUrl || '',
    linkUrl: item.link || item.linkUrl || '',
    content: item.description || item.content || '',
    tag: item.tag || '',
    sortOrder: item.sortOrder ?? 0,
    locations: item.location ? [item.location] : [],
    status: item.status || 'PAUSED',
    dateRange: formatDateRange(item.startTime, item.endTime),
    ctr: item.ctr != null ? (Number(item.ctr) * 100).toFixed(1) + '%' : '0%'
  }
}

// 格式化日期范围（后端 → 前端显示）
function formatDateRange(startTime, endTime) {
  if (!startTime && !endTime) return ''
  const fmt = (val) => {
    if (!val) return ''
    const d = new Date(val)
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return mm + '/' + dd
  }
  return fmt(startTime) + ' - ' + fmt(endTime)
}

// 解析日期范围字符串（前端显示 → 后端字段）
function parseDateRange(dateRangeStr) {
  if (!dateRangeStr) return [null, null]
  const parts = dateRangeStr.split('-').map(s => s.trim())
  if (parts.length !== 2) return [null, null]
  // 未携带年份，补足为当前年份
  const currentYear = new Date().getFullYear()
  const parseOne = (mmdd) => {
    if (!mmdd || mmdd === 'undefined') return null
    const [m, d] = mmdd.split('/').map(n => parseInt(n, 10))
    if (!m || !d) return null
    return new Date(currentYear, m - 1, d).toISOString()
  }
  return [parseOne(parts[0]), parseOne(parts[1])]
}

// 加载CMS列表（Banner / 推荐位 / 专题页）
async function loadBannerData() {
  try {
    const list = await getCmsList()
    const items = list || []
    bannerData.value = items.filter(i => !i.type || i.type === 'BANNER').map(mapItem)
    recommendData.value = items.filter(i => i.type === 'RECOMMEND').map(mapItem)
    topicData.value = items.filter(i => i.type === 'TOPIC').map(mapItem)
  } catch (e) {
    console.error('获取CMS列表失败', e)
  }
}

// 状态枚举：前端存储/传输统一用英文，显示时转中文
const STATUS_ACTIVE = 'ACTIVE'
const STATUS_PAUSED = 'PAUSED'
const STATUS_EXPIRED = 'EXPIRED'

// 英文枚举 → 中文显示
function statusLabel(status) {
  const map = { ACTIVE: '投放中', PAUSED: '已暂停', EXPIRED: '已过期' }
  return map[status] || status || '未知'
}

function statusTagClass(status) {
  const map = {
    ACTIVE: 'status-tag status-tag-active',
    PAUSED: 'status-tag status-tag-paused',
    EXPIRED: 'status-tag status-tag-expired'
  }
  return map[status] || 'status-tag'
}

function handleEdit(item) {
  dialogTitle.value = '编辑 Banner'
  editForm.value = { ...item }
  dialogVisible.value = true
}

// 新建 Banner
function handleAdd() {
  dialogTitle.value = '新建 Banner'
  editForm.value = {
    title: '',
    type: 'BANNER',
    imageUrl: '',
    linkUrl: '',
    content: '',
    tag: '',
    sortOrder: 0,
    dateRange: '',
    status: 'PAUSED'
  }
  dialogVisible.value = true
}

// 上下移动排序
async function handleMove(item, dir) {
  try {
    const idx = bannerData.value.findIndex(i => i.id === item.id)
    if (idx === -1) return
    const swapIdx = dir === 'up' ? idx - 1 : idx + 1
    if (swapIdx < 0 || swapIdx >= bannerData.value.length) return
    
    // 交换位置
    const newList = [...bannerData.value]
    const temp = newList[idx].sortOrder
    newList[idx].sortOrder = newList[swapIdx].sortOrder
    newList[swapIdx].sortOrder = temp
    
    // 构建后端期望的排序数组
    const orders = newList.map((item, index) => ({
      id: item.id,
      sort: item.sortOrder || index
    }))
    
    await reorderCms(orders)
    ElMessage.success(`已将 "${item.title}" ${dir === 'up' ? '上移' : '下移'}`)
    await loadBannerData()
  } catch (e) {
    ElMessage.error('排序操作失败')
  }
}

// 切换投放/暂停状态
async function handleToggleStatus(item) {
  const newStatus = item.status === STATUS_ACTIVE ? STATUS_PAUSED : STATUS_ACTIVE
  try {
    await updateCmsStatus(item.id, { status: newStatus })
    item.status = newStatus
    ElMessage.success(`Banner 已${item.status === STATUS_ACTIVE ? '恢复投放' : '暂停'}`)
  } catch (e) {
    ElMessage.error('状态更新失败')
  }
}

// 保存Banner编辑/新建
async function handleSave() {
  try {
    // 后端不读取 startTime/endTime，不随 payload 提交（表单 dateRange 仅作展示）
    const payload = {
      title: editForm.value.title,
      type: editForm.value.type,
      cover: editForm.value.imageUrl,
      link: editForm.value.linkUrl,
      description: editForm.value.content,
      tag: editForm.value.tag,
      sortOrder: editForm.value.sortOrder,
      status: editForm.value.status
    }
    if (editForm.value.id) {
      // 编辑：带上id调用更新接口
      payload.id = editForm.value.id
      await updateCms(payload)
    } else {
      // 新建：调用创建接口
      await createCms(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadBannerData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 删除 Banner
async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除 "${item.title}" 吗？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await deleteCms(item.id)
    ElMessage.success(`已删除 "${item.title}"`)
    await loadBannerData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('删除Banner失败:', e)
      ElMessage.error('删除失败: ' + (e?.message || '未知错误'))
    }
  }
}

// 推荐位和专题页新增操作：复用新建弹窗并预设类型
function openAddDialog(type, title, statusDefault) {
  dialogTitle.value = title
  editForm.value = {
    title: '',
    type,
    imageUrl: '',
    linkUrl: '',
    content: '',
    tag: '',
    sortOrder: 0,
    dateRange: '',
    status: statusDefault || 'PAUSED'
  }
  dialogVisible.value = true
}

function handleAddRecommend() {
  openAddDialog('RECOMMEND', '新建推荐位')
}

function handleAddTopic() {
  openAddDialog('TOPIC', '新建专题页')
}

onMounted(() => {
  loadBannerData()
})
</script>

<style scoped>
.page-wrapper { }
.page-title-section { margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 6px; }
.page-desc { font-size: 13px; color: var(--text-400); margin: 0; }

/* 推荐位/专题页列表 */
.cms-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cms-list-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--card);
  transition: box-shadow 0.15s ease;
}
.cms-list-item:hover { box-shadow: var(--shadow-sm); }
.cms-list-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.cms-list-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cms-list-date { font-size: 12px; color: var(--text-400); white-space: nowrap; }
.cms-list-actions { display: flex; gap: 8px; }

/* Tab 栏 */
.custom-tab-bar {
  display: flex;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
  gap: 0;
}
.custom-tab {
  flex: none;
  width: 140px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-400);
  background: transparent;
  border: none;
  cursor: pointer;
  position: relative;
  transition: color 0.15s ease;
  font-family: var(--font-sans);
}
.custom-tab:hover { color: var(--text-600); }
.custom-tab-active {
  color: var(--primary);
  font-weight: 600;
}
.custom-tab-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 32px;
  height: 2px;
  border-radius: 1px;
  background: var(--primary);
}

/* Banner 网格 */
.banner-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* Banner 卡片 */
.banner-card {
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
.banner-card-expired {
  opacity: 0.7;
}
.banner-img-wrap {
  position: relative;
}
.banner-img-placeholder {
  width: 100%;
  aspect-ratio: 16 / 7;
  /* 优先显示设置的真实图片；未设置时降级到默认渐变占位 */
  background: linear-gradient(135deg, var(--secondary), var(--accent));
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: calc(var(--radius) - 4px);
}
.banner-img-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-400);
}
.banner-order-num {
  position: absolute;
  left: 10px;
  bottom: 10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}
.banner-body { padding: 12px; }
.banner-body-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.banner-info { flex: 1; min-width: 0; }
.banner-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--foreground);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.banner-locations {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

/* 位置标签 */
.location-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
  background: var(--background-200);
  color: var(--text-600);
}

/* 状态标签 */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}
.status-tag-active {
  background: var(--state-success-surface);
  color: var(--state-success);
}
.status-tag-paused {
  background: var(--background-200);
  color: var(--text-500);
}
.status-tag-expired {
  background: var(--state-error-surface);
  color: var(--state-error);
}

/* Banner 元数据行 */
.banner-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}
.banner-meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.meta-icon { font-size: 14px; }
.meta-text { font-size: 12px; color: var(--text-400); }
.meta-ctr { color: var(--primary); font-weight: 600; }

/* 操作按钮 */
.banner-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: calc(var(--radius) - 6px);
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-600);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
  font-family: var(--font-sans);
}
.action-btn:hover {
  background: var(--secondary);
  color: var(--foreground);
}
.action-btn-primary {
  flex: 1;
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
}
.action-btn-primary:hover {
  filter: brightness(0.96);
}
.action-btn-danger {
  color: var(--state-error);
  border-color: var(--state-error);
}
.action-btn-danger:hover {
  background: var(--state-error-surface);
  color: var(--state-error);
}

/* 空面板 */
.empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--text-400);
  font-size: 14px;
}

/* Section header 通用样式 */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}
</style>
