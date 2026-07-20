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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getImportRecords, getImportTemplate, submitImport } from '../api/admin'

const importType = ref('商品')
const currentFile = ref(null)

const tableData = ref([])

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
  ElMessage.success('导入任务已提交')
}

function handleDownloadTemplate() {
  ElMessage.success('模板下载中...')
}

function handleViewFail(row) { ElMessage.info('查看失败详情：' + row.fileName) }
function handleViewDetail(row) { ElMessage.info('查看导入详情：' + row.fileName) }

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
