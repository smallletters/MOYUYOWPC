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
      <div class="banner-grid">
        <div
          v-for="(item, index) in bannerData"
          :key="item.id"
          class="banner-card"
          :class="{ 'banner-card-expired': item.status === '已过期' }"
        >
          <div class="banner-img-wrap">
            <div class="banner-img-placeholder" :style="{ opacity: item.status === '已暂停' || item.status === '已过期' ? 0.6 : 1 }">
              <span class="banner-img-label">{{ item.title }}</span>
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
              <span :class="statusTagClass(item.status)">{{ item.status }}</span>
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
                {{ item.status === '投放中' ? '⏸' : '▶' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 推荐位面板 ===== -->
    <div v-show="activeTab === 'recommend'" class="empty-panel">
      <p>推荐位管理功能开发中</p>
    </div>

    <!-- ===== 专题页面板 ===== -->
    <div v-show="activeTab === 'topic'" class="empty-panel">
      <p>专题页管理功能开发中</p>
    </div>

    <!-- ===== Push推送面板 ===== -->
    <div v-show="activeTab === 'push'" class="empty-panel">
      <p>Push 推送功能开发中</p>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="请输入 Banner 标题" />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-input v-model="editForm.dateRange" placeholder="例如: 03/01 - 04/30" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width:100%">
            <el-option label="投放中" value="投放中" />
            <el-option label="已暂停" value="已暂停" />
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
import { ElMessage } from 'element-plus'
import { getCmsList, createCms, updateCms, updateCmsStatus, reorderCms } from '../api/admin'

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

// 加载Banner列表
async function loadBannerData() {
  try {
    const list = await getCmsList()
    bannerData.value = (list || []).map(item => ({
      id: item.id,
      title: item.title || '',
      locations: item.locations || [],
      status: item.status || '已暂停',
      dateRange: item.dateRange || '',
      ctr: item.ctr || '0%'
    }))
  } catch (e) {
    console.error('获取CMS列表失败', e)
  }
}

function statusTagClass(status) {
  const map = {
    '投放中': 'status-tag status-tag-active',
    '已暂停': 'status-tag status-tag-paused',
    '已过期': 'status-tag status-tag-expired'
  }
  return map[status] || 'status-tag'
}

function handleEdit(item) {
  dialogTitle.value = '编辑 Banner'
  editForm.value = { ...item }
  dialogVisible.value = true
}

// 上下移动排序
async function handleMove(item, dir) {
  try {
    await reorderCms({ id: item.id, direction: dir })
    ElMessage.success(`已将 "${item.title}" ${dir === 'up' ? '上移' : '下移'}`)
    await loadBannerData()
  } catch (e) {
    ElMessage.error('排序操作失败')
  }
}

// 切换投放/暂停状态
async function handleToggleStatus(item) {
  const newStatus = item.status === '投放中' ? '已暂停' : '投放中'
  try {
    await updateCmsStatus(item.id, { status: newStatus })
    item.status = newStatus
    ElMessage.success(`Banner 已${item.status === '投放中' ? '恢复投放' : '暂停'}`)
  } catch (e) {
    ElMessage.error('状态更新失败')
  }
}

// 保存Banner编辑
async function handleSave() {
  try {
    await updateCms(editForm.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadBannerData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadBannerData()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-title-section { margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--text-800); margin: 0 0 6px; }
.page-desc { font-size: 13px; color: var(--text-400); margin: 0; }

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
  background: linear-gradient(135deg, var(--secondary), var(--accent));
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

/* 空面板 */
.empty-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--text-400);
  font-size: 14px;
}
</style>
