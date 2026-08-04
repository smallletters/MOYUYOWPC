<template>
  <div class="page-wrapper">
    <!-- 页面标题区域 -->
    <div class="page-title-area">
      <h1>应用版本管理</h1>
      <p>管理 APP 版本发布、强制更新策略与版本历史</p>
    </div>

    <!-- KPI 概览 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🚀</span>
          <span class="kpi-card-label">当前最新版本</span>
        </div>
        <div class="kpi-card-value">{{ latestVersion || '-' }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">全量发布中</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📦</span>
          <span class="kpi-card-label">版本总数</span>
        </div>
        <div class="kpi-card-value">{{ tableData.length }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">累计发布记录</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">✅</span>
          <span class="kpi-card-label">已发布</span>
        </div>
        <div class="kpi-card-value">{{ publishedCount }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">正式版本</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">📝</span>
          <span class="kpi-card-label">草稿 / 待发布</span>
        </div>
        <div class="kpi-card-value">{{ draftCount }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">待完善版本</span></div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-bar-left">
        <span class="list-title">版本历史</span>
      </div>
      <div class="action-bar-right">
        <button class="btn btn-primary" @click="handleAdd">＋ 发布新版本</button>
      </div>
    </div>

    <!-- 版本表格 -->
    <div class="data-table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>版本号</th>
            <th>平台</th>
            <th>更新内容</th>
            <th>强制更新</th>
            <th>状态</th>
            <th>发布时间</th>
            <th style="min-width: 170px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedData" :key="row.id">
            <td>
              <span class="version-cell">{{ row.version }}</span>
              <div v-if="row.versionCode" class="version-build">build {{ row.versionCode }}</div>
            </td>
            <td>
              <span :class="row.platform === 'iOS' ? 'tag tag-blue' : 'tag tag-green'">{{ row.platform }}</span>
            </td>
            <td>
              <div class="update-desc" :title="row.description">{{ row.updateTitle || row.description }}</div>
              <div v-if="row.updateTitle && row.description" class="update-sub">{{ row.description }}</div>
            </td>
            <td>
              <span :class="row.forceUpdate ? 'tag tag-red' : 'tag tag-gray'">{{ row.forceUpdate ? '强制' : '非强制' }}</span>
            </td>
            <td>
              <span :class="statusPillClass(row.status)">{{ row.status }}</span>
            </td>
            <td class="time-cell">{{ row.publishTime || '-' }}</td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-outline" @click="handleEdit(row)">编辑</button>
                <button v-if="row.status === '草稿'" class="btn btn-sm btn-primary" @click="handlePublish(row)">发布</button>
                <button class="btn btn-sm btn-danger" @click="handleDelete(row)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="pagedData.length === 0">
            <td colspan="7">
              <div class="empty-state">
                <div class="empty-state-icon">📦</div>
                <div class="empty-state-text">暂无版本记录，点击右上角「发布新版本」创建</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="tableData.length > pageSize">
        <span class="pagination-info">共 {{ tableData.length }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="currentPage--">‹ 上一页</button>
          <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="currentPage++">下一页 ›</button>
        </div>
      </div>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑版本' : '发布新版本'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="版本号" required>
          <el-input v-model="form.version" placeholder="如 2.1.0" />
        </el-form-item>
        <el-form-item label="版本数字" required>
          <el-input v-model="form.versionCode" type="number" placeholder="如 210（整数递增）" />
        </el-form-item>
        <el-form-item label="平台" required>
          <el-radio-group v-model="form.platform">
            <el-radio value="iOS">iOS</el-radio>
            <el-radio value="Android">Android</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="更新标题" required>
          <el-input v-model="form.updateTitle" placeholder="如：全新视觉升级" />
        </el-form-item>
        <el-form-item label="更新内容" required>
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入更新内容" />
        </el-form-item>
        <el-form-item label="下载链接" required>
          <el-input v-model="form.downloadUrl" placeholder="如：https://cdn.example.com/app-v2.1.0.apk" />
        </el-form-item>
        <el-form-item label="文件大小">
          <el-input v-model="form.fileSize" placeholder="如：36.5MB" />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="form.forceUpdate" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAppVersionList, createAppVersion, updateAppVersion, publishAppVersion, deleteAppVersion } from '../api/admin'
import { toArray } from '../utils/safeArray'

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  version: '',
  platform: 'iOS',
  description: '',
  forceUpdate: false,
})

const tableData = ref([])

// 分页（客户端）
const pageSize = 8
const currentPage = ref(1)
const pagedData = computed(() => tableData.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const totalPages = computed(() => Math.max(1, Math.ceil(tableData.value.length / pageSize)))

// KPI 统计（基于真实列表数据推导）
const latestVersion = computed(() => tableData.value[0]?.version || '')
const publishedCount = computed(() => tableData.value.filter(v => v.status === '已发布').length)
const draftCount = computed(() => tableData.value.filter(v => v.status === '草稿' || v.status === '待发布').length)

function statusPillClass(status) {
  if (status === '已发布') return 'tag tag-green'
  if (status === '待发布') return 'tag tag-warning'
  return 'tag tag-gray'
}

function resetForm() {
  form.version = ''
  form.versionCode = 0
  form.platform = 'iOS'
  form.updateTitle = ''
  form.description = ''
  form.downloadUrl = ''
  form.fileSize = ''
  form.forceUpdate = false
}

// 加载版本列表
async function loadData() {
  try {
    const res = await getAppVersionList()
    tableData.value = toArray(res)
    currentPage.value = 1
  } catch (e) {
    ElMessage.error('获取版本列表失败')
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.version = row.version
  form.versionCode = row.versionCode || 0
  form.platform = row.platform
  form.updateTitle = row.updateTitle || ''
  form.description = row.description
  form.downloadUrl = row.downloadUrl || ''
  form.fileSize = row.fileSize || ''
  form.forceUpdate = row.forceUpdate
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.version || !form.description || !form.updateTitle || !form.downloadUrl) {
    ElMessage.warning('请填写完整信息（版本号/更新标题/更新内容/下载链接）')
    return
  }
  // 字段映射：前端 form -> 后端 AppVersionEntity 字段名
  // platform(iOS/Android) -> appType(IOS/ANDROID)
  // version(显示版本) -> versionName(显示版本) + versionCode(数字)
  // description -> updateDesc
  const payload = {
    id: isEdit.value ? editId.value : undefined,
    appType: form.platform === 'iOS' ? 'IOS' : 'ANDROID',
    versionName: form.version,
    versionCode: form.versionCode || Number(form.version.replace(/\./g, '')) || 1,
    updateTitle: form.updateTitle,
    updateDesc: form.description,
    downloadUrl: form.downloadUrl,
    fileSize: form.fileSize || null,
    forceUpdate: !!form.forceUpdate,
  }
  try {
    if (isEdit.value) {
      await updateAppVersion(payload)
      ElMessage.success('编辑成功')
    } else {
      await createAppVersion(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    console.error('保存版本失败:', e)
    ElMessage.error('保存失败: ' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm('确定发布版本 ' + row.version + ' 吗？', '提示')
    await publishAppVersion(row.id)
    ElMessage.success('版本已发布')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('发布版本失败:', e)
      ElMessage.error('发布失败: ' + (e?.message || '未知错误'))
    }
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除版本 ' + row.version + ' 吗？', '提示')
    await deleteAppVersion(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('删除版本失败:', e)
      ElMessage.error('删除失败: ' + (e?.message || '未知错误'))
    }
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }

/* 操作栏 */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.list-title { font-size: 14px; font-weight: 600; color: var(--text-600); }
.action-bar-right { display: flex; gap: 8px; }

/* 版本单元格 */
.version-cell {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-800);
}
.version-build {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 2px;
}
.update-desc {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-700);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.update-sub {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 2px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.time-cell {
  font-size: 12px;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}
</style>
