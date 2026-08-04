<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>批量导入</h2>
      <div class="header-actions">
        <el-button @click="handleDownloadTemplate">下载模板</el-button>
      </div>
    </div>
    <!-- 上传区域 -->
    <el-card shadow="never" class="filter-card">
      <el-upload
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        accept=".xlsx,.xls,.csv"
      >
        <el-icon style="font-size:48px;color:var(--el-color-primary);margin-bottom:8px">
          <UploadFilled />
        </el-icon>
        <div>将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 .xlsx、.xls、.csv 格式，最大 10MB</div>
        </template>
      </el-upload>
      <div style="margin-top:12px;text-align:center">
        <el-select v-model="importType" placeholder="选择导入类型" style="width:200px;margin-right:8px">
          <el-option label="商品" value="商品" />
          <el-option label="订单" value="订单" />
          <el-option label="用户" value="用户" />
        </el-select>
        <el-button type="primary" @click="handleImport">开始导入</el-button>
      </div>
    </el-card>
    <!-- 导入历史表格 -->
    <el-card shadow="never">
      <template #header><span>导入历史</span></template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="fileName" label="导入文件名" min-width="180" />
        <el-table-column prop="importType" label="导入类型" width="100" />
        <el-table-column prop="totalCount" label="导入条数" width="100" />
        <el-table-column prop="successCount" label="成功条数" width="100">
          <template #default="{ row }">
            <span style="color:var(--el-color-success)">{{ row.successCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="failCount" label="失败条数" width="100">
          <template #default="{ row }">
            <span v-if="row.failCount > 0" style="color:var(--el-color-danger)">{{ row.failCount }}</span>
            <span v-else>{{ row.failCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="importTime" label="导入时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.failCount > 0" type="primary" link size="small" @click="handleViewFail(row)">查看失败</el-button>
            <el-button v-else type="primary" link size="small" @click="handleViewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 导入失败详情弹窗 -->
    <el-dialog v-model="errorDialogVisible" :title="`导入失败详情 - ${currentRecord?.fileName || ''}`" width="640px">
      <div v-loading="loadingErrors">
        <div v-if="errorRows.length === 0 && !loadingErrors" class="empty-tip">该任务无失败记录</div>
        <el-table v-else :data="errorRows" stripe max-height="400" size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="field" label="字段" width="120" />
          <el-table-column prop="value" label="值" min-width="140" show-overflow-tooltip />
          <el-table-column prop="reason" label="失败原因" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>

    <!-- 导入详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="导入详情" width="520px">
      <el-descriptions v-if="currentRecord" :column="1" border>
        <el-descriptions-item label="文件名">{{ currentRecord.fileName }}</el-descriptions-item>
        <el-descriptions-item label="导入类型">{{ currentRecord.importType }}</el-descriptions-item>
        <el-descriptions-item label="导入条数">{{ currentRecord.totalCount }}</el-descriptions-item>
        <el-descriptions-item label="成功条数">{{ currentRecord.successCount }}</el-descriptions-item>
        <el-descriptions-item label="失败条数">{{ currentRecord.failCount }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentRecord.status }}</el-descriptions-item>
        <el-descriptions-item label="导入时间">{{ currentRecord.importTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getImportRecords, getImportTemplate, submitImport, getImportErrors } from '../api/admin'

const importType = ref('商品')
const currentFile = ref(null)

const tableData = ref([])

// 错误/详情弹窗状态
const errorDialogVisible = ref(false)
const loadingErrors = ref(false)
const errorRows = ref([])
const detailDialogVisible = ref(false)
const currentRecord = ref(null)

async function loadData() {
  try {
    const res = await getImportRecords({ page: 1, size: 15 })
    tableData.value = res || []
  } catch (err) {
    console.error('获取导入历史失败', err)
  }
}

function statusTag(status) {
  if (status === '已完成') return 'success'
  if (status === '导入中') return 'warning'
  return 'danger'
}

function handleFileChange(file) {
  currentFile.value = file
}

function handleImport() {
  if (!currentFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  // 后端 /batch-import/import 接收 JSON（仅记录导入任务，不解析文件内容）
  submitImport({ type: importType.value, fileName: currentFile.value.name || '' })
    .then(res => {
      ElMessage.success('导入任务已提交')
      loadData()
    })
    .catch(err => {
      ElMessage.error('导入失败: ' + (err.message || '未知错误'))
    })
}

function handleDownloadTemplate() {
  // 根据当前选中的导入类型下载对应模板
  const typeMap = { '商品': 'product', '订单': 'order', '用户': 'user' }
  const type = typeMap[importType.value] || 'product'
  getImportTemplate(type)
    .then(res => {
      ElMessage.success('模板下载中...')
      // 触发文件下载
      if (res && res.url) {
        window.open(res.url, '_blank')
      }
    })
    .catch(err => {
      ElMessage.error('模板下载失败: ' + (err.message || '未知错误'))
    })
}

// 查看失败详情：拉取该导入任务的错误列表
async function handleViewFail(row) {
  loadingErrors.value = true
  errorDialogVisible.value = true
  errorRows.value = []
  currentRecord.value = row
  try {
    const errors = await getImportErrors(row.id)
    errorRows.value = Array.isArray(errors) ? errors : []
  } catch (err) {
    console.error('获取导入错误失败', err)
    ElMessage.error('获取导入错误失败：' + (err?.message || '未知错误'))
  } finally {
    loadingErrors.value = false
  }
}

// 查看导入详情
function handleViewDetail(row) {
  currentRecord.value = row
  detailDialogVisible.value = true
}

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
.empty-tip { padding: 24px 0; text-align: center; color: var(--text-400); font-size: 13px; }
</style>
